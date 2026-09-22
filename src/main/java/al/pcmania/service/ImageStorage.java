package al.pcmania.service;

import al.pcmania.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores uploads as three JPEG variants under {uploadDir}/{thumb|medium|full}/{uuid}.jpg,
 * served at /img/p/{size}/{filename}.
 */
@Service
@Slf4j
public class ImageStorage {

    public enum Size {
        thumb(400), medium(800), full(1600);
        public final int px;
        Size(int px) { this.px = px; }
    }

    private final Path root;

    public ImageStorage(AppProperties props) throws IOException {
        root = Path.of(props.uploadDir()).toAbsolutePath().normalize();
        for (Size s : Size.values()) Files.createDirectories(root.resolve(s.name()));
        log.info("Image storage at {}", root);
    }

    public static String url(Size size, String filename) {
        return "/img/p/" + size.name() + "/" + filename;
    }

    /** Validates, resizes and stores an upload; returns the stored filename. */
    public String store(MultipartFile file) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("upload-", ".img");
            file.transferTo(tmp);
            int maxSide = maxSide(tmp);
            String filename = UUID.randomUUID().toString().replace("-", "") + ".jpg";
            for (Size s : Size.values()) {
                int target = Math.min(s.px, maxSide); // never upscale
                Thumbnails.of(tmp.toFile())
                        .size(target, target)
                        .addFilter(ImageStorage::flattenOnWhite)
                        .outputFormat("jpg")
                        .outputQuality(s == Size.thumb ? 0.8 : 0.85)
                        .toFile(root.resolve(s.name()).resolve(filename).toFile());
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
        try {
            for (Size s : Size.values()) {
                Path src = root.resolve(s.name()).resolve(filename);
                if (Files.exists(src)) Files.copy(src, root.resolve(s.name()).resolve(copy));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return copy;
    }

    public void delete(String filename) {
        for (Size s : Size.values()) {
            try {
                Files.deleteIfExists(root.resolve(s.name()).resolve(filename));
            } catch (IOException e) {
                log.warn("Could not delete {}/{}: {}", s, filename, e.getMessage());
            }
        }
    }

    private final Map<String, int[]> dimensionCache = new ConcurrentHashMap<>();

    /** Width/height of a stored variant, read from the file header (cached). Null if missing. */
    public int[] dimensions(Size size, String filename) {
        return dimensionCache.computeIfAbsent(size + "/" + filename, k -> {
            try (ImageInputStream in = ImageIO.createImageInputStream(root.resolve(size.name()).resolve(filename).toFile())) {
                Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
                if (readers == null || !readers.hasNext()) return null;
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    return new int[]{reader.getWidth(0), reader.getHeight(0)};
                } finally {
                    reader.dispose();
                }
            } catch (IOException e) {
                return null;
            }
        });
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
