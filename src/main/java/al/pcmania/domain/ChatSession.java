package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One conversation with the assistant. The browser keeps {@code sessionToken} and sends it with every
 * message; nothing else identifies the visitor until they leave a name and phone through a lead.
 */
@Entity
@Table(name = "chat_session")
@Getter
@Setter
public class ChatSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sessionToken;
    private LocalDateTime startedAt;
    private LocalDateTime lastMessageAt;
    /** Customer messages so far; the per-session guardrail counts these. */
    private int messageCount;
    /** Which provider answered last: gemini, anthropic or guided. */
    private String providerUsed;
    private boolean leadCaptured;

    @PrePersist
    void prePersist() {
        if (startedAt == null) startedAt = LocalDateTime.now();
        if (lastMessageAt == null) lastMessageAt = startedAt;
    }
}
