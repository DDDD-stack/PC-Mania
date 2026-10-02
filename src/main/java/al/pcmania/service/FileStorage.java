package al.pcmania.service;

import al.pcmania.domain.StoredFile;
import al.pcmania.repo.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
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
    private final JdbcTemplate jdbc;

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

    /**
     * Writes a new file from a stream without holding it in memory: a trade-in proof video is tens of
     * megabytes, and the hosted instance has 512 MB in all. The key must not exist yet.
     */
    @Transactional
    public void putStream(String key, String contentType, InputStream data, long size, String label) {
        if (size > Integer.MAX_VALUE) throw new IllegalArgumentException("File too large");
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into stored_file (file_key, content_type, size_bytes, label, data, created_at) values (?, ?, ?, ?, ?, ?)");
            ps.setString(1, key);
            ps.setString(2, contentType);
            ps.setInt(3, (int) size);
            ps.setString(4, label);
            ps.setBinaryStream(5, data, size);
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        });
    }

    public long totalSizeUnder(String prefix) {
        return files.totalSizeUnder(prefix);
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
