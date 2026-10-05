package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * How many requests a provider answered on one day, and how many failed or hit its rate limit. The
 * admin reads it against the free tier's daily cap; "guided-fallback" rows count the times the
 * finder stood in for a provider that was busy, which is the signal to move to a paid provider.
 */
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
