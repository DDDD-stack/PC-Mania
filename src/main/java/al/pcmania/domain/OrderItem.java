package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;
    private String titleSnapshot;
    private int priceLekSnapshot;
    /** Cost at sale time, so later cost edits don't rewrite margin history. */
    private int costLekSnapshot;
    private int quantity;

    public int getLineTotalLek() {
        return priceLekSnapshot * quantity;
    }
}
