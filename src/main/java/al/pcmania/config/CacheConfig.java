package al.pcmania.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caches the few lookups every public page repeats: the visible categories (header, menu, footer) and
 * whether anything is listed under "Së shpejti". The database is in another data centre from the web
 * service, so each of those queries costs a network round trip on every page view.
 *
 * Everything that changes the underlying rows evicts the cache (see {@link #CATEGORIES} and
 * {@link #UPCOMING_COUNTS}). The expiry is only a safety net for rows edited directly in the database.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Visible categories, as the shop's navigation shows them. Evicted by CategoryAdminService. */
    public static final String CATEGORIES = "visibleCategories";

    /** Teaser counts per status. Evicted by UpcomingService. */
    public static final String UPCOMING_COUNTS = "upcomingCounts";

    /** The GPU catalogue, read per keystroke by the admin autofill. Evicted by GpuCatalogService. */
    public static final String GPU_CATALOG = "gpuCatalog";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager caffeine = new CaffeineCacheManager(CATEGORIES, UPCOMING_COUNTS, GPU_CATALOG);
        caffeine.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).maximumSize(100));
        // Evictions wait for the commit, so a page rendered mid-transaction cannot put the old rows back.
        return new TransactionAwareCacheManagerProxy(caffeine);
    }
}
