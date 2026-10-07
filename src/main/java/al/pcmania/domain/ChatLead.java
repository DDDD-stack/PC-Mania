package al.pcmania.domain;

import al.pcmania.domain.Enums.LeadSource;
import al.pcmania.domain.Enums.LeadStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

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
    private LeadSource source = LeadSource.CHAT;
    @Enumerated(EnumType.STRING)
    private LeadStatus status = LeadStatus.NEW;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
