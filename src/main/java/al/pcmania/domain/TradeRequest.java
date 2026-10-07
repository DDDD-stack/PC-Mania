package al.pcmania.domain;

import al.pcmania.domain.Enums.ContactMethod;
import al.pcmania.domain.Enums.TradeItemType;
import al.pcmania.domain.Enums.TradeMediaType;
import al.pcmania.domain.Enums.TradeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class TradeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String requestNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    private String productTitleSnapshot;

    private String customerName;
    @Enumerated(EnumType.STRING)
    private ContactMethod contactMethod;
    private String customerPhone;
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    private TradeItemType itemType;
    private String manufacturer;
    private String model;
    private String extraNotes;

    private String mediaFilename;
    @Enumerated(EnumType.STRING)
    private TradeMediaType mediaType;
    private boolean whatsappInstead;

    @Enumerated(EnumType.STRING)
    private TradeStatus status = TradeStatus.NEW;
    private Integer quotedValueLek;
    private String quoteNotes;
    private String declineReason;
    private LocalDateTime quotedAt;
    private LocalDateTime quoteExpiresAt;

    private LocalDateTime quoteEmailedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private Long orderId;

    private Long stockProductId;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public String getContact() {
        return contactMethod == ContactMethod.EMAIL ? customerEmail : customerPhone;
    }

    public boolean isNeedsCall() {
        return status == TradeStatus.QUOTED && quoteEmailedAt == null;
    }

    public void close(TradeStatus finalStatus) {
        status = finalStatus;
        closedAt = LocalDateTime.now();
    }
}
