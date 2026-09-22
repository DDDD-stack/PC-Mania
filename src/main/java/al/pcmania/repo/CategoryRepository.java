package al.pcmania.repo;

import al.pcmania.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderBySortOrderAsc();

    /** Categories shown on the public site. */
    List<Category> findByVisibleTrueOrderBySortOrderAsc();

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
