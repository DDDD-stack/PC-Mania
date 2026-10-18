package al.pcmania.service;

import al.pcmania.config.CacheConfig;
import al.pcmania.domain.Enums.UpscalerVersion;
import al.pcmania.domain.GpuCatalog;
import al.pcmania.domain.Product;
import al.pcmania.domain.ProductSpec;
import al.pcmania.domain.SpecCatalogEntry;
import al.pcmania.repo.GpuCatalogRepository;
import al.pcmania.repo.SpecCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpecCatalogService {

    public static final String GPU = "gpu";

    public record Match(SpecCatalogEntry entry, int score) {}

    public record EntryForm(String categorySlug, String name, String aliases, boolean verified, Map<String, String> values) {}

    private final SpecCatalogRepository repo;
    private final GpuCatalogRepository gpus;
    private final FieldTemplateService templates;

    @Lazy
    @Autowired
    private SpecCatalogService self;

    @Cacheable(CacheConfig.SPEC_CATALOG)
    @Transactional(readOnly = true)
    public List<SpecCatalogEntry> inCategory(String categorySlug) {
        return List.copyOf(repo.findByCategorySlugOrderByNameAsc(categorySlug));
    }

    public List<SpecCatalogEntry> all() {
        return repo.findAllByOrderByCategorySlugAscNameAsc();
    }

    public Map<String, Long> counts() {
        Map<String, Long> out = new TreeMap<>();
        for (Object[] r : repo.countByCategory()) out.put((String) r[0], (Long) r[1]);
        return out;
    }

    public SpecCatalogEntry get(Long id) {
        return repo.findById(id).orElseThrow(NotFoundException::new);
    }

    public Optional<SpecCatalogEntry> find(Long id) {
        return id == null ? Optional.empty() : repo.findById(id);
    }

    public List<Match> search(String categorySlug, String query, int limit) {
        String q = Fuzzy.normalize(query);
        if (q.length() < 2 || !StringUtils.hasText(categorySlug)) return List.of();
        List<Match> hits = new ArrayList<>();
        for (SpecCatalogEntry e : self.inCategory(categorySlug.trim())) {
            List<String> names = new ArrayList<>();
            names.add(e.getName());
            names.addAll(e.aliasList());
            int score = Fuzzy.score(names, q);
            if (score > 0) hits.add(new Match(e, score));
        }
        hits.sort(Comparator.comparingInt(Match::score).reversed().thenComparing(m -> m.entry().getName()));
        return hits.size() > limit ? hits.subList(0, limit) : hits;
    }

    public static Map<String, String> values(SpecCatalogEntry e) {
        Map<String, String> out = new LinkedHashMap<>();
        if (e.getSpecJson() == null) return out;
        e.getSpecJson().forEach((k, v) -> {
            if (v != null && !String.valueOf(v).isBlank()) out.put(k, String.valueOf(v));
        });
        return out;
    }

    @CacheEvict(cacheNames = CacheConfig.SPEC_CATALOG, allEntries = true)
    @Transactional
    public SpecCatalogEntry save(Long id, EntryForm form) {
        SpecCatalogEntry e = id == null ? new SpecCatalogEntry() : get(id);
        if (e.getGpuCatalogId() != null) {
            throw new IllegalArgumentException("Ky rresht vjen nga Katalogu GPU dhe ndryshohet atje, që asistenti dhe forma të shohin të njëjtat të dhëna.");
        }
        if (!StringUtils.hasText(form.name())) throw new IllegalArgumentException("Shkruani emrin e modelit.");
        if (!StringUtils.hasText(form.categorySlug())) throw new IllegalArgumentException("Zgjidhni kategorinë.");
        String category = form.categorySlug().trim();
        e.setCategorySlug(category);
        e.setName(form.name().trim());
        e.setAliases(StringUtils.hasText(form.aliases()) ? form.aliases().trim() : null);
        e.setVerified(form.verified());
        e.setSpecJson(cleanValues(category, form.values()));
        return repo.save(e);
    }

    @CacheEvict(cacheNames = CacheConfig.SPEC_CATALOG, allEntries = true)
    @Transactional
    public void delete(Long id) {
        SpecCatalogEntry e = get(id);
        if (e.getGpuCatalogId() != null) throw new IllegalArgumentException("Ky rresht fshihet nga Katalogu GPU.");
        repo.delete(e);
    }

    @CacheEvict(cacheNames = CacheConfig.SPEC_CATALOG, allEntries = true)
    @Transactional
    public SpecCatalogEntry fromProduct(Product p, String catalogKey) {
        Map<String, String> values = new LinkedHashMap<>();
        FieldTemplateService.Template template = templates.forCategory(p.getCategorySlug());
        for (ProductSpec s : p.getSpecs()) {
            if (template.field(s.getSpecKey()).isPresent()) values.put(s.getSpecKey(), s.getSpecValue());
        }
        if (values.isEmpty()) throw new IllegalArgumentException("Produkti nuk ka ende vlera te fushat e shabllonit, kështu që s'ka çfarë të ruhet.");
        SpecCatalogEntry e = new SpecCatalogEntry();
        e.setCategorySlug(catalogKey);
        e.setName(StringUtils.hasText(p.getModel()) ? p.getModel().trim() : p.getTitle().trim());
        e.setVerified(false);
        e.setSpecJson(new LinkedHashMap<>(values));
        return repo.save(e);
    }

    private Map<String, Object> cleanValues(String catalogKey, Map<String, String> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null) return out;
        Optional<FieldTemplateService.Template> template = templates.all().values().stream()
                .filter(t -> catalogKey.equals(t.catalogKey())).findFirst();
        for (Map.Entry<String, String> v : raw.entrySet()) {
            if (!StringUtils.hasText(v.getKey()) || !StringUtils.hasText(v.getValue())) continue;
            String key = v.getKey().trim();
            Optional<FieldTemplateService.Field> field = template.flatMap(t -> t.field(key));
            if (field.isPresent()) {
                try {
                    out.put(key, FieldTemplateService.normalize(field.get(), v.getValue()));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(field.get().labelSq() + ": " + e.getMessage());
                }
            } else {
                out.put(key, v.getValue().trim());
            }
        }
        return out;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void mirrorGpuCatalogAtStartup() {
        try {
            int n = self.mirrorGpuCatalog();
            if (n > 0) log.info("Spec catalogue: {} GPU row(s) brought in step with the GPU catalogue", n);
        } catch (RuntimeException e) {
            log.warn("Could not mirror the GPU catalogue into the spec catalogue: {}", e.getMessage());
        }
    }

    @CacheEvict(cacheNames = CacheConfig.SPEC_CATALOG, allEntries = true)
    @Transactional
    public int mirrorGpuCatalog() {
        int changed = 0;
        for (GpuCatalog g : gpus.findAll()) {
            if (mirror(g)) changed++;
        }
        return changed;
    }

    @CacheEvict(cacheNames = CacheConfig.SPEC_CATALOG, allEntries = true)
    @Transactional
    public void mirrorOne(GpuCatalog g) {
        mirror(g);
    }

    private boolean mirror(GpuCatalog g) {
        SpecCatalogEntry e = repo.findByGpuCatalogId(g.getId()).orElseGet(SpecCatalogEntry::new);
        Map<String, Object> spec = gpuSpec(g);
        boolean same = e.getId() != null && GPU.equals(e.getCategorySlug()) && Objects.equals(e.getName(), g.getName())
                && Objects.equals(e.getAliases(), g.getAliases()) && Objects.equals(e.getSpecJson(), spec);
        if (same) return false;
        e.setGpuCatalogId(g.getId());
        e.setCategorySlug(GPU);
        e.setName(g.getName());
        e.setAliases(g.getAliases());
        e.setSpecJson(spec);
        repo.save(e);
        return true;
    }

    static Map<String, Object> gpuSpec(GpuCatalog g) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (g.getVendor() != null) m.put("vendor", g.getVendor().label);
        putInt(m, "vram_gb", g.getVramGb());
        if (StringUtils.hasText(g.getMemoryType())) m.put("memory_type", g.getMemoryType());
        putInt(m, "memory_bus_bits", g.getMemoryBusBits());
        putInt(m, "tdp_watts", g.getTdpWatts());
        putInt(m, "psu_min_watts", g.getPsuMinWatts());
        if (StringUtils.hasText(g.getPcieConnectors())) m.put("pcie_connectors", g.getPcieConnectors());
        putInt(m, "length_mm", g.getLengthMm());
        if (g.getSupportsDlss() != null) m.put("supports_dlss", upscaler(g.getSupportsDlss()));
        m.put("frame_generation", String.valueOf(g.isFrameGeneration()));
        if (g.getSupportsFsr() != null) m.put("supports_fsr", upscaler(g.getSupportsFsr()));
        m.put("ray_tracing", String.valueOf(g.isRayTracing()));
        if (g.getDriverStatus() != null) m.put("driver_status", g.getDriverStatus().label);
        return m;
    }

    private static String upscaler(UpscalerVersion v) {
        return v.label;
    }

    private static void putInt(Map<String, Object> m, String key, Integer v) {
        if (v != null) m.put(key, String.valueOf(v));
    }
}
