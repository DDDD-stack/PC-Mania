package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class PromotionProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Promotion promotion;
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;
    private Integer salePriceLek;

    public PromotionProduct(Promotion promotion, Product product, Integer salePriceLek) {
        this.promotion = promotion;
        this.product = product;
        this.salePriceLek = salePriceLek;
    }
}
