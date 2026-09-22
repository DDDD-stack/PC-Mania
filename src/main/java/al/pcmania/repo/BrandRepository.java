package al.pcmania.repo;

import al.pcmania.domain.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    List<Brand> findAllByOrderByNameAsc();
    Optional<Brand> findBySlug(String slug);
    Optional<Brand> findByNameIgnoreCase(String name);
}
