package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

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

    private int messageCount;

    private String providerUsed;
    private boolean leadCaptured;

    @PrePersist
    void prePersist() {
        if (startedAt == null) startedAt = LocalDateTime.now();
        if (lastMessageAt == null) lastMessageAt = startedAt;
    }
}
