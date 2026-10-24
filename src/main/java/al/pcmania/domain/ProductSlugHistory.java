package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ProductSlugHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String oldSlug;
    private Long productId;
    private LocalDateTime createdAt = LocalDateTime.now();

    public ProductSlugHistory(String oldSlug, Long productId) {
        this.oldSlug = oldSlug;
        this.productId = productId;
    }
}
