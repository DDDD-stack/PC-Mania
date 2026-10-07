package al.pcmania.service;

import al.pcmania.repo.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.util.exif.ExifFilterUtils;
import net.coobird.thumbnailator.util.exif.ExifUtils;
import net.coobird.thumbnailator.util.exif.Orientation;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageStorage {

    public enum Size {
        thumb(400), medium(800), full(1600);
        public final int px;
        Size(int px) { this.px = px; }
    }

    private final FileStorage storage;

    static {

        ImageIO.scanForPlugins();
    }

    public static String url(Size size, String filename) {
        return "/img/p/" + size.name() + "/" + filename;
    }

    public static String key(Size size, String filename) {
        return "img/" + size.name() + "/" + filename;
    }

    public String store(MultipartFile file) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("upload-", ".img");
            file.transferTo(tmp);
            String filename = UUID.randomUUID().toString().replace("-", "") + ".jpg";

            BufferedImage decoded = decode(tmp);
            int maxSide = Math.max(decoded.getWidth(), decoded.getHeight());
            BufferedImage largest = Thumbnails.of(decoded)
                    .size(Math.min(Size.full.px, maxSide), Math.min(Size.full.px, maxSide))
                    .asBufferedImage();
            decoded = null;
            for (Size s : Size.values()) {
                int target = Math.min(s.px, maxSide);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                Thumbnails.of(largest)
                        .size(target, target)
                        .outputFormat("jpg")
                        .outputQuality(s == Size.thumb ? 0.8 : 0.85)
                        .toOutputStream(out);
                storage.put(key(s, filename), "image/jpeg", out.toByteArray(), null);
            }
            return filename;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (tmp != null) try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    public String copy(String filename) {
        String copy = UUID.randomUUID().toString().replace("-", "") + ".jpg";
        for (Size s : Size.values()) {
            storage.content(key(s, filename))
                    .ifPresent(c -> storage.put(key(s, copy), c.getContentType(), c.getData(), null));
        }
        return copy;
    }

    public void delete(String filename) {
        for (Size s : Size.values()) {
            storage.delete(key(s, filename));
            dimensionCache.remove(s + "/" + filename);
        }
    }

    public Optional<StoredFileRepository.Content> content(Size size, String filename) {
        return storage.content(key(size, filename));
    }

    private final Map<String, int[]> dimensionCache = new ConcurrentHashMap<>();

    public int[] dimensions(Size size, String filename) {
        return dimensionCache.computeIfAbsent(size + "/" + filename, k ->
                storage.dimensions(key(size, filename))
                        .filter(d -> d.getWidthPx() != null && d.getHeightPx() != null)
                        .map(d -> new int[]{d.getWidthPx(), d.getHeightPx()})
                        .orElse(null));
    }

    static final int DECODE_MIN_SIDE = 2400;

    static BufferedImage decode(Path file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new IllegalArgumentException("Formati i imazhit nuk mbështetet. Përdorni JPG, PNG ose WebP.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                int longest = Math.max(reader.getWidth(0), reader.getHeight(0));
                ImageReadParam param = reader.getDefaultReadParam();
                int step = longest / DECODE_MIN_SIDE;
                if (step > 1) param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage img = reader.read(0, param);

                Orientation orientation = exifOrientation(file);
                if (orientation != null && orientation != Orientation.TOP_LEFT) {
                    img = ExifFilterUtils.getFilterForOrientation(orientation).apply(img);
                }
                return flattenOnWhite(img);
            } finally {
                reader.dispose();
            }
        }
    }

    static Orientation exifOrientation(Path file) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            byte[] exif = exifBlock(in);
            return exif == null ? null : ExifUtils.getOrientationFromExif(exif);
        } catch (IOException | RuntimeException e) {
            log.debug("Could not read EXIF orientation: {}", e.getMessage());
            return null;
        }
    }

    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};

    private static byte[] exifBlock(DataInputStream in) throws IOException {
        byte[] head = new byte[12];
        in.readFully(head);
        if ((head[0] & 0xff) == 0xff && (head[1] & 0xff) == 0xd8) {

            DataInputStream rest = new DataInputStream(new SequenceInputStream(
                    new ByteArrayInputStream(head, 2, 10), in));
            while (true) {
                int marker = rest.readUnsignedShort();
                while (marker == 0xffff) marker = 0xff00 | rest.readUnsignedByte();
                if ((marker & 0xff00) != 0xff00 || marker == 0xffda || marker == 0xffd9) return null;
                int length = rest.readUnsignedShort() - 2;
                if (length < 0) return null;
                if (marker == 0xffe1 && length > EXIF_HEADER.length) {
                    byte[] segment = new byte[length];
                    rest.readFully(segment);
                    if (Arrays.equals(segment, 0, EXIF_HEADER.length, EXIF_HEADER, 0, EXIF_HEADER.length)) return segment;
                } else {
                    rest.skipNBytes(length);
                }
            }
        }
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {

            byte[] name = new byte[4];
            while (true) {
                in.readFully(name);
                long size = Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
                if (name[0] == 'E' && name[1] == 'X' && name[2] == 'I' && name[3] == 'F') {
                    if (size > 1 << 20) return null;
                    byte[] payload = new byte[(int) size];
                    in.readFully(payload);
                    if (Arrays.equals(payload, 0, Math.min(EXIF_HEADER.length, payload.length), EXIF_HEADER, 0, EXIF_HEADER.length)) return payload;
                    byte[] withHeader = Arrays.copyOf(EXIF_HEADER, EXIF_HEADER.length + payload.length);
                    System.arraycopy(payload, 0, withHeader, EXIF_HEADER.length, payload.length);
                    return withHeader;
                }
                in.skipNBytes(size + (size & 1));
            }
        }
        return null;
    }

    private static BufferedImage flattenOnWhite(BufferedImage img) {
        if (!img.getColorModel().hasAlpha()) return img;
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rgb;
    }
}
