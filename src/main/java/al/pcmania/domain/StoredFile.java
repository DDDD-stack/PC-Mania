package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An uploaded file kept in the database, because the hosted service has no persistent disk.
 * {@code fileKey} doubles as the path the file is served at.
 */
@Entity
@Getter
@Setter
public class StoredFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fileKey;
    private String contentType;
    private int sizeBytes;
    private Integer widthPx;
    private Integer heightPx;
    private String label;
    /** Lazy: listing files in the admin screen must not drag a 34 MB APK into memory. */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] data;
    private LocalDateTime createdAt;
}
