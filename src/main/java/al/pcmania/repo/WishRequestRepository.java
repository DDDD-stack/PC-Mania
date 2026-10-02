package al.pcmania.repo;

import al.pcmania.domain.Enums.WishStatus;
import al.pcmania.domain.WishRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishRequestRepository extends JpaRepository<WishRequest, Long> {
    Page<WishRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<WishRequest> findByStatusOrderByCreatedAtDesc(WishStatus status, Pageable pageable);
    long countByStatus(WishStatus status);
}
