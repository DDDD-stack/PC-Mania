package al.pcmania.repo;

import al.pcmania.domain.ProviderUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProviderUsageRepository extends JpaRepository<ProviderUsage, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProviderUsage> findForUpdateByProviderAndDay(String provider, LocalDate day);

    Optional<ProviderUsage> findByProviderAndDay(String provider, LocalDate day);

    List<ProviderUsage> findByDayOrderByProviderAsc(LocalDate day);

    List<ProviderUsage> findByDayGreaterThanEqualOrderByDayDescProviderAsc(LocalDate from);
}
