package al.pcmania.domain;

import al.pcmania.domain.Enums.BuildStatus;
import al.pcmania.domain.Enums.UseCase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class BuildRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customerName;
    private String customerPhone;
    private int budgetLek;
    @Enumerated(EnumType.STRING)
    private UseCase useCase;
    private String notes;
    @Enumerated(EnumType.STRING)
    private BuildStatus status = BuildStatus.NEW;
    private Integer quotedTotalLek;
    private String adminNotes;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
