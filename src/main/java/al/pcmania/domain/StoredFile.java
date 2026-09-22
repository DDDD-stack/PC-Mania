package al.pcmania.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
    /**
     * Not @Lob: on Postgres that maps to a large object (an OID), not to bytea, and the insert
     * fails. Plain bytes bind correctly to bytea and to LONGBLOB alike.
     *
     * Field-level lazy loading would need Hibernate bytecode enhancement, which is not enabled,
     * so this is kept out of memory by never loading the entity - read through the projections
     * on StoredFileRepository instead.
     */
    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    private byte[] data;
    private LocalDateTime createdAt;
}
