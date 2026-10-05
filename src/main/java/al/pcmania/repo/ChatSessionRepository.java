package al.pcmania.repo;

import al.pcmania.domain.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findBySessionToken(String token);
    Page<ChatSession> findAllByOrderByLastMessageAtDesc(Pageable pageable);
}
