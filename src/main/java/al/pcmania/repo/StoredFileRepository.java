package al.pcmania.repo;

import al.pcmania.domain.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    /**
     * Always read through one of the projections below. Loading the entity pulls the whole file
     * into memory, which for an uploaded APK is tens of megabytes.
     */
    <T> Optional<T> findByFileKey(String fileKey, Class<T> type);

    List<Meta> findByFileKeyStartingWithOrderByCreatedAtDesc(String prefix);

    boolean existsByFileKey(String fileKey);

    void deleteByFileKey(String fileKey);

    /** Everything except the bytes. */
    interface Meta {
        String getFileKey();
        String getContentType();
        int getSizeBytes();
        String getLabel();
        LocalDateTime getCreatedAt();
    }

    /** Pixel size, recorded at upload for Open Graph tags. */
    interface Dimensions {
        Integer getWidthPx();
        Integer getHeightPx();
    }

    /** The bytes, for serving a download. */
    interface Content {
        String getContentType();
        int getSizeBytes();
        byte[] getData();
    }
}
