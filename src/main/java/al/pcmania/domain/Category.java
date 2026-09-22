package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nameSq;
    private String slug;
    private int sortOrder;
    private String iconClass;

    /** Hidden categories disappear from the shop entirely (navigation, home, sitemap; the page 404s). */
    @Column(name = "is_visible")
    private boolean visible = true;

    /** Listed as usual but flagged "Pa stok", with a notice on the category page. */
    @Column(name = "is_out_of_stock")
    private boolean outOfStock;
}
