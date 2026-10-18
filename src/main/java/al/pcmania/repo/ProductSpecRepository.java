package al.pcmania.repo;

import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.ProductSpec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductSpecRepository extends JpaRepository<ProductSpec, Long> {

    @Query("select s.specKey, s.specValue from ProductSpec s where s.product.categorySlug = :cat and s.product.status in :statuses and s.specKey in :keys group by s.specKey, s.specValue")
    List<Object[]> facets(@Param("cat") String categorySlug, @Param("statuses") Collection<ProductStatus> statuses, @Param("keys") Collection<String> keys);

    @Query("select s.product.id, s.specKey, s.specValue from ProductSpec s where s.product.id in :ids and s.specKey in :keys")
    List<Object[]> valuesFor(@Param("ids") Collection<Long> productIds, @Param("keys") Collection<String> keys);

    @Query("select distinct s.specKey from ProductSpec s where s.product.categorySlug = :cat order by s.specKey")
    List<String> keysInCategory(@Param("cat") String categorySlug);
}
