package al.pcmania.service;

import al.pcmania.domain.*;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.repo.*;
import al.pcmania.web.view.CatalogFilter;
import al.pcmania.web.view.PrebuiltView;
import al.pcmania.web.view.ProductCard;
import al.pcmania.web.view.ProductDetail;
import jakarta.persistence.criteria.JoinType;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    public static final int PAGE_SIZE = 24;
    private static final Set<ProductStatus> PUBLIC_DETAIL = EnumSet.of(ProductStatus.ACTIVE, ProductStatus.RESERVED, ProductStatus.SOLD);
    public static final Set<ProductStatus> LISTED = EnumSet.of(ProductStatus.ACTIVE, ProductStatus.RESERVED);

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ProductSpecRepository specs;
    private final CategoryRepository categories;
    private final PrebuiltSpecRepository prebuilts;
    private final FieldTemplateService templates;

    public record CategoryTile(Category category, long count) {

        public boolean isEmpty() {
            return category.isOutOfStock() || count == 0;
        }
    }

    public record Option(String value, String label) {}

    public record FacetGroup(String key, String label, List<Option> options) {}

    public record Facets(List<Brand> brands, List<FacetGroup> specs, List<String> gpus, List<Integer> rams) {}

    public List<Category> categories() {
        return categories.findByVisibleTrueOrderBySortOrderAsc();
    }

    public List<CategoryTile> categoryTiles() {
        Map<String, Long> counts = products.countByCategory(ProductStatus.ACTIVE).stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));
        return categories().stream().map(c -> new CategoryTile(c, counts.getOrDefault(c.getSlug(), 0L))).toList();
    }

    public Optional<Category> category(String slug) {
        return categories().stream().filter(c -> c.getSlug().equals(slug)).findFirst();
    }

    public List<ProductCard> newest(int limit) {
        return cards(products.findByStatusInOrderByStatusAscListedAtDesc(LISTED, PageRequest.of(0, limit)));
    }

    public Page<ProductCard> list(String categorySlug, CatalogFilter f) {
        Sort sort = switch (f.sort()) {
            case CMIMI_ASC -> Sort.by("priceLek").ascending();
            case CMIMI_DESC -> Sort.by("priceLek").descending();
            case TE_REJAT -> Sort.by("listedAt").descending();
        };
        Sort availableFirst = Sort.by("status").ascending().and(sort).and(Sort.by("id").descending());
        Page<Product> page = products.findAll(spec(categorySlug, f), PageRequest.of(f.page() - 1, PAGE_SIZE, availableFirst));
        List<ProductCard> cards = cards(page.getContent());
        return new PageImpl<>(cards, page.getPageable(), page.getTotalElements());
    }

    public Page<ProductCard> search(String query, int page) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) return Page.empty(PageRequest.of(0, PAGE_SIZE));
        Page<Product> found = products.findAll(searchSpec(q),
                PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, Sort.by("status").ascending()
                        .and(Sort.by("listedAt").descending()).and(Sort.by("id").descending())));
        return new PageImpl<>(cards(found.getContent()), found.getPageable(), found.getTotalElements());
    }

    private static Specification<Product> searchSpec(String q) {
        List<String> terms = Arrays.stream(q.toLowerCase(Locale.ROOT).split("\s+")).filter(t -> !t.isBlank()).limit(6).toList();
        return (root, query, cb) -> {
            query.distinct(true);
            var brand = root.join("brand", JoinType.LEFT);
            var gpu = root.join("gpuModel", JoinType.LEFT);
            List<Predicate> all = new ArrayList<>();
            all.add(root.get("status").in(LISTED));
            for (String term : terms) {
                String like = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                all.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like, '!'),
                        cb.like(cb.lower(cb.coalesce(root.get("model"), "")), like, '!'),
                        cb.like(cb.lower(cb.coalesce(root.get("shortDescription"), "")), like, '!'),
                        cb.like(cb.lower(cb.coalesce(brand.get("name"), "")), like, '!'),
                        cb.like(cb.lower(cb.coalesce(gpu.get("name"), "")), like, '!'),
                        cb.like(cb.lower(cb.coalesce(gpu.get("aliases"), "")), like, '!'),
                        cb.like(cb.lower(root.get("categorySlug")), like, '!')));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    public Facets facets(String categorySlug) {
        FieldTemplateService.Template template = templates.forCategory(categorySlug);
        List<FieldTemplateService.Field> filterFields = template.fields().stream().filter(FieldTemplateService.Field::showInFilters).toList();
        List<FacetGroup> groups = new ArrayList<>();
        if (!filterFields.isEmpty()) {
            Map<String, TreeSet<String>> values = new HashMap<>();
            for (Object[] r : specs.facets(categorySlug, LISTED, filterFields.stream().map(FieldTemplateService.Field::key).toList())) {
                values.computeIfAbsent((String) r[0], k -> new TreeSet<>(NATURAL)).add((String) r[1]);
            }
            for (FieldTemplateService.Field f : filterFields) {
                TreeSet<String> vs = values.get(f.key());
                if (vs == null || vs.isEmpty()) continue;
                groups.add(new FacetGroup(f.key(), f.labelSq(), vs.stream().map(v -> new Option(v, f.display(v))).toList()));
            }
        }
        List<String> gpus = List.of();
        List<Integer> rams = List.of();
        if (template.prebuilt()) {
            gpus = prebuilts.gpuFacets(categorySlug, LISTED).stream().map(r -> (String) r[0]).filter(Objects::nonNull)
                    .sorted(NATURAL).toList();
            rams = prebuilts.ramFacets(categorySlug, LISTED);
        }
        return new Facets(products.brandsInCategory(categorySlug, LISTED), groups, gpus, rams);
    }

    public Optional<ProductDetail> detail(String slug) {
        return products.findBySlug(slug).filter(p -> PUBLIC_DETAIL.contains(p.getStatus())).map(this::toDetail);
    }

    private ProductDetail toDetail(Product p) {
        FieldTemplateService.Template template = templates.forCategory(p.getCategorySlug());
        PrebuiltView prebuilt = template.prebuilt()
                ? prebuilts.findByProductId(p.getId()).map(PrebuiltView::of).orElse(null) : null;
        return ProductDetail.of(p, template, prebuilt);
    }

    public List<ProductCard> related(ProductDetail p, int limit) {
        return cards(products.findByCategorySlugAndStatusAndIdNotOrderByListedAtDesc(
                p.categorySlug(), ProductStatus.ACTIVE, p.id(), PageRequest.of(0, limit)));
    }

    public List<ProductCard> tradeable() {
        return cards(products.findByTradeEligibleTrueAndStatusAndQuantityGreaterThanOrderByListedAtDesc(ProductStatus.ACTIVE, 0));
    }

    public List<ProductCard> cardsBySlugs(Collection<String> slugs) {
        return slugs.isEmpty() ? List.of() : cards(products.findBySlugIn(slugs));
    }

    public List<Product> sitemapProducts() {
        return products.findByStatusIn(LISTED);
    }

    @Transactional
    public void countView(Long productId) {
        products.incrementViews(productId);
    }

    private static Specification<Product> spec(String categorySlug, CatalogFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(root.get("status").in(LISTED));
            p.add(cb.equal(root.get("categorySlug"), categorySlug));
            if (f.min() != null) p.add(cb.ge(root.get("priceLek"), f.min()));
            if (f.max() != null) p.add(cb.le(root.get("priceLek"), f.max()));
            if (f.band() != null && f.band().min != null) p.add(cb.ge(root.get("priceLek"), f.band().min));
            if (f.band() != null && f.band().max != null) p.add(cb.lt(root.get("priceLek"), f.band().max));
            if (!f.conditions().isEmpty()) p.add(root.get("condition").in(f.conditions()));
            if (f.trade()) p.add(cb.isTrue(root.get("tradeEligible")));
            if (!f.brands().isEmpty()) p.add(root.join("brand").get("slug").in(f.brands()));

            f.specs().forEach((key, vals) -> {
                Subquery<Long> sq = query.subquery(Long.class);
                var s = sq.from(ProductSpec.class);
                sq.select(s.get("id")).where(
                        cb.equal(s.get("product"), root),
                        cb.equal(s.get("specKey"), key),
                        s.get("specValue").in(vals));
                p.add(cb.exists(sq));
            });
            if (!f.gpus().isEmpty()) {
                Subquery<Long> sq = query.subquery(Long.class);
                var s = sq.from(PrebuiltSpec.class);
                var g = s.join("gpuCatalog", JoinType.LEFT);
                var name = cb.coalesce(cb.nullif(cb.trim(s.get("gpuNameOverride")), ""), g.<String>get("name"));
                sq.select(s.get("id")).where(cb.equal(s.get("product"), root), name.in(f.gpus()));
                p.add(cb.exists(sq));
            }
            if (!f.rams().isEmpty()) {
                Subquery<Long> sq = query.subquery(Long.class);
                var s = sq.from(PrebuiltSpec.class);
                sq.select(s.get("id")).where(cb.equal(s.get("product"), root), s.get("ramGb").in(f.rams()));
                p.add(cb.exists(sq));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    public List<ProductCard> cards(List<Product> list) {
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(Product::getId).toList();
        Map<Long, String> primary = images.findByProductIdInOrderBySortOrderAsc(ids)
                .stream().collect(Collectors.toMap(i -> i.getProduct().getId(), ProductImage::getFilename, (a, b) -> a));
        Map<Long, String> lines = specLines(list);
        return list.stream().map(p -> ProductCard.of(p, primary.get(p.getId()), lines.get(p.getId()))).toList();
    }

    private Map<Long, String> specLines(List<Product> list) {
        Map<String, FieldTemplateService.Template> bySlug = new HashMap<>();
        Set<String> keys = new HashSet<>();
        List<Long> prebuiltIds = new ArrayList<>();
        List<Long> otherIds = new ArrayList<>();
        for (Product p : list) {
            FieldTemplateService.Template t = bySlug.computeIfAbsent(p.getCategorySlug(), templates::forCategory);
            if (t.prebuilt()) {
                prebuiltIds.add(p.getId());
                continue;
            }
            List<String> listing = t.fields().stream().filter(FieldTemplateService.Field::showInListing).map(FieldTemplateService.Field::key).toList();
            if (!listing.isEmpty()) {
                keys.addAll(listing);
                otherIds.add(p.getId());
            }
        }
        Map<Long, String> out = new HashMap<>();
        if (!prebuiltIds.isEmpty()) {
            for (PrebuiltSpec s : prebuilts.findByProductIds(prebuiltIds)) {
                String line = PrebuiltView.of(s).specLine();
                if (line != null) out.put(s.getProduct().getId(), line);
            }
        }
        if (!otherIds.isEmpty()) {
            Map<Long, Map<String, String>> values = new HashMap<>();
            for (Object[] r : specs.valuesFor(otherIds, keys)) {
                values.computeIfAbsent((Long) r[0], k -> new HashMap<>()).put((String) r[1], (String) r[2]);
            }
            for (Product p : list) {
                Map<String, String> v = values.get(p.getId());
                if (v == null) continue;
                FieldTemplateService.Template t = bySlug.get(p.getCategorySlug());
                String line = t.fields().stream().filter(FieldTemplateService.Field::showInListing)
                        .filter(f -> v.containsKey(f.key())).map(f -> f.display(v.get(f.key())))
                        .collect(Collectors.joining(" · "));
                if (!line.isEmpty()) out.put(p.getId(), line);
            }
        }
        return out;
    }

    static final Comparator<String> NATURAL = Comparator
            .comparing((Function<String, Double>) CatalogService::leadingNumber, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(String.CASE_INSENSITIVE_ORDER);

    private static Double leadingNumber(String s) {
        var m = java.util.regex.Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)").matcher(s);
        return m.find() ? Double.valueOf(m.group(1).replace(',', '.')) : null;
    }
}
