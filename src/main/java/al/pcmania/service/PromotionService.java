package al.pcmania.service;

import al.pcmania.domain.Enums.DiscountType;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Enums.PromoState;
import al.pcmania.domain.Enums.PromoTheme;
import al.pcmania.domain.Product;
import al.pcmania.domain.Promotion;
import al.pcmania.domain.PromotionProduct;
import al.pcmania.repo.ProductRepository;
import al.pcmania.repo.PromotionRepository;
import al.pcmania.web.Fmt;
import al.pcmania.web.view.Deals;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionService {

    static final int MAX_PERCENT = 90;
    static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    public record Line(Long productId, Integer fixedPriceLek) {}

    public record PromotionForm(String name, String headline, DiscountType discountType, Integer discountValue,
                                LocalDateTime startsAt, LocalDateTime endsAt, PromoTheme theme, boolean showBanner,
                                boolean active, List<Line> items) {

        public Integer fixedPriceFor(Long productId) {
            return items.stream().filter(l -> l.productId().equals(productId)).map(Line::fixedPriceLek)
                    .filter(Objects::nonNull).findFirst().orElse(null);
        }

        public boolean includes(Long productId) {
            return items.stream().anyMatch(l -> l.productId().equals(productId));
        }
    }

    public record ItemView(Product product, Integer fixedPriceLek, int salePriceLek) {

        public int getPercent() {
            return Deals.percent(product.getPriceLek(), salePriceLek);
        }

        public boolean isBelowCost() {
            return product.getCostLek() > 0 && salePriceLek < product.getCostLek();
        }
    }

    public record Row(Promotion promotion, PromoState state, List<ItemView> items, int maxPercent, int progressPercent) {

        public String getWindowLabel() {
            return SHORT.format(promotion.getStartsAt()) + " → " + SHORT.format(promotion.getEndsAt());
        }
    }

    public record Saved(Promotion promotion, List<String> warnings) {}

    public record Stats(long live, long scheduled, int productsOnSale, int maxPercent) {}

    private final PromotionRepository promotions;
    private final ProductRepository products;
    private final ApplicationEventPublisher events;

    public List<Row> rows() {
        LocalDateTime now = LocalDateTime.now();
        return promotions.findAllWithItems().stream().map(p -> row(p, now))
                .sorted(Comparator.comparingInt((Row r) -> stateOrder(r.state()))
                        .thenComparing(r -> r.promotion().getStartsAt(), Comparator.reverseOrder()))
                .toList();
    }

    public Stats stats(List<Row> rows) {
        Set<Long> onSale = new HashSet<>();
        int max = 0;
        long live = 0, scheduled = 0;
        for (Row r : rows) {
            if (r.state() == PromoState.LIVE) {
                live++;
                r.items().forEach(i -> onSale.add(i.product().getId()));
                max = Math.max(max, r.maxPercent());
            }
            if (r.state() == PromoState.SCHEDULED) scheduled++;
        }
        return new Stats(live, scheduled, onSale.size(), max);
    }

    public Row row(Promotion p, LocalDateTime now) {
        List<ItemView> items = p.getItems().stream()
                .map(i -> new ItemView(i.getProduct(), i.getSalePriceLek(), p.salePriceFor(i.getProduct().getPriceLek(), i.getSalePriceLek())))
                .toList();
        int max = items.stream().mapToInt(ItemView::getPercent).max().orElse(0);
        return new Row(p, p.stateAt(now), items, max, progress(p, now));
    }

    static int progress(Promotion p, LocalDateTime now) {
        if (!now.isAfter(p.getStartsAt())) return 0;
        if (!now.isBefore(p.getEndsAt())) return 100;
        long total = Duration.between(p.getStartsAt(), p.getEndsAt()).toSeconds();
        long done = Duration.between(p.getStartsAt(), now).toSeconds();
        return total <= 0 ? 100 : (int) Math.min(100, done * 100 / total);
    }

    private static int stateOrder(PromoState s) {
        return switch (s) {
            case LIVE -> 0;
            case SCHEDULED -> 1;
            case PAUSED -> 2;
            case ENDED -> 3;
        };
    }

    public Promotion get(Long id) {
        return promotions.findWithItems(id).orElseThrow(NotFoundException::new);
    }

    public PromotionForm formOf(Promotion p) {
        List<Line> lines = p.getItems().stream().map(i -> new Line(i.getProduct().getId(), i.getSalePriceLek())).toList();
        return new PromotionForm(p.getName(), p.getHeadline(), p.getDiscountType(), p.getDiscountValue(), p.getStartsAt(),
                p.getEndsAt(), p.getTheme(), p.isShowBanner(), p.isActive(), lines);
    }

    public List<Product> pickableProducts(Promotion current) {
        List<Product> out = new ArrayList<>(products.findByStatusNotOrderByTitleAsc(ProductStatus.SOLD));
        if (current != null) {
            Set<Long> ids = new HashSet<>();
            out.forEach(p -> ids.add(p.getId()));
            current.getItems().stream().map(PromotionProduct::getProduct).filter(p -> ids.add(p.getId())).forEach(out::add);
        }
        out.sort(Comparator.comparing((Product p) -> p.getStatus() != ProductStatus.ACTIVE).thenComparing(Product::getTitle));
        return out;
    }

    @Transactional
    public Saved save(Long id, PromotionForm form) {
        Promotion p = id == null ? new Promotion() : get(id);
        if (!StringUtils.hasText(form.name())) throw new IllegalArgumentException("Shkruani emrin e ofertës.");
        if (form.discountType() == null) throw new IllegalArgumentException("Zgjidhni llojin e zbritjes.");
        int value = form.discountValue() == null ? 0 : form.discountValue();
        boolean everyLineFixed = !form.items().isEmpty() && form.items().stream().allMatch(l -> l.fixedPriceLek() != null);
        if (!everyLineFixed) {
            if (value <= 0) throw new IllegalArgumentException("Shkruani sa është zbritja.");
            if (form.discountType() == DiscountType.PERCENT && value > MAX_PERCENT) {
                throw new IllegalArgumentException("Zbritja në përqindje mund të jetë deri në " + MAX_PERCENT + "%.");
            }
        }
        if (form.startsAt() == null || form.endsAt() == null) throw new IllegalArgumentException("Zgjidhni kur fillon dhe kur mbaron oferta.");
        if (!form.endsAt().isAfter(form.startsAt())) throw new IllegalArgumentException("Oferta duhet të mbarojë pasi fillon.");
        if (id == null && !form.endsAt().isAfter(LocalDateTime.now())) throw new IllegalArgumentException("Data e mbarimit është në të kaluarën.");
        if (form.items().isEmpty()) throw new IllegalArgumentException("Zgjidhni të paktën një produkt.");

        Map<Long, Product> found = new HashMap<>();
        products.findAllById(form.items().stream().map(Line::productId).toList()).forEach(x -> found.put(x.getId(), x));
        if (found.size() != new HashSet<>(form.items().stream().map(Line::productId).toList()).size()) {
            throw new IllegalArgumentException("Një nga produktet nuk ekziston më.");
        }

        List<String> warnings = new ArrayList<>();
        for (Line l : form.items()) {
            Product prod = found.get(l.productId());
            int sale = Promotion.salePrice(form.discountType(), value, prod.getPriceLek(), l.fixedPriceLek());
            if (sale <= 0) throw new IllegalArgumentException("Me këtë zbritje " + prod.getTitle() + " del falas ose me çmim negativ.");
            if (sale >= prod.getPriceLek()) {
                throw new IllegalArgumentException("Çmimi i ofertës për " + prod.getTitle() + " (" + Fmt.lek(sale)
                        + ") duhet të jetë më i ulët se çmimi i zakonshëm (" + Fmt.lek(prod.getPriceLek()) + ").");
            }
            if (prod.getCostLek() > 0 && sale < prod.getCostLek()) {
                warnings.add(prod.getTitle() + " shitet " + Fmt.lek(prod.getCostLek() - sale) + " nën kosto.");
            }
        }

        p.setName(form.name().trim());
        p.setHeadline(StringUtils.hasText(form.headline()) ? form.headline().trim() : null);
        p.setDiscountType(form.discountType());
        p.setDiscountValue(Math.max(value, 0));
        p.setStartsAt(form.startsAt());
        p.setEndsAt(form.endsAt());
        p.setTheme(form.theme() == null ? PromoTheme.FIRE : form.theme());
        p.setShowBanner(form.showBanner());
        p.setActive(form.active());

        Map<Long, Line> wanted = new LinkedHashMap<>();
        form.items().forEach(l -> wanted.put(l.productId(), l));
        p.getItems().removeIf(i -> !wanted.containsKey(i.getProduct().getId()));
        for (Line l : wanted.values()) {
            Optional<PromotionProduct> existing = p.getItems().stream()
                    .filter(i -> i.getProduct().getId().equals(l.productId())).findFirst();
            if (existing.isPresent()) existing.get().setSalePriceLek(l.fixedPriceLek());
            else p.getItems().add(new PromotionProduct(p, found.get(l.productId()), l.fixedPriceLek()));
        }
        Promotion saved = promotions.save(p);
        events.publishEvent(new PricingService.PromotionsChanged());
        return new Saved(saved, warnings);
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        get(id).setActive(active);
        events.publishEvent(new PricingService.PromotionsChanged());
    }

    @Transactional
    public void endNow(Long id) {
        Promotion p = get(id);
        LocalDateTime now = LocalDateTime.now();
        if (!p.getEndsAt().isAfter(now)) return;
        p.setEndsAt(now);
        if (!p.getStartsAt().isBefore(now)) p.setStartsAt(now.minusSeconds(1));
        events.publishEvent(new PricingService.PromotionsChanged());
    }

    @Transactional
    public void delete(Long id) {
        promotions.delete(get(id));
        events.publishEvent(new PricingService.PromotionsChanged());
    }
}
