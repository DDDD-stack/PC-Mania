package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;
    private String filename;
    private int sortOrder;
    @Column(name = "is_primary")
    private boolean primary;

    public ProductImage(Product product, String filename, int sortOrder, boolean primary) {
        this.product = product;
        this.filename = filename;
        this.sortOrder = sortOrder;
        this.primary = primary;
    }
}
