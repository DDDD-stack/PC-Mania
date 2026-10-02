package al.pcmania.domain;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.WishStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Something a customer asked the shop to bring in ("Kërko një produkt"). Free text on purpose: people ask
 * for "an RTX 3070 or similar" rather than for a listing. The operator calls back once it is found.
 */
@Entity
@Getter
@Setter
public class WishRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customerName;
    private String customerPhone;
    /** What they are looking for, in their own words. */
    private String item;
    /** The most they want to pay; null when they did not say. */
    private Integer maxPriceLek;
    /** Preferred condition; null means any. */
    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition")
    private Condition condition;
    private String notes;
    @Enumerated(EnumType.STRING)
    private WishStatus status = WishStatus.NEW;
    private String adminNotes;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
