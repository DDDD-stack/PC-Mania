package al.pcmania.repo;

import al.pcmania.domain.ProductSlugHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductSlugHistoryRepository extends JpaRepository<ProductSlugHistory, Long> {
    Optional<ProductSlugHistory> findByOldSlug(String oldSlug);

    @Modifying
    @Query("delete from ProductSlugHistory h where h.oldSlug = :slug")
    void deleteByOldSlug(String slug);
}
