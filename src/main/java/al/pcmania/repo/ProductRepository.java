package al.pcmania.repo;

import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = "brand")
    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("select p.id from Product p where p.slug = :slug")
    Optional<Long> findIdBySlug(@Param("slug") String slug);

    @Override
    @EntityGraph(attributePaths = "brand")
    org.springframework.data.domain.Page<Product> findAll(org.springframework.data.jpa.domain.Specification<Product> spec, Pageable pageable);


    @EntityGraph(attributePaths = "brand")
    List<Product> findByStatusOrderByListedAtDesc(ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "brand")
    List<Product> findByStatusOrderByListedAtAsc(ProductStatus status);

    @EntityGraph(attributePaths = "brand")
    List<Product> findByCategorySlugAndStatusAndIdNotOrderByListedAtDesc(String categorySlug, ProductStatus status, Long id, Pageable pageable);

    List<Product> findByStatusNot(ProductStatus status);

    List<Product> findByStatusIn(Collection<ProductStatus> statuses);

    @Modifying
    @Query("update Product p set p.viewCount = p.viewCount + 1 where p.id = :id")
    void incrementViews(@Param("id") Long id);

    @Query("select distinct b from Product p join p.brand b where p.categorySlug = :cat and p.status = :status order by b.name")
    List<al.pcmania.domain.Brand> brandsInCategory(@Param("cat") String categorySlug, @Param("status") ProductStatus status);

    @Query("select p.categorySlug, count(p) from Product p where p.status = :status group by p.categorySlug")
    List<Object[]> countByCategory(@Param("status") ProductStatus status);

    @Query("select p.categorySlug, count(p) from Product p group by p.categorySlug")
    List<Object[]> countByCategoryAllStatuses();

    long countByCategorySlug(String categorySlug);
}
