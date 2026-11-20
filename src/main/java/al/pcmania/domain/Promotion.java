package al.pcmania.domain;

import al.pcmania.domain.Enums.DiscountType;
import al.pcmania.domain.Enums.PromoState;
import al.pcmania.domain.Enums.PromoTheme;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Promotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String headline;
    @Enumerated(EnumType.STRING)
    private DiscountType discountType = DiscountType.PERCENT;
    private int discountValue;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    @Enumerated(EnumType.STRING)
    private PromoTheme theme = PromoTheme.FIRE;
    private boolean showBanner = true;
    @Column(name = "is_active")
    private boolean active = true;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PromotionProduct> items = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public PromoState stateAt(LocalDateTime now) {
        if (!now.isBefore(endsAt)) return PromoState.ENDED;
        if (!active) return PromoState.PAUSED;
        if (now.isBefore(startsAt)) return PromoState.SCHEDULED;
        return PromoState.LIVE;
    }

    public int salePriceFor(int regularPriceLek, Integer fixedPriceLek) {
        return salePrice(discountType, discountValue, regularPriceLek, fixedPriceLek);
    }

    public static int salePrice(DiscountType type, int value, int regularPriceLek, Integer fixedPriceLek) {
        if (fixedPriceLek != null) return fixedPriceLek;
        int raw = switch (type) {
            case PERCENT -> (int) ((long) regularPriceLek * (100 - value) / 100);
            case AMOUNT -> regularPriceLek - value;
        };
        return roundDown(raw);
    }

    static int roundDown(int lek) {
        if (lek <= 0) return 0;
        return lek >= 1000 ? lek - lek % 100 : lek - lek % 10;
    }
}
