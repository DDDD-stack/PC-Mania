package al.pcmania.repo;

import al.pcmania.domain.BuildRequest;
import al.pcmania.domain.Enums.BuildStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildRequestRepository extends JpaRepository<BuildRequest, Long> {
    Page<BuildRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<BuildRequest> findByStatusOrderByCreatedAtDesc(BuildStatus status, Pageable pageable);
    Page<BuildRequest> findByStatusInOrderByCreatedAtDesc(java.util.Collection<BuildStatus> statuses, Pageable pageable);
    long countByStatus(BuildStatus status);
}
