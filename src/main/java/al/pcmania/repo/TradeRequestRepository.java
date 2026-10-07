package al.pcmania.repo;

import al.pcmania.domain.Enums.TradeStatus;
import al.pcmania.domain.TradeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TradeRequestRepository extends JpaRepository<TradeRequest, Long> {

    @EntityGraph(attributePaths = "product")
    Page<TradeRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "product")
    Page<TradeRequest> findByStatusOrderByCreatedAtDesc(TradeStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "product")
    Optional<TradeRequest> findWithProductById(Long id);

    long countByStatus(TradeStatus status);

    long countByStatusIn(Collection<TradeStatus> statuses);

    Optional<TradeRequest> findByRequestNumber(String requestNumber);

    List<TradeRequest> findByStatusAndStockProductIdIsNullOrderByClosedAtAsc(TradeStatus status);

    List<TradeRequest> findByStatusAndQuoteExpiresAtBefore(TradeStatus status, LocalDateTime cutoff);

    @Query("""
            select t from TradeRequest t
            where t.mediaFilename is not null and t.closedAt is not null and t.closedAt < :cutoff""")
    List<TradeRequest> findMediaToDelete(@Param("cutoff") LocalDateTime cutoff);
}
