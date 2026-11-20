package al.pcmania.repo;

import al.pcmania.domain.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    @Query("select pr from Promotion pr where pr.endsAt > :now")
    List<Promotion> findNotEnded(@Param("now") LocalDateTime now);

    @Query("select i.promotion.id, i.product.id, i.salePriceLek, i.product.priceLek from PromotionProduct i where i.promotion.endsAt > :now")
    List<Object[]> findItemRows(@Param("now") LocalDateTime now);

    @Query("select distinct pr from Promotion pr left join fetch pr.items i left join fetch i.product order by pr.startsAt desc, pr.id desc")
    List<Promotion> findAllWithItems();

    @Query("select pr from Promotion pr left join fetch pr.items i left join fetch i.product where pr.id = :id")
    Optional<Promotion> findWithItems(@Param("id") Long id);
}
