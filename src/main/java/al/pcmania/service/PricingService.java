package al.pcmania.service;

import al.pcmania.domain.Enums.DiscountType;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Enums.PromoTheme;
import al.pcmania.domain.Product;
import al.pcmania.domain.Promotion;
import al.pcmania.repo.PromotionRepository;
import al.pcmania.web.view.Deals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PricingService {

    static final Duration MAX_SNAPSHOT_AGE = Duration.ofMinutes(10);

    public record PromotionsChanged() {}

    public record Sale(int priceLek, int regularPriceLek, Long promotionId, String promotionName, PromoTheme theme,
                       LocalDateTime endsAt) {

        public int savingsLek() {
            return regularPriceLek - priceLek;
        }

        public int percent() {
            return (int) Math.round(savingsLek() * 100.0 / regularPriceLek);
        }
    }

    public record LivePromotion(Long id, String name, String headline, PromoTheme theme, boolean showBanner,
                                LocalDateTime startsAt, LocalDateTime endsAt, DiscountType discountType,
                                int discountValue, Set<Long> productIds, Map<Long, Integer> fixedPrices, int maxPercent) {

        public boolean liveAt(LocalDateTime now) {
            return !now.isBefore(startsAt) && now.isBefore(endsAt);
        }

        public String getBannerText() {
            if (headline != null && !headline.isBlank()) return headline;
            return maxPercent > 0 ? name + " · deri -" + maxPercent + "%" : name;
        }

        public long getEndsAtMillis() {
            return Deals.epochMillis(endsAt);
        }

        public String getThemeClass() {
            return "promo-theme-" + theme.name();
        }

        int priceFor(Product p) {
            return Promotion.salePrice(discountType, discountValue, p.getPriceLek(), fixedPrices.get(p.getId()));
        }
    }

    private record Snapshot(List<LivePromotion> promotions, Map<Long, List<LivePromotion>> byProduct, Instant loadedAt) {}

    private final PromotionRepository promotions;
    private volatile Snapshot snapshot;

    public Optional<Sale> saleFor(Product p) {
        if (p == null || p.getId() == null || p.getStatus() == ProductStatus.SOLD) return Optional.empty();
        List<LivePromotion> candidates = snapshot().byProduct().getOrDefault(p.getId(), List.of());
        if (candidates.isEmpty()) return Optional.empty();
        LocalDateTime now = LocalDateTime.now();
        LivePromotion best = null;
        int bestPrice = p.getPriceLek();
        for (LivePromotion promo : candidates) {
            if (!promo.liveAt(now)) continue;
            int price = promo.priceFor(p);
            if (price > 0 && price < bestPrice) {
                bestPrice = price;
                best = promo;
            }
        }
        if (best == null) return Optional.empty();
        return Optional.of(new Sale(bestPrice, p.getPriceLek(), best.id(), best.name(), best.theme(), best.endsAt()));
    }

    public int priceNow(Product p) {
        return saleFor(p).map(Sale::priceLek).orElse(p.getPriceLek());
    }

    public List<LivePromotion> live() {
        LocalDateTime now = LocalDateTime.now();
        return snapshot().promotions().stream().filter(p -> p.liveAt(now)).toList();
    }

    public Optional<LivePromotion> banner() {
        return live().stream().filter(LivePromotion::showBanner).findFirst();
    }

    public Set<Long> productIdsInLivePromotions() {
        Set<Long> ids = new LinkedHashSet<>();
        live().forEach(p -> ids.addAll(p.productIds()));
        return ids;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onChange(PromotionsChanged event) {
        snapshot = null;
    }

    private Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s == null || s.loadedAt().plus(MAX_SNAPSHOT_AGE).isBefore(Instant.now())) {
            s = load();
            snapshot = s;
        }
        return s;
    }

    private Snapshot load() {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Set<Long>> members = new HashMap<>();
        Map<Long, Map<Long, Integer>> fixed = new HashMap<>();
        Map<Long, Map<Long, Integer>> regular = new HashMap<>();
        for (Object[] row : promotions.findItemRows(now)) {
            Long promotionId = (Long) row[0];
            Long productId = (Long) row[1];
            members.computeIfAbsent(promotionId, k -> new LinkedHashSet<>()).add(productId);
            if (row[2] != null) fixed.computeIfAbsent(promotionId, k -> new HashMap<>()).put(productId, (Integer) row[2]);
            regular.computeIfAbsent(promotionId, k -> new HashMap<>()).put(productId, (Integer) row[3]);
        }
        List<LivePromotion> list = new ArrayList<>();
        Map<Long, List<LivePromotion>> byProduct = new HashMap<>();
        for (Promotion pr : promotions.findNotEnded(now)) {
            if (!pr.isActive()) continue;
            Set<Long> ids = Collections.unmodifiableSet(members.getOrDefault(pr.getId(), Set.of()));
            Map<Long, Integer> fixedPrices = fixed.getOrDefault(pr.getId(), Map.of());
            int maxPercent = 0;
            for (Map.Entry<Long, Integer> e : regular.getOrDefault(pr.getId(), Map.of()).entrySet()) {
                int sale = pr.salePriceFor(e.getValue(), fixedPrices.get(e.getKey()));
                if (sale > 0 && sale < e.getValue()) maxPercent = Math.max(maxPercent, Deals.percent(e.getValue(), sale));
            }
            LivePromotion live = new LivePromotion(pr.getId(), pr.getName(), pr.getHeadline(), pr.getTheme(), pr.isShowBanner(),
                    pr.getStartsAt(), pr.getEndsAt(), pr.getDiscountType(), pr.getDiscountValue(), ids,
                    Collections.unmodifiableMap(fixedPrices), maxPercent);
            list.add(live);
            for (Long id : ids) byProduct.computeIfAbsent(id, k -> new ArrayList<>()).add(live);
        }
        list.sort(Comparator.comparing(LivePromotion::endsAt).thenComparing(LivePromotion::id));
        return new Snapshot(List.copyOf(list), Collections.unmodifiableMap(byProduct), Instant.now());
    }
}
