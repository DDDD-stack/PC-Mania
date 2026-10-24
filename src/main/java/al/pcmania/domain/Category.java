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

    @Column(name = "is_visible")
    private boolean visible = true;

    @Column(name = "is_out_of_stock")
    private boolean outOfStock;

    private String catalogSlug;

    private String metaTitle;
    private String metaDescription;
    private String introHtml;

    public static final String PREBUILT = "prebuilt";

    public String catalogKey() {
        return catalogSlug != null && !catalogSlug.isBlank() ? catalogSlug : slug;
    }

    public boolean isPrebuilt() {
        return PREBUILT.equals(catalogSlug);
    }
}
