package al.pcmania.service;

import al.pcmania.config.CacheConfig;
import al.pcmania.domain.Enums.UpscalerVersion;
import al.pcmania.domain.GpuCatalog;
import al.pcmania.repo.GpuCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GpuCatalogService {

    public record Match(GpuCatalog gpu, int score) {}

    private final GpuCatalogRepository repo;

    @Cacheable(CacheConfig.GPU_CATALOG)
    public List<GpuCatalog> all() {
        return repo.findAllByOrderByTierDescNameAsc();
    }

    public GpuCatalog get(Long id) {
        return repo.findById(id).orElseThrow(NotFoundException::new);
    }

    public Optional<GpuCatalog> find(Long id) {
        return id == null ? Optional.empty() : repo.findById(id);
    }

    public List<Match> search(String query, int limit) {
        String q = normalize(query);
        if (q.length() < 2) return List.of();
        List<Match> hits = new ArrayList<>();
        for (GpuCatalog g : all()) {
            int score = score(g, q);
            if (score > 0) hits.add(new Match(g, score));
        }
        hits.sort(Comparator.comparingInt(Match::score).reversed()
                .thenComparing(m -> m.gpu().getTier() == null ? 0 : m.gpu().getTier(), Comparator.reverseOrder())
                .thenComparing(m -> m.gpu().getName()));
        return hits.size() > limit ? hits.subList(0, limit) : hits;
    }

    public Optional<GpuCatalog> resolve(String query) {
        List<Match> hits = search(query, 2);
        if (hits.isEmpty()) return Optional.empty();
        Match best = hits.get(0);

        if (hits.size() > 1 && hits.get(1).score() == best.score()) return Optional.empty();
        return Optional.of(best.gpu());
    }

    private static int score(GpuCatalog g, String q) {
        int best = 0;
        List<String> terms = new ArrayList<>();
        terms.add(g.getName());
        terms.addAll(g.aliasList());
        for (String raw : terms) {
            String t = normalize(raw);
            if (t.isEmpty()) continue;
            int s;
            if (t.equals(q)) s = 1000;
            else if (t.startsWith(q)) s = 800 + q.length();
            else if (t.contains(q)) s = 600 + q.length();
            else if (t.length() >= 4 && q.contains(t)) {
                int end = q.indexOf(t) + t.length();
                s = 400 + Math.min(end, 40) * 4 + Math.min(t.length(), 30);
            } else s = 0;
            best = Math.max(best, s);
        }
        return best;
    }

    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString();
    }

    public static String shortDescriptionSq(GpuCatalog g) {
        StringBuilder s = new StringBuilder(g.getName());
        List<String> intro = new ArrayList<>();
        if (StringUtils.hasText(g.getArchitecture())) intro.add(g.getArchitecture());
        if (g.getReleaseYear() != null) intro.add(String.valueOf(g.getReleaseYear()));
        if (!intro.isEmpty()) s.append(" (").append(String.join(", ", intro)).append(")");
        List<String> specs = new ArrayList<>();
        if (g.getVramGb() != null) specs.add(g.getVramGb() + " GB" + (StringUtils.hasText(g.getMemoryType()) ? " " + g.getMemoryType() : ""));
        if (g.getTdpWatts() != null) specs.add("TDP " + g.getTdpWatts() + " W");
        if (g.getPsuMinWatts() != null) specs.add("PSU min. " + g.getPsuMinWatts() + " W");
        if (!specs.isEmpty()) s.append(" – ").append(String.join(", ", specs));
        s.append('.');
        List<String> features = new ArrayList<>();
        if (g.getSupportsDlss() != null && g.getSupportsDlss() != UpscalerVersion.NONE) features.add("DLSS " + g.getSupportsDlss().label);
        if (g.getSupportsFsr() != null && g.getSupportsFsr() != UpscalerVersion.NONE) features.add("FSR " + g.getSupportsFsr().label);
        if (g.isRayTracing()) features.add("ray tracing");
        if (!features.isEmpty()) s.append(" Mbështet ").append(String.join(", ", features)).append('.');
        if (g.getFpsAaa1080p() != null || g.getFpsAaa1440p() != null) {
            s.append(" Lojëra AAA");
            if (g.getFpsAaa1080p() != null) s.append(" ~").append(g.getFpsAaa1080p()).append(" FPS në 1080p");
            if (g.getFpsAaa1080p() != null && g.getFpsAaa1440p() != null) s.append(",");
            if (g.getFpsAaa1440p() != null) s.append(" ~").append(g.getFpsAaa1440p()).append(" FPS në 1440p");
            s.append('.');
        }
        return s.toString();
    }

    @CacheEvict(cacheNames = CacheConfig.GPU_CATALOG, allEntries = true)
    @Transactional
    public GpuCatalog save(Long id, GpuCatalog form) {
        GpuCatalog g = id == null ? new GpuCatalog() : get(id);
        if (!StringUtils.hasText(form.getName())) throw new IllegalArgumentException("Shkruani emrin.");
        if (form.getVendor() == null) throw new IllegalArgumentException("Zgjidhni prodhuesin.");
        String slug = StringUtils.hasText(form.getSlug()) ? Slugs.of(form.getSlug()) : Slugs.of(form.getName());
        if (!slug.equals(g.getSlug())) {
            if (repo.existsBySlug(slug)) throw new IllegalArgumentException("Ekziston një rresht me slug \"" + slug + "\".");
            g.setSlug(slug);
        }
        g.setName(form.getName().trim());
        g.setVendor(form.getVendor());
        g.setAliases(trimToNull(form.getAliases()));
        g.setReleaseYear(form.getReleaseYear());
        g.setArchitecture(trimToNull(form.getArchitecture()));
        g.setVramGb(form.getVramGb());
        g.setMemoryType(trimToNull(form.getMemoryType()));
        g.setMemoryBusBits(form.getMemoryBusBits());
        g.setTdpWatts(form.getTdpWatts());
        g.setPsuMinWatts(form.getPsuMinWatts());
        g.setPcieConnectors(trimToNull(form.getPcieConnectors()));
        g.setLengthMm(form.getLengthMm());
        if (form.getTier() != null && (form.getTier() < 1 || form.getTier() > 20)) throw new IllegalArgumentException("Tier duhet të jetë 1–20.");
        g.setTier(form.getTier());
        g.setSupportsDlss(form.getSupportsDlss());
        g.setFrameGeneration(form.isFrameGeneration());
        g.setSupportsFsr(form.getSupportsFsr());
        g.setRayTracing(form.isRayTracing());
        g.setDriverStatus(form.getDriverStatus());
        g.setMiningRisk(form.getMiningRisk());
        g.setFpsEsports1080p(form.getFpsEsports1080p());
        g.setFpsAaa1080p(form.getFpsAaa1080p());
        g.setFpsAaa1440p(form.getFpsAaa1440p());
        g.setNotesSq(trimToNull(form.getNotesSq()));
        return repo.save(g);
    }

    @CacheEvict(cacheNames = CacheConfig.GPU_CATALOG, allEntries = true)
    @Transactional
    public void delete(Long id) {
        repo.delete(get(id));
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
