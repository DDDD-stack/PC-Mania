package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ProductSpec {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;
    private String specKey;
    private String specValue;
    private int sortOrder;

    public ProductSpec(Product product, String specKey, String specValue, int sortOrder) {
        this.product = product;
        this.specKey = specKey;
        this.specValue = specValue;
        this.sortOrder = sortOrder;
    }
}
