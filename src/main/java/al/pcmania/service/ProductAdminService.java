package al.pcmania.service;

import al.pcmania.domain.*;
import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.repo.BrandRepository;
import al.pcmania.repo.OrderItemRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.web.admin.ProductForm;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductAdminService {

    private static final Safelist DESCRIPTION_HTML = Safelist.basic().addTags("h3", "h4");

    private final ProductRepository products;
    private final BrandRepository brands;
    private final OrderItemRepository orderItems;
    private final ImageStorage images;

    public Product get(Long id) {
        return products.findById(id).orElseThrow(NotFoundException::new);
    }

    public Page<Product> search(String q, ProductStatus status, String category, Pageable pageable) {
        return search(q, status == null ? null : List.of(status), category, pageable);
    }

    public Page<Product> search(String q, Collection<ProductStatus> statuses, String category, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (StringUtils.hasText(q)) p.add(cb.like(cb.lower(root.get("title")), "%" + q.toLowerCase() + "%"));
            if (statuses != null && !statuses.isEmpty()) p.add(root.get("status").in(statuses));
            if (StringUtils.hasText(category)) p.add(cb.equal(root.get("categorySlug"), category));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return products.findAll(spec, pageable);
    }

    /**
     * Partial update from the mobile app: any null argument is left unchanged. The slug is deliberately
     * left alone even when the title changes, so links already shared on Facebook keep working.
     */
    @Transactional
    public Product quickUpdate(Long id, ProductStatus status, Integer quantity, Integer priceLek, Integer costLek,
                               String title, Condition condition, String shortDescription) {
        Product p = get(id);
        if (quantity != null) {
            if (quantity < 0) throw new IllegalArgumentException("Sasia nuk mund të jetë negative.");
            p.setQuantity(quantity);
        }
        if (priceLek != null) {
            if (priceLek < 0) throw new IllegalArgumentException("Çmimi nuk mund të jetë negativ.");
            p.setPriceLek(priceLek);
        }
        if (costLek != null) {
            if (costLek < 0) throw new IllegalArgumentException("Kostoja nuk mund të jetë negative.");
            p.setCostLek(costLek);
        }
        if (title != null) {
            String clean = title.trim();
            if (clean.length() < 3) throw new IllegalArgumentException("Titulli është shumë i shkurtër.");
            if (clean.length() > 200) throw new IllegalArgumentException("Titulli është shumë i gjatë.");
            p.setTitle(clean);
        }
        if (condition != null) p.setCondition(condition);
        if (shortDescription != null) p.setShortDescription(trimToNull(shortDescription));
        if (status != null) p.changeStatus(status);
        return p;
    }

    @Transactional
    public Product save(Long id, ProductForm form) {
        Product p = id == null ? new Product() : get(id);
        p.setTitle(form.getTitle().trim());
        p.setBrand(resolveBrand(form));
        p.setModel(trimToNull(form.getModel()));
        p.setCategorySlug(form.getCategorySlug());
        p.setCondition(form.getCondition());
        p.setPriceLek(form.getPriceLek());
        p.setCostLek(form.getCostLek());
        p.setQuantity(form.getQuantity());
        p.setShortDescription(trimToNull(form.getShortDescription()));
        p.setFullDescription(sanitize(form.getFullDescription()));
        p.setWarrantyDays(form.getWarrantyDays());
        p.setTestNotes(trimToNull(form.getTestNotes()));
        p.setMiningFree(form.isMiningFree());
        p.setTransportIncluded(form.isTransportIncluded());

        // Slugs are only generated once: changing them later breaks links already shared on Facebook.
        String wanted = StringUtils.hasText(form.getSlug()) ? form.getSlug() : (p.getSlug() != null ? p.getSlug() : Slugs.of(p.getTitle()));
        if (!wanted.equals(p.getSlug())) p.setSlug(uniqueSlug(wanted));

        p.changeStatus(form.getStatus());
        if (form.getListedAt() != null) p.setListedAt(form.getListedAt().atTime(p.getListedAt() == null ? LocalTime.NOON : p.getListedAt().toLocalTime()));

        p.getSpecs().clear();
        int order = 0;
        for (int i = 0; i < form.getSpecKeys().size(); i++) {
            String key = trimToNull(form.getSpecKeys().get(i));
            String value = i < form.getSpecValues().size() ? trimToNull(form.getSpecValues().get(i)) : null;
            if (key != null && value != null) p.getSpecs().add(new ProductSpec(p, key, value, ++order));
        }
        return products.save(p);
    }

    @Transactional
    public Product duplicate(Long id) {
        Product src = get(id);
        Product p = new Product();
        p.setTitle(src.getTitle());
        p.setBrand(src.getBrand());
        p.setModel(src.getModel());
        p.setCategorySlug(src.getCategorySlug());
        p.setCondition(src.getCondition());
        p.setPriceLek(src.getPriceLek());
        p.setCostLek(src.getCostLek());
        p.setQuantity(1);
        p.setShortDescription(src.getShortDescription());
        p.setFullDescription(src.getFullDescription());
        p.setWarrantyDays(src.getWarrantyDays());
        p.setTestNotes(src.getTestNotes());
        p.setMiningFree(src.isMiningFree());
        p.setTransportIncluded(src.isTransportIncluded());
        p.setSlug(uniqueSlug(src.getSlug()));
        p.setStatus(ProductStatus.DRAFT);
        src.getSpecs().forEach(s -> p.getSpecs().add(new ProductSpec(p, s.getSpecKey(), s.getSpecValue(), s.getSortOrder())));
        src.getImages().forEach(i -> p.getImages().add(new ProductImage(p, images.copy(i.getFilename()), i.getSortOrder(), i.isPrimary())));
        return products.save(p);
    }

    @Transactional
    public int bulkStatus(Collection<Long> ids, ProductStatus status) {
        List<Product> list = products.findAllById(ids);
        list.forEach(p -> p.changeStatus(status));
        return list.size();
    }

    /** Returns false if the product is referenced by orders (hide it instead, to keep reporting intact). */
    @Transactional
    public boolean delete(Long id) {
        if (orderItems.existsByProductId(id)) return false;
        Product p = get(id);
        p.getImages().forEach(i -> images.delete(i.getFilename()));
        products.delete(p);
        return true;
    }

    @Transactional
    public void addImages(Long id, List<MultipartFile> files) {
        Product p = get(id);
        for (MultipartFile f : files) {
            if (f.isEmpty()) continue;
            p.getImages().add(new ProductImage(p, images.store(f), p.getImages().size() + 1, false));
        }
        syncPrimary(p);
    }

    /** Reorders images to match the given ids; the first image becomes the primary one. */
    @Transactional
    public void reorderImages(Long id, List<Long> imageIds) {
        Product p = get(id);
        Map<Long, Integer> pos = new HashMap<>();
        for (int i = 0; i < imageIds.size(); i++) pos.put(imageIds.get(i), i + 1);
        p.getImages().forEach(img -> img.setSortOrder(pos.getOrDefault(img.getId(), 1000 + img.getSortOrder())));
        p.getImages().sort(Comparator.comparingInt(ProductImage::getSortOrder));
        syncPrimary(p);
    }

    @Transactional
    public void deleteImage(Long id, Long imageId) {
        Product p = get(id);
        p.getImages().removeIf(img -> {
            if (!img.getId().equals(imageId)) return false;
            images.delete(img.getFilename());
            return true;
        });
        syncPrimary(p);
    }

    private void syncPrimary(Product p) {
        for (int i = 0; i < p.getImages().size(); i++) {
            p.getImages().get(i).setSortOrder(i + 1);
            p.getImages().get(i).setPrimary(i == 0);
        }
    }

    private Brand resolveBrand(ProductForm form) {
        if (StringUtils.hasText(form.getNewBrand())) {
            String name = form.getNewBrand().trim();
            return brands.findByNameIgnoreCase(name).orElseGet(() -> {
                Brand b = new Brand();
                b.setName(name);
                b.setSlug(Slugs.of(name));
                return brands.save(b);
            });
        }
        return form.getBrandId() == null ? null : brands.findById(form.getBrandId()).orElse(null);
    }

    private String uniqueSlug(String base) {
        String slug = Slugs.of(base);
        if (!products.existsBySlug(slug)) return slug;
        for (int i = 2; ; i++) {
            if (!products.existsBySlug(slug + "-" + i)) return slug + "-" + i;
        }
    }

    private static String sanitize(String html) {
        return StringUtils.hasText(html) ? Jsoup.clean(html, DESCRIPTION_HTML) : null;
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
