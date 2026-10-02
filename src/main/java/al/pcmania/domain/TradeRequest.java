package al.pcmania.domain;

import al.pcmania.domain.Enums.ContactMethod;
import al.pcmania.domain.Enums.TradeItemType;
import al.pcmania.domain.Enums.TradeMediaType;
import al.pcmania.domain.Enums.TradeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * "Nderro": a customer asks what an old GPU, CPU or RAM is worth against a product in stock. A quote
 * request only - it creates no order and reserves nothing until the operator quotes and the customer
 * accepts, and then {@code orderId} links the order made from it.
 */
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

    /** Key of the proof in stored_file; null once deleted, or when it comes by WhatsApp. */
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
    /** When the quote email went out; null for phone customers, or if mail is not configured. */
    private LocalDateTime quoteEmailedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private Long orderId;
    /** The Draft product made from the traded-in item once it is taken into stock. */
    private Long stockProductId;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public String getContact() {
        return contactMethod == ContactMethod.EMAIL ? customerEmail : customerPhone;
    }

    /** The quote still has to reach a customer who gets no email: the admin list flags these "call". */
    public boolean isNeedsCall() {
        return status == TradeStatus.QUOTED && quoteEmailedAt == null;
    }

    public void close(TradeStatus finalStatus) {
        status = finalStatus;
        closedAt = LocalDateTime.now();
    }
}
