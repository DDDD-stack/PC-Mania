package al.pcmania.repo;

import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.ProductSpec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductSpecRepository extends JpaRepository<ProductSpec, Long> {

    @Query("select s.specKey, s.specValue, min(s.sortOrder) from ProductSpec s where s.product.categorySlug = :cat and s.product.status = :status group by s.specKey, s.specValue")
    List<Object[]> facets(@Param("cat") String categorySlug, @Param("status") ProductStatus status);

    @Query("select distinct s.specKey from ProductSpec s where s.product.categorySlug = :cat order by s.specKey")
    List<String> keysInCategory(@Param("cat") String categorySlug);
}
