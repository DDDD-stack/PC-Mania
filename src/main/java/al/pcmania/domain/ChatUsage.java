package al.pcmania.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_usage")
@Getter
@Setter
public class ChatUsage {
    @Id
    @Column(name = "month")
    private String month;
    private long inputTokens;
    private long outputTokens;
    private long cacheReadTokens;
    private long cacheWriteTokens;
    private long costMicroUsd;
    private int replies;
    private LocalDateTime updatedAt;

    public double costUsd() {
        return costMicroUsd / 1_000_000.0;
    }
}
