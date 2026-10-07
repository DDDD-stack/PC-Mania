package al.pcmania.repo;

import al.pcmania.config.CacheConfig;
import al.pcmania.domain.Enums.UpcomingStatus;
import al.pcmania.domain.UpcomingProduct;
import org.springframework.cache.annotation.Cacheable;
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

    @Cacheable(CacheConfig.UPCOMING_COUNTS)
    long countByStatus(UpcomingStatus status);

    @Query("""
            select i.upcoming.id, count(i)
            from UpcomingInterest i
            where i.upcoming.id in :ids
            group by i.upcoming.id
            """)
    List<Object[]> interestCounts(Collection<Long> ids);
}
