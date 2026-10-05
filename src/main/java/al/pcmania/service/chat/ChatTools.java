package al.pcmania.service.chat;

import al.pcmania.domain.ChatLead;
import al.pcmania.domain.ChatSession;
import al.pcmania.domain.Enums.GpuVendor;
import al.pcmania.domain.Enums.LeadStatus;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.GpuCatalog;
import al.pcmania.domain.Product;
import al.pcmania.domain.ProductSpec;
import al.pcmania.repo.ChatLeadRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.service.GpuCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The six tools the assistant can call. Every one reads our own database and nothing else; the
 * records they return are what the model sees, so none of them carries {@code costLek},
 * {@code maxTradeValueLek} or anything else that is internal. Only {@link #createLead} writes.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatTools {

    public enum UseCase { ESPORTS_1080P, AAA_1080P, AAA_1440P }

    public enum Fit { OK, TIGHT, NO_FIT }

    /** Statuses a customer may ask about by slug: listed, or recently listed and now reserved/sold. */
    private static final Set<ProductStatus> PUBLIC = EnumSet.of(ProductStatus.ACTIVE, ProductStatus.RESERVED, ProductStatus.SOLD);
    /** The spec key the admin autofill writes the exact card length under. */
    private static final Pattern LENGTH_SPEC = Pattern.compile("(?i)^(gjat[eë]sia|length)$");
    private static final Pattern LEADING_NUMBER = Pattern.compile("(\\d+)");

    private final ProductRepository products;
    private final GpuCatalogService catalog;
    private final ChatLeadRepository leads;

    // ---- What the model sees ----

    /** A card in stock, as searchStock lists it. */
    public record StockItem(String slug, String title, int priceLek, String condition, Integer warrantyDays,
                            Integer vramGb, Integer tier, Integer psuMinWatts, String pcieConnectors,
                            String testNotes, boolean isMiningFree, boolean transportIncluded, String gpuModel,
                            Integer fpsEsports1080p, Integer fpsAaa1080p, Integer fpsAaa1440p) {
        static StockItem of(Product p) {
            GpuCatalog g = p.getGpuModel();
            return new StockItem(p.getSlug(), p.getTitle(), p.getPriceLek(), p.getCondition().label, p.getWarrantyDays(),
                    g == null ? null : g.getVramGb(), g == null ? null : g.getTier(), g == null ? null : g.getPsuMinWatts(),
                    g == null ? null : g.getPcieConnectors(), p.getTestNotes(), p.isMiningFree(), p.isTransportIncluded(),
                    g == null ? null : g.getName(), g == null ? null : g.getFpsEsports1080p(),
                    g == null ? null : g.getFpsAaa1080p(), g == null ? null : g.getFpsAaa1440p());
        }
    }

    /** The catalogue side of a product, for getProduct and compareProducts. */
    public record GpuInfo(String name, String vendor, Integer releaseYear, String architecture, Integer vramGb,
                          String memoryType, Integer memoryBusBits, Integer tdpWatts, Integer psuMinWatts,
                          String pcieConnectors, Integer lengthMm, Integer tier, String supportsDlss, boolean frameGeneration,
                          String supportsFsr, boolean rayTracing, String driverStatus, String miningRisk,
                          Integer fpsEsports1080p, Integer fpsAaa1080p, Integer fpsAaa1440p, String notes) {
        static GpuInfo of(GpuCatalog g) {
            if (g == null) return null;
            return new GpuInfo(g.getName(), g.getVendor() == null ? null : g.getVendor().name(), g.getReleaseYear(),
                    g.getArchitecture(), g.getVramGb(), g.getMemoryType(), g.getMemoryBusBits(), g.getTdpWatts(),
                    g.getPsuMinWatts(), g.getPcieConnectors(), g.getLengthMm(), g.getTier(),
                    g.getSupportsDlss() == null ? null : g.getSupportsDlss().name(), g.isFrameGeneration(),
                    g.getSupportsFsr() == null ? null : g.getSupportsFsr().name(), g.isRayTracing(),
                    g.getDriverStatus() == null ? null : g.getDriverStatus().name(),
                    g.getMiningRisk() == null ? null : g.getMiningRisk().name(),
                    g.getFpsEsports1080p(), g.getFpsAaa1080p(), g.getFpsAaa1440p(), g.getNotesSq());
        }
    }

    public record Spec(String key, String value) {}

    /** One product in full, for getProduct. */
    public record ProductInfo(String slug, String title, String brand, int priceLek, String condition, String status,
                              boolean inStock, int quantity, Integer warrantyDays, String shortDescription,
                              String testNotes, boolean isMiningFree, boolean transportIncluded, boolean tradeEligible,
                              List<Spec> specs, GpuInfo gpu, String url) {
        static ProductInfo of(Product p) {
            return new ProductInfo(p.getSlug(), p.getTitle(), p.getBrand() == null ? null : p.getBrand().getName(),
                    p.getPriceLek(), p.getCondition().label, p.getStatus().name(), ChatTools.inStock(p), p.getQuantity(),
                    p.getWarrantyDays(), p.getShortDescription(), p.getTestNotes(), p.isMiningFree(),
                    p.isTransportIncluded(), p.isTradeEligible(),
                    p.getSpecs().stream().map(s -> new Spec(s.getSpecKey(), s.getSpecValue())).toList(),
                    GpuInfo.of(p.getGpuModel()), "/produkt/" + p.getSlug());
        }
    }

    public record Comparison(ProductInfo a, ProductInfo b, Integer tierDelta, Integer performanceGapPercent, String note) {}

    public record UpgradeOption(StockItem product, int tierDelta, Integer performanceGapPercent) {}

    public record UpgradeAdvice(GpuInfo currentCard, List<UpgradeOption> options, String note) {}

    public record FitCheck(String slug, Fit verdict, Fit psuVerdict, Fit lengthVerdict, Integer psuMinWatts,
                           Integer cardLengthMm, List<String> reasons) {}

    public record LeadResult(boolean ok, Long leadId, String message) {}

    // ---- The tools ----

    /** ACTIVE products with a catalogue row, fastest first, narrowed by whatever the customer has said. */
    public List<StockItem> searchStock(Integer budgetMinLek, Integer budgetMaxLek, UseCase useCase, Integer minVramGb,
                                       Integer maxPsuWatts, GpuVendor vendor) {
        List<StockItem> out = new ArrayList<>();
        for (Product p : products.findInStockWithGpuModel(ProductStatus.ACTIVE)) {
            GpuCatalog g = p.getGpuModel();
            if (budgetMinLek != null && p.getPriceLek() < budgetMinLek) continue;
            if (budgetMaxLek != null && p.getPriceLek() > budgetMaxLek) continue;
            if (minVramGb != null && (g.getVramGb() == null || g.getVramGb() < minVramGb)) continue;
            if (maxPsuWatts != null && g.getPsuMinWatts() != null && g.getPsuMinWatts() > maxPsuWatts) continue;
            if (vendor != null && g.getVendor() != vendor) continue;
            if (useCase == UseCase.AAA_1440P && g.getFpsAaa1440p() != null && g.getFpsAaa1440p() < 40) continue;
            out.add(StockItem.of(p));
        }
        return out;
    }

    public Optional<ProductInfo> getProduct(String slug) {
        return product(slug).map(ProductInfo::of);
    }

    /** Both products side by side, with how far apart they are on the catalogue's tiers and frame rates. */
    public Optional<Comparison> compareProducts(String slugA, String slugB) {
        Optional<Product> a = product(slugA), b = product(slugB);
        if (a.isEmpty() || b.isEmpty()) return Optional.empty();
        GpuCatalog ga = a.get().getGpuModel(), gb = b.get().getGpuModel();
        Integer tierDelta = ga == null || gb == null || ga.getTier() == null || gb.getTier() == null ? null : gb.getTier() - ga.getTier();
        Integer gap = performanceGap(ga, gb);
        String note = gap == null ? "Njëra kartë nuk ka të dhëna në katalog, prandaj nuk ka krahasim performance."
                : gap > 0 ? b.get().getTitle() + " është afërsisht " + gap + "% më e shpejtë se " + a.get().getTitle() + "."
                : gap < 0 ? a.get().getTitle() + " është afërsisht " + (-gap) + "% më e shpejtë se " + b.get().getTitle() + "."
                : "Të dyja kartat janë afërsisht në të njëjtin nivel.";
        return Optional.of(new Comparison(ProductInfo.of(a.get()), ProductInfo.of(b.get()), tierDelta, gap, note));
    }

    /**
     * What in stock is a step up from the customer's current card. The card is resolved with the same
     * matcher as the admin autofill; only products with a higher tier qualify, and never one whose
     * minimum power supply exceeds what the customer has.
     */
    public UpgradeAdvice recommendUpgrade(String currentCardQuery, Integer psuWatts, Integer budgetLek) {
        Optional<GpuCatalog> current = catalog.resolve(currentCardQuery);
        if (current.isEmpty()) {
            return new UpgradeAdvice(null, List.of(), "Nuk e njoha kartën \"" + currentCardQuery
                    + "\" në katalog. Pyet klientin për modelin e saktë (p.sh. \"GTX 1660 Super\", \"RX 580 8GB\").");
        }
        GpuCatalog cur = current.get();
        List<UpgradeOption> options = new ArrayList<>();
        int bestTier = cur.getTier() == null ? 0 : cur.getTier();
        for (Product p : products.findInStockWithGpuModel(ProductStatus.ACTIVE)) {
            GpuCatalog g = p.getGpuModel();
            if (g.getTier() == null || cur.getTier() == null || g.getTier() <= cur.getTier()) continue;
            if (psuWatts != null && g.getPsuMinWatts() != null && g.getPsuMinWatts() > psuWatts) continue;
            if (budgetLek != null && p.getPriceLek() > budgetLek) continue;
            options.add(new UpgradeOption(StockItem.of(p), g.getTier() - cur.getTier(), performanceGap(cur, g)));
            bestTier = Math.max(bestTier, g.getTier());
        }
        String note;
        if (options.isEmpty()) {
            note = "Asnjë kartë në stok nuk është hap përpara nga " + cur.getName()
                    + (psuWatts != null ? " me PSU " + psuWatts + " W" : "") + (budgetLek != null ? " brenda " + budgetLek + " Lekë" : "")
                    + ". Thuaja hapur: karta e tij është në nivel me atë që kemi, ose ofro PC me porosi / createLead.";
        } else if (bestTier - (cur.getTier() == null ? 0 : cur.getTier()) <= 1) {
            note = "Hapi përpara është i vogël (1 tier). Thuaja klientit se ndryshimi do të jetë i vogël dhe mos e shty të blejë.";
        } else {
            note = "Opsionet janë renditur nga më e shpejta. Para se të rekomandosh një kartë me psuMinWatts mbi 450 W, pyet për furnizuesin e energjisë nëse nuk e di.";
        }
        return new UpgradeAdvice(GpuInfo.of(cur), options, note);
    }

    /** Whether a card in stock works with the customer's power supply and fits their case. */
    public Optional<FitCheck> checkFit(String slug, Integer psuWatts, Integer caseLengthMm) {
        return product(slug).map(p -> {
            GpuCatalog g = p.getGpuModel();
            List<String> reasons = new ArrayList<>();
            Integer psuMin = g == null ? null : g.getPsuMinWatts();
            Fit psu = null;
            if (psuWatts != null) {
                if (psuMin == null) {
                    reasons.add("Nuk kemi të dhëna për furnizuesin minimal të kësaj karte.");
                } else if (psuWatts >= psuMin) {
                    psu = Fit.OK;
                    reasons.add("PSU " + psuWatts + " W mjafton (minimumi " + psuMin + " W).");
                } else if (psuWatts >= psuMin - 50) {
                    psu = Fit.TIGHT;
                    reasons.add("PSU " + psuWatts + " W është në kufi: rekomandohet " + psuMin + " W. Varet nga cilësia e PSU-së dhe procesori.");
                } else {
                    psu = Fit.NO_FIT;
                    reasons.add("PSU " + psuWatts + " W nuk mjafton: karta kërkon të paktën " + psuMin + " W.");
                }
            }
            Integer length = cardLength(p);
            Fit len = null;
            if (caseLengthMm != null) {
                if (length == null) {
                    reasons.add("Nuk kemi gjatësinë e kësaj karte.");
                } else if (caseLengthMm >= length + 10) {
                    len = Fit.OK;
                    reasons.add("Karta është " + length + " mm, kasa pranon " + caseLengthMm + " mm: hyn.");
                } else if (caseLengthMm >= length) {
                    len = Fit.TIGHT;
                    reasons.add("Karta është " + length + " mm dhe kasa pranon " + caseLengthMm + " mm: hyn ngushtë, kontrollo kabllot e energjisë.");
                } else {
                    len = Fit.NO_FIT;
                    reasons.add("Karta është " + length + " mm, kasa pranon vetëm " + caseLengthMm + " mm: nuk hyn.");
                }
            }
            if (psuWatts == null && caseLengthMm == null) reasons.add("Nuk u dha as PSU as gjatësia e kasës: pyet klientin.");
            Fit overall = worst(psu, len);
            return new FitCheck(p.getSlug(), overall == null ? Fit.OK : overall, psu, len, psuMin, length, reasons);
        });
    }

    /** Records a customer to call back. Marks the session so the admin sees which conversations converted. */
    @Transactional
    public LeadResult createLead(ChatSession session, String name, String phone, String wantedItem, Integer budgetLek,
                                 Integer psuWatts, String notes) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(phone) || !StringUtils.hasText(wantedItem)) {
            return new LeadResult(false, null, "Duhen emri, telefoni dhe çfarë kërkon klienti.");
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 8) return new LeadResult(false, null, "Numri i telefonit nuk duket i plotë. Pyet përsëri.");
        ChatLead lead = new ChatLead();
        lead.setSession(session);
        lead.setName(name.trim());
        lead.setPhone(phone.trim());
        lead.setWantedItem(wantedItem.trim());
        lead.setBudgetLek(budgetLek);
        lead.setPsuWatts(psuWatts);
        lead.setNotes(StringUtils.hasText(notes) ? notes.trim() : null);
        lead.setStatus(LeadStatus.NEW);
        leads.save(lead);
        session.setLeadCaptured(true);
        return new LeadResult(true, lead.getId(), "U regjistrua. Thuaji klientit se do ta telefonojmë.");
    }

    // ---- Helpers ----

    private Optional<Product> product(String slug) {
        if (!StringUtils.hasText(slug)) return Optional.empty();
        return products.findWithGpuModelBySlug(slug.trim()).filter(p -> PUBLIC.contains(p.getStatus()));
    }

    static boolean inStock(Product p) {
        return p.getStatus() == ProductStatus.ACTIVE && p.getQuantity() > 0;
    }

    /** Average of the per-use-case frame-rate ratios, as a rounded percentage: positive when {@code b} is faster. */
    static Integer performanceGap(GpuCatalog a, GpuCatalog b) {
        if (a == null || b == null) return null;
        double sum = 0;
        int n = 0;
        Integer[][] pairs = {{a.getFpsEsports1080p(), b.getFpsEsports1080p()}, {a.getFpsAaa1080p(), b.getFpsAaa1080p()}, {a.getFpsAaa1440p(), b.getFpsAaa1440p()}};
        for (Integer[] pair : pairs) {
            if (pair[0] == null || pair[1] == null || pair[0] == 0) continue;
            sum += (pair[1] / (double) pair[0] - 1) * 100;
            n++;
        }
        return n == 0 ? null : (int) Math.round(sum / n);
    }

    /** The exact length from the product's specs when the operator entered one, else the catalogue's reference. */
    static Integer cardLength(Product p) {
        for (ProductSpec s : p.getSpecs()) {
            if (LENGTH_SPEC.matcher(s.getSpecKey().trim()).matches()) {
                Matcher m = LEADING_NUMBER.matcher(s.getSpecValue());
                if (m.find()) return Integer.parseInt(m.group(1));
            }
        }
        return p.getGpuModel() == null ? null : p.getGpuModel().getLengthMm();
    }

    private static Fit worst(Fit a, Fit b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
