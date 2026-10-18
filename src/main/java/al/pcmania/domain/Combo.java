package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Combo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String slug;
    private String titleSq;
    private String subtitleSq;
    private int comboPriceLek;
    @Column(name = "is_active")
    private boolean active = true;
    private int sortOrder;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ComboItem> items = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public int originalTotalLek() {
        return items.stream().mapToInt(i -> i.getProduct().getPriceLek() * i.getQuantity()).sum();
    }

    public int savingsLek() {
        return originalTotalLek() - comboPriceLek;
    }
}
