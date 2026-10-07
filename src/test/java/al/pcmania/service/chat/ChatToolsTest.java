package al.pcmania.service.chat;

import al.pcmania.domain.Brand;
import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.GpuVendor;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.GpuCatalog;
import al.pcmania.domain.Product;
import al.pcmania.domain.ProductSpec;
import al.pcmania.repo.ProductRepository;
import al.pcmania.service.GpuCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChatToolsTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final GpuCatalogService catalog = mock(GpuCatalogService.class);
    private final ChatTools tools = new ChatTools(products, catalog);

    private final GpuCatalog gtx1660s = gpu("gtx-1660-super", "GeForce GTX 1660 Super", GpuVendor.NVIDIA, 6, 6, 450, 229, 200, 58, 37);
    private final GpuCatalog rx6600 = gpu("rx-6600", "Radeon RX 6600", GpuVendor.AMD, 8, 8, 450, 200, 240, 68, 45);
    private final GpuCatalog rtx3060ti = gpu("rtx-3060-ti", "GeForce RTX 3060 Ti", GpuVendor.NVIDIA, 8, 11, 600, 242, 300, 92, 64);
    private final GpuCatalog rtx3080 = gpu("rtx-3080-10gb", "GeForce RTX 3080 10GB", GpuVendor.NVIDIA, 10, 15, 750, 285, 380, 130, 92);

    private final Product p6600 = product(1L, "sapphire-rx-6600", "Sapphire RX 6600 Pulse", 24_000, 19_000, rx6600);
    private final Product p3060ti = product(2L, "msi-rtx-3060-ti", "MSI RTX 3060 Ti Ventus", 38_000, 30_000, rtx3060ti);
    private final Product p3080 = product(3L, "asus-rtx-3080", "ASUS TUF RTX 3080", 60_000, 48_000, rtx3080);

    @BeforeEach
    void stock() {

        when(products.findInStockWithGpuModel(ProductStatus.ACTIVE)).thenReturn(List.of(p3080, p3060ti, p6600));
        for (Product p : List.of(p6600, p3060ti, p3080)) when(products.findWithGpuModelBySlug(p.getSlug())).thenReturn(Optional.of(p));
        when(products.findWithGpuModelBySlug(anyString())).thenAnswer(inv -> List.of(p6600, p3060ti, p3080).stream()
                .filter(p -> p.getSlug().equals(inv.getArgument(0))).findFirst());
    }

    @Test
    void searchStockNarrowsByBudgetPsuVramAndVendor() {
        assertEquals(List.of("asus-rtx-3080", "msi-rtx-3060-ti", "sapphire-rx-6600"),
                tools.searchStock(null, null, null, null, null, null).stream().map(ChatTools.StockItem::slug).toList());
        assertEquals(List.of("msi-rtx-3060-ti", "sapphire-rx-6600"),
                tools.searchStock(null, 40_000, null, null, null, null).stream().map(ChatTools.StockItem::slug).toList());
        assertEquals(List.of("sapphire-rx-6600"),
                tools.searchStock(null, null, null, null, 500, null).stream().map(ChatTools.StockItem::slug).toList());
        assertEquals(List.of("asus-rtx-3080"),
                tools.searchStock(null, null, null, 10, null, null).stream().map(ChatTools.StockItem::slug).toList());
        assertEquals(List.of("sapphire-rx-6600"),
                tools.searchStock(null, null, null, null, null, GpuVendor.AMD).stream().map(ChatTools.StockItem::slug).toList());
        assertEquals(List.of("asus-rtx-3080", "msi-rtx-3060-ti"),
                tools.searchStock(30_000, null, ChatTools.UseCase.AAA_1440P, null, null, null).stream().map(ChatTools.StockItem::slug).toList());
    }

    @Test
    void toolResultsNeverCarryCost() throws Exception {
        ObjectMapper json = new ObjectMapper();
        String stock = json.writeValueAsString(tools.searchStock(null, null, null, null, null, null));
        String product = json.writeValueAsString(tools.getProduct("msi-rtx-3060-ti").orElseThrow());
        String compare = json.writeValueAsString(tools.compareProducts("sapphire-rx-6600", "msi-rtx-3060-ti").orElseThrow());
        String upgrade = json.writeValueAsString(tools.recommendUpgrade("1660 super", null, null));
        for (String s : List.of(stock, product, compare, upgrade)) {
            assertFalse(s.toLowerCase().contains("cost"), s);
            assertFalse(s.contains("30000") || s.contains("19000") || s.contains("48000"), s);
            assertFalse(s.toLowerCase().contains("tradevalue"), s);
        }
        assertTrue(stock.contains("\"priceLek\":38000"));
        assertTrue(product.contains("\"url\":\"/produkt/msi-rtx-3060-ti\""));
    }

    @Test
    void getProductTellsStockAndSpecs() {
        ChatTools.ProductInfo info = tools.getProduct("msi-rtx-3060-ti").orElseThrow();
        assertTrue(info.inStock());
        assertEquals("MSI", info.brand());
        assertEquals(11, info.gpu().tier());
        assertEquals(List.of(new ChatTools.Spec("VRAM", "8 GB")), info.specs());
        assertTrue(tools.getProduct("nuk-ekziston").isEmpty());

        p3080.changeStatus(ProductStatus.SOLD);
        assertFalse(tools.getProduct("asus-rtx-3080").orElseThrow().inStock());
        Product draft = product(9L, "draft", "Draft", 1, 1, rx6600);
        draft.setStatus(ProductStatus.DRAFT);
        when(products.findWithGpuModelBySlug("draft")).thenReturn(Optional.of(draft));
        assertTrue(tools.getProduct("draft").isEmpty());
    }

    @Test
    void comparisonUsesTiersAndFrameRates() {
        ChatTools.Comparison c = tools.compareProducts("sapphire-rx-6600", "msi-rtx-3060-ti").orElseThrow();
        assertEquals(3, c.tierDelta());

        assertEquals(34, c.performanceGapPercent());
        assertTrue(c.note().contains("MSI RTX 3060 Ti Ventus është afërsisht 34% më e shpejtë"), c.note());
        ChatTools.Comparison reverse = tools.compareProducts("msi-rtx-3060-ti", "sapphire-rx-6600").orElseThrow();
        assertEquals(-3, reverse.tierDelta());
        assertTrue(tools.compareProducts("sapphire-rx-6600", "nuk-ekziston").isEmpty());
    }

    @Test
    void upgradeRespectsThePowerSupplyAndTheBudget() {
        when(catalog.resolve("1660 super")).thenReturn(Optional.of(gtx1660s));
        ChatTools.UpgradeAdvice all = tools.recommendUpgrade("1660 super", null, null);
        assertEquals("GeForce GTX 1660 Super", all.currentCard().name());
        assertEquals(List.of("asus-rtx-3080", "msi-rtx-3060-ti", "sapphire-rx-6600"),
                all.options().stream().map(o -> o.product().slug()).toList());
        assertEquals(9, all.options().get(0).tierDelta());

        ChatTools.UpgradeAdvice psu = tools.recommendUpgrade("1660 super", 500, null);
        assertEquals(List.of("sapphire-rx-6600"), psu.options().stream().map(o -> o.product().slug()).toList());
        assertEquals(2, psu.options().get(0).tierDelta());

        ChatTools.UpgradeAdvice budget = tools.recommendUpgrade("1660 super", 650, 40_000);
        assertEquals(List.of("msi-rtx-3060-ti", "sapphire-rx-6600"), budget.options().stream().map(o -> o.product().slug()).toList());
    }

    @Test
    void upgradeSaysSoWhenNothingInStockIsAStepUp() {
        when(catalog.resolve("3080")).thenReturn(Optional.of(rtx3080));
        ChatTools.UpgradeAdvice none = tools.recommendUpgrade("3080", null, null);
        assertTrue(none.options().isEmpty());
        assertTrue(none.note().contains("Asnjë kartë në stok"), none.note());

        when(catalog.resolve(any())).thenReturn(Optional.empty());
        ChatTools.UpgradeAdvice unknown = tools.recommendUpgrade("karta e vjetër", null, null);
        assertNull(unknown.currentCard());
        assertTrue(unknown.note().contains("Nuk e njoha"), unknown.note());
    }

    @Test
    void fitChecksPowerAndLength() {
        ChatTools.FitCheck ok = tools.checkFit("msi-rtx-3060-ti", 650, 300).orElseThrow();
        assertEquals(ChatTools.Fit.OK, ok.verdict());
        assertEquals(ChatTools.Fit.TIGHT, tools.checkFit("msi-rtx-3060-ti", 560, null).orElseThrow().verdict());
        ChatTools.FitCheck no = tools.checkFit("msi-rtx-3060-ti", 450, null).orElseThrow();
        assertEquals(ChatTools.Fit.NO_FIT, no.verdict());
        assertTrue(no.reasons().get(0).contains("kërkon të paktën 600 W"));

        p3080.getSpecs().add(new ProductSpec(p3080, "Gjatësia", "320 mm", 2));
        ChatTools.FitCheck tight = tools.checkFit("asus-rtx-3080", 850, 325).orElseThrow();
        assertEquals(320, tight.cardLengthMm());
        assertEquals(ChatTools.Fit.TIGHT, tight.lengthVerdict());
        assertEquals(ChatTools.Fit.NO_FIT, tools.checkFit("asus-rtx-3080", 850, 300).orElseThrow().verdict());
        assertEquals(ChatTools.Fit.OK, tools.checkFit("asus-rtx-3080", null, null).orElseThrow().verdict());
        assertTrue(tools.checkFit("nuk-ekziston", 500, null).isEmpty());
    }

    @Test
    void contactFormIsOnlyASignal() {
        ChatTools.LeadFormSignal sig = tools.requestContactForm("RTX 4070", 80_000, 650);
        assertEquals("show_lead_form", sig.action());
        assertEquals("RTX 4070", sig.wantedItem());
        assertEquals(80_000, sig.budgetLek());
        assertThrows(IllegalArgumentException.class, () -> tools.requestContactForm(" ", null, null));
    }

    @Test
    void searchStockStopsAtSixResults() {
        List<Product> many = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) many.add(product(100L + i, "card-" + i, "Card " + i, 10_000 + i, 5_000, rx6600));
        when(products.findInStockWithGpuModel(ProductStatus.ACTIVE)).thenReturn(many);
        assertEquals(6, tools.searchStock(null, null, null, null, null, null).size());
        assertEquals(200, tools.searchStock(null, null, null, null, null, null).get(0).lengthMm());
    }

    private static GpuCatalog gpu(String slug, String name, GpuVendor vendor, int vram, int tier, int psu, int length, int esports, int aaa1080, int aaa1440) {
        GpuCatalog g = new GpuCatalog();
        g.setSlug(slug);
        g.setName(name);
        g.setVendor(vendor);
        g.setVramGb(vram);
        g.setTier(tier);
        g.setPsuMinWatts(psu);
        g.setLengthMm(length);
        g.setPcieConnectors("1x 8-pin");
        g.setFpsEsports1080p(esports);
        g.setFpsAaa1080p(aaa1080);
        g.setFpsAaa1440p(aaa1440);
        return g;
    }

    private static Product product(Long id, String slug, String title, int price, int cost, GpuCatalog gpu) {
        Product p = new Product();
        p.setId(id);
        p.setSlug(slug);
        p.setTitle(title);
        Brand b = new Brand();
        b.setName(title.split(" ")[0]);
        p.setBrand(b);
        p.setCategorySlug("karta-grafike");
        p.setCondition(Condition.USED);
        p.setPriceLek(price);
        p.setCostLek(cost);
        p.setQuantity(1);
        p.setGpuModel(gpu);
        p.setWarrantyDays(90);
        p.changeStatus(ProductStatus.ACTIVE);
        p.getSpecs().add(new ProductSpec(p, "VRAM", gpu.getVramGb() + " GB", 1));
        return p;
    }
}
