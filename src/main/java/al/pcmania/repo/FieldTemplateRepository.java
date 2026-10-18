package al.pcmania.repo;

import al.pcmania.domain.CategoryFieldTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FieldTemplateRepository extends JpaRepository<CategoryFieldTemplate, Long> {

    @Query("select t from CategoryFieldTemplate t join fetch t.category c order by c.sortOrder, t.sortOrder, t.id")
    List<CategoryFieldTemplate> findAllWithCategory();

    @Query("select t from CategoryFieldTemplate t where t.category.id = :categoryId order by t.sortOrder, t.id")
    List<CategoryFieldTemplate> findByCategory(@Param("categoryId") Long categoryId);

    boolean existsByCategoryIdAndFieldKey(Long categoryId, String fieldKey);
}
