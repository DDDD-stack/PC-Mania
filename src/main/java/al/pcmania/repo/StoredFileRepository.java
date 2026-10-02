package al.pcmania.repo;

import al.pcmania.domain.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Total bytes stored under a key prefix, e.g. "trade/" for trade-in proof media. */
    @Query("select coalesce(sum(f.sizeBytes), 0) from StoredFile f where f.fileKey like concat(:prefix, '%')")
    long totalSizeUnder(@Param("prefix") String prefix);

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
