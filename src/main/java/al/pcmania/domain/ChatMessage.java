package al.pcmania.domain;

import al.pcmania.domain.Enums.ChatRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One turn of a conversation: what the customer typed, or what the assistant answered. */
@Entity
@Table(name = "chat_message")
@Getter
@Setter
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSession session;
    @Enumerated(EnumType.STRING)
    private ChatRole role;
    @Column(columnDefinition = "TEXT")
    private String content;
    /** For assistant turns: the tools called and the product slugs they surfaced, as JSON. Null otherwise. */
    @Column(columnDefinition = "TEXT")
    private String toolCallsJson;
    /** For assistant turns: the provider that wrote it (gemini, anthropic, guided). */
    private String providerUsed;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
