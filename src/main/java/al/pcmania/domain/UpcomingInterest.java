package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Someone who asked to be called when an upcoming item arrives. */
@Entity
@Getter
@Setter
public class UpcomingInterest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upcoming_id")
    private UpcomingProduct upcoming;

    private String customerName;
    private String customerPhone;
    private boolean notified;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
