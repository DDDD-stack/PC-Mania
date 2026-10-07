package al.pcmania.domain;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.UpcomingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

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
