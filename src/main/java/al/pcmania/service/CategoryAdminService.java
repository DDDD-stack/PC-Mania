package al.pcmania.service;

import al.pcmania.config.CacheConfig;
import al.pcmania.domain.Category;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.repo.CategoryRepository;
import al.pcmania.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryAdminService {

    private static final Safelist INTRO_HTML = Safelist.none()
            .addTags("p", "strong", "em", "ul", "ol", "li", "a")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto", "tel")
            .preserveRelativeLinks(true);

    private final CategoryRepository categories;
    private final ProductRepository products;

    public List<Category> all() {
        return categories.findAllByOrderBySortOrderAsc();
    }

    public Map<String, long[]> counts() {
        Map<String, Long> active = products.countByCategory(ProductStatus.ACTIVE).stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));
        Map<String, Long> total = products.countByCategoryAllStatuses().stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));
        return total.keySet().stream().collect(Collectors.toMap(slug -> slug,
                slug -> new long[]{total.getOrDefault(slug, 0L), active.getOrDefault(slug, 0L)}));
    }

    @CacheEvict(cacheNames = {CacheConfig.CATEGORIES, CacheConfig.FIELD_TEMPLATES}, allEntries = true)
    @Transactional
    public Category create(String nameSq, String slug, String iconClass, Integer sortOrder) {
        Category c = new Category();
        c.setNameSq(nameSq.trim());
        c.setSlug(uniqueSlug(StringUtils.hasText(slug) ? slug : nameSq));
        c.setIconClass(StringUtils.hasText(iconClass) ? iconClass.trim() : "bi-box-seam");
        c.setSortOrder(sortOrder != null ? sortOrder : nextSortOrder());
        c.setVisible(true);
        return categories.save(c);
    }

    @CacheEvict(cacheNames = {CacheConfig.CATEGORIES, CacheConfig.FIELD_TEMPLATES}, allEntries = true)
    @Transactional
    public void update(Long id, String nameSq, String iconClass, Integer sortOrder) {
        Category c = get(id);
        c.setNameSq(nameSq.trim());
        c.setIconClass(StringUtils.hasText(iconClass) ? iconClass.trim() : null);
        if (sortOrder != null) c.setSortOrder(sortOrder);
    }

    @CacheEvict(cacheNames = {CacheConfig.CATEGORIES, CacheConfig.FIELD_TEMPLATES}, allEntries = true)
    @Transactional
    public Category updateSeo(Long id, String metaTitle, String metaDescription, String introHtml) {
        Category c = get(id);
        c.setMetaTitle(StringUtils.hasText(metaTitle) ? metaTitle.trim() : null);
        c.setMetaDescription(StringUtils.hasText(metaDescription) ? metaDescription.trim() : null);
        c.setIntroHtml(sanitizeIntro(introHtml));
        return c;
    }

    public static String sanitizeIntro(String html) {
        if (!StringUtils.hasText(html)) return null;
        String clean = Jsoup.clean(html, "http://intro.invalid", INTRO_HTML).trim();
        return Jsoup.parse(clean).text().isBlank() ? null : clean;
    }

    @CacheEvict(cacheNames = {CacheConfig.CATEGORIES, CacheConfig.FIELD_TEMPLATES}, allEntries = true)
    @Transactional
    public Category setVisible(Long id, boolean visible) {
        Category c = get(id);
        c.setVisible(visible);
        return c;
    }

    @CacheEvict(cacheNames = {CacheConfig.CATEGORIES, CacheConfig.FIELD_TEMPLATES}, allEntries = true)
    @Transactional
    public Category setOutOfStock(Long id, boolean outOfStock) {
        Category c = get(id);
        c.setOutOfStock(outOfStock);
        return c;
    }

    @CacheEvict(cacheNames = {CacheConfig.CATEGORIES, CacheConfig.FIELD_TEMPLATES}, allEntries = true)
    @Transactional
    public boolean delete(Long id) {
        Category c = get(id);
        if (products.countByCategorySlug(c.getSlug()) > 0) return false;
        categories.delete(c);
        return true;
    }

    public Category get(Long id) {
        return categories.findById(id).orElseThrow(NotFoundException::new);
    }

    private int nextSortOrder() {
        return categories.findAll().stream().mapToInt(Category::getSortOrder).max().orElse(0) + 1;
    }

    private String uniqueSlug(String base) {
        String slug = Slugs.of(base);
        if (!categories.existsBySlug(slug)) return slug;
        for (int i = 2; ; i++) {
            if (!categories.existsBySlug(slug + "-" + i)) return slug + "-" + i;
        }
    }
}
