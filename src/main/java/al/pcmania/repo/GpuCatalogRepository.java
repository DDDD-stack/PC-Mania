package al.pcmania.repo;

import al.pcmania.domain.GpuCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GpuCatalogRepository extends JpaRepository<GpuCatalog, Long> {
    Optional<GpuCatalog> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<GpuCatalog> findAllByOrderByTierDescNameAsc();
}
