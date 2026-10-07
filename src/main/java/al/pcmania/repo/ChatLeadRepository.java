package al.pcmania.repo;

import al.pcmania.domain.ChatLead;
import al.pcmania.domain.Enums.LeadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatLeadRepository extends JpaRepository<ChatLead, Long> {
    Page<ChatLead> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<ChatLead> findByStatusOrderByCreatedAtDesc(LeadStatus status, Pageable pageable);
    long countByStatus(LeadStatus status);
    List<ChatLead> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<ChatLead> findAllByOrderByCreatedAtDesc();
}
