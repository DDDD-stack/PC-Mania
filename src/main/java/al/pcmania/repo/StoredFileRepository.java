package al.pcmania.repo;

import al.pcmania.domain.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    <T> Optional<T> findByFileKey(String fileKey, Class<T> type);

    List<Meta> findByFileKeyStartingWithOrderByCreatedAtDesc(String prefix);

    boolean existsByFileKey(String fileKey);

    @Query("select coalesce(sum(f.sizeBytes), 0) from StoredFile f where f.fileKey like concat(:prefix, '%')")
    long totalSizeUnder(@Param("prefix") String prefix);

    void deleteByFileKey(String fileKey);

    interface Meta {
        String getFileKey();
        String getContentType();
        int getSizeBytes();
        String getLabel();
        LocalDateTime getCreatedAt();
    }

    interface Dimensions {
        Integer getWidthPx();
        Integer getHeightPx();
    }

    interface Content {
        String getContentType();
        int getSizeBytes();
        byte[] getData();
    }
}
