package al.pcmania.service;

import al.pcmania.repo.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns an upload into three JPEG variants and hands them to {@link FileStorage},
 * which serves them at /img/p/{size}/{filename}.
 */
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

    public static String url(Size size, String filename) {
        return "/img/p/" + size.name() + "/" + filename;
    }

    /** Storage key for a variant. Deliberately mirrors {@link #url} without the /img/p prefix. */
    public static String key(Size size, String filename) {
        return "img/" + size.name() + "/" + filename;
    }

    /** Validates, resizes and stores an upload; returns the stored filename. */
    public String store(MultipartFile file) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("upload-", ".img");
            file.transferTo(tmp);
            int maxSide = maxSide(tmp);
            String filename = UUID.randomUUID().toString().replace("-", "") + ".jpg";
            // The original - often a 12-50 megapixel phone photo - is decoded once, into the largest
            // variant, and the smaller ones are scaled from that. Decoding it per variant tripled the
            // work, which on the hosted service's fraction of a CPU made every upload crawl. Reading from
            // the file (not a BufferedImage) is also what keeps Thumbnailator honouring EXIF rotation.
            BufferedImage largest = Thumbnails.of(tmp.toFile())
                    .size(Math.min(Size.full.px, maxSide), Math.min(Size.full.px, maxSide)) // never upscale
                    .addFilter(ImageStorage::flattenOnWhite)
                    .asBufferedImage();
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

    /** Width/height of a stored variant, recorded when it was uploaded (cached). Null if missing. */
    public int[] dimensions(Size size, String filename) {
        return dimensionCache.computeIfAbsent(size + "/" + filename, k ->
                storage.dimensions(key(size, filename))
                        .filter(d -> d.getWidthPx() != null && d.getHeightPx() != null)
                        .map(d -> new int[]{d.getWidthPx(), d.getHeightPx()})
                        .orElse(null));
    }

    private static int maxSide(Path file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new IllegalArgumentException("Formati i imazhit nuk mbështetet. Përdorni JPG ose PNG.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return Math.max(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    /** JPEG has no alpha channel: paint transparent PNGs onto white instead of black. */
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
