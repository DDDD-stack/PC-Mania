package al.pcmania.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CATEGORIES = "visibleCategories";

    public static final String UPCOMING_COUNTS = "upcomingCounts";

    public static final String GPU_CATALOG = "gpuCatalog";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager caffeine = new CaffeineCacheManager(CATEGORIES, UPCOMING_COUNTS, GPU_CATALOG);
        caffeine.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).maximumSize(100));

        return new TransactionAwareCacheManagerProxy(caffeine);
    }
}
