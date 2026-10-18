package al.pcmania.repo;

import al.pcmania.domain.PrebuiltSpec;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PrebuiltSpecRepository extends JpaRepository<PrebuiltSpec, Long> {

    @EntityGraph(attributePaths = "gpuCatalog")
    Optional<PrebuiltSpec> findByProductId(Long productId);

    @EntityGraph(attributePaths = "gpuCatalog")
    @Query("select s from PrebuiltSpec s where s.product.id in :ids")
    List<PrebuiltSpec> findByProductIds(@Param("ids") Collection<Long> productIds);

    @Query("""
            select coalesce(nullif(trim(s.gpuNameOverride), ''), g.name), count(s) from PrebuiltSpec s left join s.gpuCatalog g
            where s.product.categorySlug = :cat and s.product.status in :statuses
            group by coalesce(nullif(trim(s.gpuNameOverride), ''), g.name)""")
    List<Object[]> gpuFacets(@Param("cat") String categorySlug, @Param("statuses") Collection<al.pcmania.domain.Enums.ProductStatus> statuses);

    @Query("""
            select distinct s.ramGb from PrebuiltSpec s
            where s.product.categorySlug = :cat and s.product.status in :statuses and s.ramGb is not null order by s.ramGb""")
    List<Integer> ramFacets(@Param("cat") String categorySlug, @Param("statuses") Collection<al.pcmania.domain.Enums.ProductStatus> statuses);
}
