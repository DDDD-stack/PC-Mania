package al.pcmania.repo;

import al.pcmania.domain.UpcomingInterest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UpcomingInterestRepository extends JpaRepository<UpcomingInterest, Long> {

    List<UpcomingInterest> findByUpcomingIdOrderByCreatedAtDesc(Long upcomingId);

    boolean existsByUpcomingIdAndCustomerPhone(Long upcomingId, String customerPhone);

    long countByUpcomingId(Long upcomingId);

    long countByUpcomingIdAndNotifiedFalse(Long upcomingId);
}
