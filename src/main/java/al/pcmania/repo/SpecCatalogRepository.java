package al.pcmania.repo;

import al.pcmania.domain.SpecCatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SpecCatalogRepository extends JpaRepository<SpecCatalogEntry, Long> {
    List<SpecCatalogEntry> findByCategorySlugOrderByNameAsc(String categorySlug);

    List<SpecCatalogEntry> findAllByOrderByCategorySlugAscNameAsc();

    Optional<SpecCatalogEntry> findByGpuCatalogId(Long gpuCatalogId);

    @Query("select e.categorySlug, count(e) from SpecCatalogEntry e group by e.categorySlug")
    List<Object[]> countByCategory();
}
