package al.pcmania.repo;

import al.pcmania.domain.ChatUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ChatUsageRepository extends JpaRepository<ChatUsage, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChatUsage> findForUpdateByMonth(String month);
}
