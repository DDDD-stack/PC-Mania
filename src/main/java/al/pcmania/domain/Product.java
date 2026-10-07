package al.pcmania.domain;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.ProductStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    @ManyToOne(fetch = FetchType.LAZY)
    private Brand brand;
    private String model;
    private String categorySlug;
    private String slug;
    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition")
    private Condition condition = Condition.USED;
    private int priceLek;
    private int costLek;
    private int quantity = 1;
    @Enumerated(EnumType.STRING)
    private ProductStatus status = ProductStatus.DRAFT;
    private String shortDescription;
    private String fullDescription;
    private Integer warrantyDays;
    private String testNotes;
    @Column(name = "is_mining_free")
    private boolean miningFree;
    private boolean transportIncluded;

    private boolean tradeEligible;

    private Integer maxTradeValueLek;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gpu_model_id")
    private GpuCatalog gpuModel;
    private LocalDateTime createdAt;
    private LocalDateTime listedAt;
    private LocalDateTime soldAt;
    private int viewCount;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<ProductSpec> specs = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<ProductImage> images = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public void changeStatus(ProductStatus newStatus) {
        if (newStatus == ProductStatus.ACTIVE && listedAt == null) listedAt = LocalDateTime.now();
        if (newStatus == ProductStatus.SOLD && soldAt == null) soldAt = LocalDateTime.now();
        if (newStatus != ProductStatus.SOLD) soldAt = null;
        status = newStatus;
    }

    public ProductImage getPrimaryImage() {
        return images.stream().filter(ProductImage::isPrimary).findFirst()
                .orElse(images.isEmpty() ? null : images.getFirst());
    }
}
