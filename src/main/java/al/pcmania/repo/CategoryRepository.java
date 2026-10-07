package al.pcmania.repo;

import al.pcmania.config.CacheConfig;
import al.pcmania.domain.Category;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderBySortOrderAsc();

    @Cacheable(CacheConfig.CATEGORIES)
    List<Category> findByVisibleTrueOrderBySortOrderAsc();

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
