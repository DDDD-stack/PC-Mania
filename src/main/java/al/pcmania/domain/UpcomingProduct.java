package al.pcmania.domain;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.UpcomingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A "Së shpejti" teaser: stock that is on its way but cannot be ordered yet. Customers leave a phone
 * number instead of buying, and the operator calls them once it arrives. Linking {@code product}
 * lets the teaser point at the real listing once it is published.
 */
@Entity
@Getter
@Setter
public class UpcomingProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String slug;
    private String teaser;
    private String categorySlug;
    private Integer expectedPriceLek;
    /** Free text such as "Brenda javës" or "Fundi i shtatorit" — vague on purpose, shipments slip. */
    private String expectedLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition")
    private Condition condition;

    @Enumerated(EnumType.STRING)
    private UpcomingStatus status = UpcomingStatus.HIDDEN;

    private String imageFilename;
    private int sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
