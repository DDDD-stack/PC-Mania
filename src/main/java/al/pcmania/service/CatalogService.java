package al.pcmania.service;

import al.pcmania.domain.*;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.repo.*;
import al.pcmania.web.view.CatalogFilter;
import al.pcmania.web.view.ProductCard;
import al.pcmania.web.view.ProductDetail;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read side of the public shop. Returns only cost-free view objects. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    public static final int PAGE_SIZE = 24;
    private static final Set<ProductStatus> PUBLIC_DETAIL = EnumSet.of(ProductStatus.ACTIVE, ProductStatus.RESERVED, ProductStatus.SOLD);

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ProductSpecRepository specs;
    private final CategoryRepository categories;

    public record CategoryTile(Category category, long count) {
        /** Flagged by the operator, or simply nothing active to show. */
        public boolean isEmpty() {
            return category.isOutOfStock() || count == 0;
        }
    }

    public record Facets(List<Brand> brands, Map<String, List<String>> specs) {}

    /** Categories shown publicly (navigation, home, sitemap). */
    public List<Category> categories() {
        return categories.findByVisibleTrueOrderBySortOrderAsc();
    }

    public List<CategoryTile> categoryTiles() {
        Map<String, Long> counts = products.countByCategory(ProductStatus.ACTIVE).stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));
        return categories().stream().map(c -> new CategoryTile(c, counts.getOrDefault(c.getSlug(), 0L))).toList();
    }

    /** Public lookup: hidden categories are treated as non-existent. Served from the cached list, not a query. */
    public Optional<Category> category(String slug) {
        return categories().stream().filter(c -> c.getSlug().equals(slug)).findFirst();
    }

    public List<ProductCard> newest(int limit) {
        return cards(products.findByStatusOrderByListedAtDesc(ProductStatus.ACTIVE, PageRequest.of(0, limit)));
    }

    public Page<ProductCard> list(String categorySlug, CatalogFilter f) {
        Sort sort = switch (f.sort()) {
            case CMIMI_ASC -> Sort.by("priceLek").ascending();
            case CMIMI_DESC -> Sort.by("priceLek").descending();
            case TE_REJAT -> Sort.by("listedAt").descending();
        };
        Page<Product> page = products.findAll(spec(categorySlug, f), PageRequest.of(f.page() - 1, PAGE_SIZE, sort.and(Sort.by("id").descending())));
        List<ProductCard> cards = cards(page.getContent());
        return new PageImpl<>(cards, page.getPageable(), page.getTotalElements());
    }

    public Facets facets(String categorySlug) {
        List<Object[]> rows = specs.facets(categorySlug, ProductStatus.ACTIVE);
        Map<String, Integer> keyOrder = new HashMap<>();
        Map<String, TreeSet<String>> values = new HashMap<>();
        for (Object[] r : rows) {
            String key = (String) r[0];
            keyOrder.merge(key, ((Number) r[2]).intValue(), Math::min);
            values.computeIfAbsent(key, k -> new TreeSet<>(NATURAL)).add((String) r[1]);
        }
        Map<String, List<String>> ordered = new LinkedHashMap<>();
        keyOrder.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> ordered.put(e.getKey(), List.copyOf(values.get(e.getKey()))));
        return new Facets(products.brandsInCategory(categorySlug, ProductStatus.ACTIVE), ordered);
    }

    public Optional<ProductDetail> detail(String slug) {
        return products.findBySlug(slug).filter(p -> PUBLIC_DETAIL.contains(p.getStatus())).map(ProductDetail::of);
    }

    public List<ProductCard> related(ProductDetail p, int limit) {
        return cards(products.findByCategorySlugAndStatusAndIdNotOrderByListedAtDesc(
                p.categorySlug(), ProductStatus.ACTIVE, p.id(), PageRequest.of(0, limit)));
    }

    /** What can be asked about on the "Nderro" page: offered for trade and in stock. */
    public List<ProductCard> tradeable() {
        return cards(products.findByTradeEligibleTrueAndStatusAndQuantityGreaterThanOrderByListedAtDesc(ProductStatus.ACTIVE, 0));
    }

    /** Cards for the given slugs, whatever their status: the assistant shows what it talked about. */
    public List<ProductCard> cardsBySlugs(Collection<String> slugs) {
        return slugs.isEmpty() ? List.of() : cards(products.findBySlugIn(slugs));
    }

    public List<Product> sitemapProducts() {
        return products.findByStatusIn(List.of(ProductStatus.ACTIVE, ProductStatus.RESERVED));
    }

    @Transactional
    public void countView(Long productId) {
        products.incrementViews(productId);
    }

    private static Specification<Product> spec(String categorySlug, CatalogFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
            p.add(cb.equal(root.get("categorySlug"), categorySlug));
            if (f.min() != null) p.add(cb.ge(root.get("priceLek"), f.min()));
            if (f.max() != null) p.add(cb.le(root.get("priceLek"), f.max()));
            if (!f.conditions().isEmpty()) p.add(root.get("condition").in(f.conditions()));
            if (f.trade()) p.add(cb.isTrue(root.get("tradeEligible")));
            if (!f.brands().isEmpty()) p.add(root.join("brand").get("slug").in(f.brands()));
            // One EXISTS per spec key: values within a key are OR-ed, different keys are AND-ed.
            f.specs().forEach((key, vals) -> {
                Subquery<Long> sq = query.subquery(Long.class);
                var s = sq.from(ProductSpec.class);
                sq.select(s.get("id")).where(
                        cb.equal(s.get("product"), root),
                        cb.equal(s.get("specKey"), key),
                        s.get("specValue").in(vals));
                p.add(cb.exists(sq));
            });
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private List<ProductCard> cards(List<Product> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, String> primary = images.findByProductIdInOrderBySortOrderAsc(list.stream().map(Product::getId).toList())
                .stream().collect(Collectors.toMap(i -> i.getProduct().getId(), ProductImage::getFilename, (a, b) -> a));
        return list.stream().map(p -> ProductCard.of(p, primary.get(p.getId()))).toList();
    }

    /** Orders "8 GB" before "12 GB" by comparing leading numbers numerically. */
    static final Comparator<String> NATURAL = Comparator
            .comparing((Function<String, Double>) CatalogService::leadingNumber, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(String.CASE_INSENSITIVE_ORDER);

    private static Double leadingNumber(String s) {
        var m = java.util.regex.Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)").matcher(s);
        return m.find() ? Double.valueOf(m.group(1).replace(',', '.')) : null;
    }
}
