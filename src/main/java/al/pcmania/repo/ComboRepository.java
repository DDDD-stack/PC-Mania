package al.pcmania.repo;

import al.pcmania.domain.Combo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ComboRepository extends JpaRepository<Combo, Long> {

    String VISIBLE = """
            c.active = true
            and (c.startsAt is null or c.startsAt <= :now)
            and (c.endsAt is null or c.endsAt > :now)
            and exists (select 1 from ComboItem i where i.combo = c)
            and not exists (
                select 1 from ComboItem i join i.product p
                where i.combo = c
                  and (p.status <> al.pcmania.domain.Enums.ProductStatus.ACTIVE or p.quantity < i.quantity))""";

    @Query("select c from Combo c where " + VISIBLE + " order by c.sortOrder, c.id")
    List<Combo> findVisible(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("select count(c) > 0 from Combo c where c.id = :id and " + VISIBLE)
    boolean isVisible(@Param("id") Long id, @Param("now") LocalDateTime now);

    Optional<Combo> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Combo> findAllByOrderBySortOrderAscIdAsc();

    @Query("select distinct i.combo from ComboItem i where i.product.id = :productId")
    List<Combo> findContaining(@Param("productId") Long productId);
}
