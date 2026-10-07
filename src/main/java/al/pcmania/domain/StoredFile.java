package al.pcmania.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

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

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    private byte[] data;
    private LocalDateTime createdAt;
}
