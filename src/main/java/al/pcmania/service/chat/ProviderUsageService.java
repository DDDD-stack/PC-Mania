package al.pcmania.service.chat;

import al.pcmania.domain.ProviderUsage;
import al.pcmania.repo.ProviderUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Per-provider, per-day counters: requests made, errors, rate-limit hits. "guided-fallback" counts the
 * times the finder answered because the chosen provider could not. Read in Admin > Asistenti against
 * the free tier's daily cap.
 */
@Service
@RequiredArgsConstructor
public class ProviderUsageService {

    /** The provider name under which fallbacks to the finder are counted. */
    public static final String FALLBACK = "guided-fallback";

    private final ProviderUsageRepository repo;

    // Each counter is its own short transaction (Spring proxies see calls from outside this bean only,
    // so the annotation sits on the public entry points, not on a shared private helper).

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void request(String provider) {
        bump(provider, 1, 0, 0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void error(String provider) {
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

    /** Locked on the row: concurrent replies must all count. */
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
