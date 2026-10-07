package al.pcmania.service.chat;

import al.pcmania.domain.ProviderUsage;
import al.pcmania.repo.ProviderUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ProviderUsageService {

    public static final String FALLBACK = "guided-fallback";

    private final ProviderUsageRepository repo;

    public record LastError(String message, LocalDateTime at) {}

    private final Map<String, LastError> lastErrors = new ConcurrentHashMap<>();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void request(String provider) {
        bump(provider, 1, 0, 0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void error(String provider, String message) {
        if (message != null && !message.isBlank()) {
            lastErrors.put(provider, new LastError(message.length() > 400 ? message.substring(0, 400) + "…" : message, LocalDateTime.now()));
        }
        bump(provider, 0, 1, 0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rateLimitHit(String provider) {
        bump(provider, 0, 0, 1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fallback() {
        bump(FALLBACK, 1, 0, 0);
    }

    public Optional<LastError> lastError(String provider) {
        return Optional.ofNullable(lastErrors.get(provider));
    }

    @Transactional(readOnly = true)
    public List<ProviderUsage> today() {
        return repo.findByDayOrderByProviderAsc(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<ProviderUsage> lastDays(int days) {
        return repo.findByDayGreaterThanEqualOrderByDayDescProviderAsc(LocalDate.now().minusDays(days - 1L));
    }

    @Transactional(readOnly = true)
    public int requestsToday(String provider) {
        return repo.findByProviderAndDay(provider, LocalDate.now()).map(ProviderUsage::getRequestCount).orElse(0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void bump(String provider, int requests, int errors, int rateLimits) {
        LocalDate today = LocalDate.now();
        ProviderUsage u = repo.findForUpdateByProviderAndDay(provider, today).orElseGet(() -> {
            ProviderUsage n = new ProviderUsage();
            n.setProvider(provider);
            n.setDay(today);
            return n;
        });
        u.setRequestCount(u.getRequestCount() + requests);
        u.setErrorCount(u.getErrorCount() + errors);
        u.setRateLimitHits(u.getRateLimitHits() + rateLimits);
        repo.save(u);
    }
}
