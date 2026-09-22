package al.pcmania.service;

import al.pcmania.domain.StoredFile;
import al.pcmania.repo.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Keeps uploaded files in the database, keyed by the path they are served at.
 *
 * The hosted service starts from its container image on every deploy, so anything written to the
 * filesystem there is gone the next time it is released. Product photos and the Android build both
 * have to outlive a deploy, and the database is the only storage this application already has.
 */
@Service
@RequiredArgsConstructor
public class FileStorage {

    private final StoredFileRepository files;

    /** Writes a file, replacing whatever was under that key. Image dimensions are recorded here. */
    @Transactional
    public void put(String key, String contentType, byte[] bytes, String label) {
        StoredFile f = files.findByFileKey(key, StoredFile.class).orElseGet(StoredFile::new);
        f.setFileKey(key);
        f.setContentType(contentType);
        f.setSizeBytes(bytes.length);
        f.setLabel(label);
        f.setData(bytes);
        f.setCreatedAt(LocalDateTime.now());
        int[] pixels = contentType.startsWith("image/") ? pixelSize(bytes) : null;
        f.setWidthPx(pixels == null ? null : pixels[0]);
        f.setHeightPx(pixels == null ? null : pixels[1]);
        files.save(f);
    }

    public Optional<StoredFileRepository.Content> content(String key) {
        return files.findByFileKey(key, StoredFileRepository.Content.class);
    }

    public Optional<StoredFileRepository.Meta> meta(String key) {
        return files.findByFileKey(key, StoredFileRepository.Meta.class);
    }

    public Optional<StoredFileRepository.Dimensions> dimensions(String key) {
        return files.findByFileKey(key, StoredFileRepository.Dimensions.class);
    }

    public List<StoredFileRepository.Meta> listUnder(String prefix) {
        return files.findByFileKeyStartingWithOrderByCreatedAtDesc(prefix);
    }

    public boolean exists(String key) {
        return files.existsByFileKey(key);
    }

    @Transactional
    public void delete(String key) {
        files.deleteByFileKey(key);
    }

    /** Width and height read from the image header, or null when the bytes are not a readable image. */
    private static int[] pixelSize(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
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
    }
}
