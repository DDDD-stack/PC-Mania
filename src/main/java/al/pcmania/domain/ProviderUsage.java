package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "provider_usage", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "day"}))
@Getter
@Setter
public class ProviderUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String provider;
    private LocalDate day;
    private int requestCount;
    private int errorCount;
    private int rateLimitHits;
}
