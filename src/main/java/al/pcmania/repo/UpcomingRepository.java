package al.pcmania.repo;

import al.pcmania.domain.Enums.UpcomingStatus;
import al.pcmania.domain.UpcomingProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UpcomingRepository extends JpaRepository<UpcomingProduct, Long> {

    List<UpcomingProduct> findByStatusOrderBySortOrderAscCreatedAtDesc(UpcomingStatus status);

    Page<UpcomingProduct> findByStatusInOrderBySortOrderAscCreatedAtDesc(Collection<UpcomingStatus> statuses, Pageable pageable);

    List<UpcomingProduct> findAllByOrderBySortOrderAscCreatedAtDesc();

    Optional<UpcomingProduct> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByStatus(UpcomingStatus status);

    /** Interest counts for a set of teasers, so a list page needs one extra query rather than one per row. */
    @Query("""
            select i.upcoming.id, count(i)
            from UpcomingInterest i
            where i.upcoming.id in :ids
            group by i.upcoming.id
            """)
    List<Object[]> interestCounts(Collection<Long> ids);
}
