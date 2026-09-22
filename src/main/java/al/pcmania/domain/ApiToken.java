package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class ApiToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private AdminUser adminUser;
    private String tokenHash;
    private String deviceName;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
}
