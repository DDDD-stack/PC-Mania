package al.pcmania.domain;

import al.pcmania.domain.Enums.LeadStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A customer the assistant could not serve from stock and who left their details to be called back.
 * {@code wantedItem} is the demand signal: Admin > Asistenti groups it to show what people ask for
 * that the shop does not have.
 */
@Entity
@Table(name = "chat_lead")
@Getter
@Setter
public class ChatLead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSession session;
    private String name;
    private String phone;
    private String wantedItem;
    private Integer budgetLek;
    private Integer psuWatts;
    private String notes;
    @Enumerated(EnumType.STRING)
    private LeadStatus status = LeadStatus.NEW;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
