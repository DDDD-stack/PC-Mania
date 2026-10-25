package al.pcmania.service;

import al.pcmania.domain.*;
import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.repo.BrandRepository;
import al.pcmania.repo.CategoryRepository;
import al.pcmania.repo.GpuCatalogRepository;
import al.pcmania.repo.ComboRepository;
import al.pcmania.repo.OrderItemRepository;
import al.pcmania.repo.PrebuiltSpecRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.repo.ProductSlugHistoryRepository;
import al.pcmania.repo.SpecCatalogRepository;
import al.pcmania.web.Fmt;
import al.pcmania.web.admin.ProductForm;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class ProductAdminService {

    private static final Safelist DESCRIPTION_HTML = Safelist.basic().addTags("h3", "h4");

    private final ProductRepository products;
    private final BrandRepository brands;
    private final CategoryRepository categories;
    private final OrderItemRepository orderItems;
    private final GpuCatalogRepository gpuCatalog;
    private final ImageStorage images;
    private final GpuCatalogService catalog;
    private final FieldTemplateService templates;
    private final PrebuiltSpecRepository prebuilts;
    private final SpecCatalogRepository specCatalog;
    private final ComboRepository combos;
    private final ProductSlugHistoryRepository slugHistory;

    public static class InvalidProduct extends IllegalArgumentException {
        public final List<String> errors;

        public InvalidProduct(List<String> errors) {
            super(String.join(" ", errors));
            this.errors = List.copyOf(errors);
        }
    }

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

    private void autoLinkCatalogue(Product p) {
        if (p.getGpuModel() != null || !StringUtils.hasText(p.getTitle())) return;
        if (!SpecCatalogService.GPU.equals(templates.forCategory(p.getCategorySlug()).catalogKey())) return;
        catalog.resolve(p.getTitle()).ifPresent(match -> {
            p.setGpuModel(match);
            log.info("Linked \"{}\" to catalogue entry {}", p.getTitle(), match.getName());
        });
    }

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

    public Optional<PrebuiltSpec> prebuilt(Long productId) {
        return productId == null ? Optional.empty() : prebuilts.findByProductId(productId);
    }

    @Transactional
    public Product save(Long id, ProductForm form) {
        FieldTemplateService.Template template = templates.forCategory(form.getCategorySlug());
        boolean enforce = form.getStatus() != ProductStatus.DRAFT;
        FieldTemplateService.Cleaned cleaned = templates.clean(form.getCategorySlug(), form.getSpecKeys(), form.getSpecValues(), enforce);
        List<String> errors = new ArrayList<>(cleaned.errors());
        if (!Gtin.isValid(form.getGtin())) {
            errors.add("Barkodi nuk është i saktë: duhen 8, 12, 13 ose 14 shifra dhe shifra e fundit e kontrollit duhet të përputhet.");
        }
        ProductForm.PrebuiltForm pb = form.getPrebuilt() == null ? new ProductForm.PrebuiltForm() : form.getPrebuilt();
        if (template.prebuilt() && enforce) {
            if (!StringUtils.hasText(pb.getCpuName())) errors.add("Shkruani procesorin e PC-së.");
            if (pb.getRamGb() == null) errors.add("Shkruani sa GB RAM ka PC-ja.");
            if (!StringUtils.hasText(pb.getStoragePrimary())) errors.add("Shkruani diskun kryesor.");
        }
        if (!errors.isEmpty()) throw new InvalidProduct(errors);

        Product p = id == null ? new Product() : get(id);
        p.setTitle(form.getTitle().trim());
        p.setBrand(resolveBrand(form));
        p.setModel(trimToNull(form.getModel()));
        p.setGtin(Gtin.normalize(form.getGtin()));
        p.setCategorySlug(form.getCategorySlug());
        p.setCondition(form.getCondition());
        p.setPriceLek(form.getPriceLek());
        p.setCostLek(form.getCostLek());
        p.setQuantity(form.getQuantity());
        p.setShortDescription(trimToNull(form.getShortDescription()));
        p.setMetaDescription(trimToNull(form.getMetaDescription()));
        p.setFullDescription(sanitize(form.getFullDescription()));
        p.setWarrantyDays(form.getWarrantyDays());
        p.setTestNotes(trimToNull(form.getTestNotes()));
        p.setMiningFree(form.isMiningFree());
        p.setTransportIncluded(form.isTransportIncluded());
        p.setTradeEligible(form.isTradeEligible());
        p.setMaxTradeValueLek(form.isTradeEligible() ? form.getMaxTradeValueLek() : null);
        p.setGpuModel(form.getGpuModelId() == null ? null : gpuCatalog.findById(form.getGpuModelId()).orElse(null));
        if (id == null) autoLinkCatalogue(p);

        String wanted = StringUtils.hasText(form.getSlug()) ? form.getSlug() : (p.getSlug() != null ? p.getSlug() : Slugs.of(p.getTitle()));
        String previousSlug = p.getSlug();
        if (!wanted.equals(previousSlug)) p.setSlug(uniqueSlug(wanted));

        p.changeStatus(form.getStatus());
        if (form.getListedAt() != null) p.setListedAt(form.getListedAt().atTime(p.getListedAt() == null ? LocalTime.NOON : p.getListedAt().toLocalTime()));

        p.getSpecs().clear();
        int order = 0;
        for (FieldTemplateService.Spec spec : cleaned.specs()) p.getSpecs().add(new ProductSpec(p, spec.key(), spec.value(), ++order));
        Product saved = products.save(p);
        if (previousSlug != null && !previousSlug.equals(saved.getSlug())) rememberSlug(previousSlug, saved);
        if (template.prebuilt()) savePrebuilt(saved, pb);
        return saved;
    }

    private void rememberSlug(String oldSlug, Product p) {
        slugHistory.deleteByOldSlug(p.getSlug());
        slugHistory.findByOldSlug(oldSlug).ifPresentOrElse(h -> h.setProductId(p.getId()),
                () -> slugHistory.save(new ProductSlugHistory(oldSlug, p.getId())));
    }

    private void savePrebuilt(Product p, ProductForm.PrebuiltForm f) {
        PrebuiltSpec s = prebuilts.findByProductId(p.getId()).orElseGet(PrebuiltSpec::new);
        s.setProduct(p);
        s.setCpuName(trimToNull(f.getCpuName()));
        s.setCpuCores(f.getCpuCores());
        s.setCpuThreads(f.getCpuThreads());
        s.setGpuCatalog(f.getGpuCatalogId() == null ? null
                : specCatalog.findById(f.getGpuCatalogId()).filter(e -> SpecCatalogService.GPU.equals(e.getCategorySlug())).orElse(null));
        s.setGpuNameOverride(trimToNull(f.getGpuNameOverride()));
        s.setRamGb(f.getRamGb());
        s.setRamSpeedMhz(f.getRamSpeedMhz());
        s.setRamConfig(trimToNull(f.getRamConfig()));
        s.setStoragePrimary(trimToNull(f.getStoragePrimary()));
        s.setStorageSecondary(trimToNull(f.getStorageSecondary()));
        s.setPsuWatts(f.getPsuWatts());
        s.setPsuBrand(trimToNull(f.getPsuBrand()));
        s.setMotherboardModel(trimToNull(f.getMotherboardModel()));
        s.setCaseModel(trimToNull(f.getCaseModel()));
        s.setOsInstalled(f.isOsInstalled());
        s.setOsName(f.isOsInstalled() ? trimToNull(f.getOsName()) : null);
        s.setLoadTempCpuC(f.getLoadTempCpuC());
        s.setLoadTempGpuC(f.getLoadTempGpuC());
        s.setTestedAt(f.getTestedAt());
        s.setNotesSq(trimToNull(f.getNotesSq()));
        prebuilts.save(s);
    }

    @Transactional
    public Product duplicate(Long id) {
        Product src = get(id);
        Product p = new Product();
        p.setTitle(src.getTitle());
        p.setBrand(src.getBrand());
        p.setModel(src.getModel());
        p.setGtin(src.getGtin());
        p.setCategorySlug(src.getCategorySlug());
        p.setCondition(src.getCondition());
        p.setPriceLek(src.getPriceLek());
        p.setCostLek(src.getCostLek());
        p.setQuantity(1);
        p.setShortDescription(src.getShortDescription());
        p.setMetaDescription(src.getMetaDescription());
        p.setFullDescription(src.getFullDescription());
        p.setWarrantyDays(src.getWarrantyDays());
        p.setTestNotes(src.getTestNotes());
        p.setMiningFree(src.isMiningFree());
        p.setTransportIncluded(src.isTransportIncluded());
        p.setTradeEligible(src.isTradeEligible());
        p.setMaxTradeValueLek(src.getMaxTradeValueLek());
        p.setGpuModel(src.getGpuModel());
        p.setSlug(uniqueSlug(src.getSlug()));
        p.setStatus(ProductStatus.DRAFT);
        src.getSpecs().forEach(s -> p.getSpecs().add(new ProductSpec(p, s.getSpecKey(), s.getSpecValue(), s.getSortOrder())));
        src.getImages().forEach(i -> p.getImages().add(new ProductImage(p, images.copy(i.getFilename()), i.getSortOrder(), i.isPrimary())));
        Product saved = products.save(p);
        prebuilts.findByProductId(src.getId()).ifPresent(spec -> {
            PrebuiltSpec copy = new PrebuiltSpec();
            copy.setProduct(saved);
            copy.setCpuName(spec.getCpuName());
            copy.setCpuCores(spec.getCpuCores());
            copy.setCpuThreads(spec.getCpuThreads());
            copy.setGpuCatalog(spec.getGpuCatalog());
            copy.setGpuNameOverride(spec.getGpuNameOverride());
            copy.setRamGb(spec.getRamGb());
            copy.setRamSpeedMhz(spec.getRamSpeedMhz());
            copy.setRamConfig(spec.getRamConfig());
            copy.setStoragePrimary(spec.getStoragePrimary());
            copy.setStorageSecondary(spec.getStorageSecondary());
            copy.setPsuWatts(spec.getPsuWatts());
            copy.setPsuBrand(spec.getPsuBrand());
            copy.setMotherboardModel(spec.getMotherboardModel());
            copy.setCaseModel(spec.getCaseModel());
            copy.setOsInstalled(spec.isOsInstalled());
            copy.setOsName(spec.getOsName());
            copy.setNotesSq(spec.getNotesSq());
            prebuilts.save(copy);
        });
        return saved;
    }

    @Transactional
    public Product createDraftFromTradeIn(TradeRequest t, String fallbackCategory) {
        Product p = new Product();
        p.setTitle((t.getManufacturer() + " " + t.getModel()).trim());
        p.setBrand(brands.findByNameIgnoreCase(t.getManufacturer().trim()).orElse(null));
        p.setModel(t.getModel());
        String category = t.getItemType().categorySlug;
        p.setCategorySlug(categories.existsBySlug(category) || fallbackCategory == null ? category : fallbackCategory);
        p.setCondition(Condition.USED);
        p.setCostLek(t.getQuotedValueLek());
        p.setPriceLek(t.getQuotedValueLek());
        p.setQuantity(1);
        p.setStatus(ProductStatus.DRAFT);
        p.setTestNotes("Marrë me këmbim " + t.getRequestNumber() + " (vlera e dhënë: " + Fmt.lek(t.getQuotedValueLek()) + ").");
        p.setSlug(uniqueSlug(p.getTitle()));
        autoLinkCatalogue(p);
        return products.save(p);
    }

    @Transactional
    public int bulkStatus(Collection<Long> ids, ProductStatus status) {
        List<Product> list = products.findAllById(ids);
        list.forEach(p -> p.changeStatus(status));
        return list.size();
    }

    public Optional<String> deleteBlocker(Long id) {
        if (orderItems.existsByProductId(id)) {
            return Optional.of("Produkti ka porosi të lidhura dhe nuk mund të fshihet. Vendoseni statusin \"I fshehur\".");
        }
        List<Combo> in = combos.findContaining(id);
        if (!in.isEmpty()) {
            return Optional.of("Produkti është në paketën \"" + in.getFirst().getTitleSq() + "\". Hiqeni nga paketa ose vendoseni \"I fshehur\".");
        }
        return Optional.empty();
    }

    @Transactional
    public boolean delete(Long id) {
        if (deleteBlocker(id).isPresent()) return false;
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
