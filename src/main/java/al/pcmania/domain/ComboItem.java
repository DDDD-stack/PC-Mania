package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ComboItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Combo combo;
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;
    private int quantity = 1;

    public ComboItem(Combo combo, Product product, int quantity) {
        this.combo = combo;
        this.product = product;
        this.quantity = quantity;
    }
}
