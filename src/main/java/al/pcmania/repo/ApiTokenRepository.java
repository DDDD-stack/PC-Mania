package al.pcmania.repo;

import al.pcmania.domain.ApiToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {
    @EntityGraph(attributePaths = "adminUser")
    Optional<ApiToken> findByTokenHash(String tokenHash);
}
