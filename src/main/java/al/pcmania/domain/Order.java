package al.pcmania.domain;

import al.pcmania.domain.Enums.DeliveryMethod;
import al.pcmania.domain.Enums.OrderStatus;
import al.pcmania.domain.Enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String orderNumber;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String city;
    private String address;
    private String customerNotes;
    @Enumerated(EnumType.STRING)
    private DeliveryMethod deliveryMethod;
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.NEW;
    private int subtotalLek;
    private int shippingLek;

    private int totalLek;

    private Long tradeRequestId;

    private int tradeCreditLek;
    private String adminNotes;
    private LocalDateTime createdAt;

    private LocalDateTime deliveredAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Set<OrderStatus> allowedTransitions() {
        return switch (status) {
            case NEW -> Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
            case CONFIRMED -> Set.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.CANCELLED);
            case SHIPPED -> Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }
}
