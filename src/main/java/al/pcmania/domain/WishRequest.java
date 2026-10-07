package al.pcmania.domain;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.WishStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class WishRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customerName;
    private String customerPhone;

    private String item;

    private Integer maxPriceLek;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition")
    private Condition condition;
    private String notes;
    @Enumerated(EnumType.STRING)
    private WishStatus status = WishStatus.NEW;
    private String adminNotes;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
