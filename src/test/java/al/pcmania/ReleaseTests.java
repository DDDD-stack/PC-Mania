package al.pcmania;

import al.pcmania.domain.Combo;
import al.pcmania.domain.Enums.DeliveryMethod;
import al.pcmania.domain.Enums.PaymentMethod;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Order;
import al.pcmania.domain.OrderItem;
import al.pcmania.domain.Product;
import al.pcmania.domain.ProductSpec;
import al.pcmania.domain.SpecCatalogEntry;
import al.pcmania.repo.BrandRepository;
import al.pcmania.repo.BuildRequestRepository;
import al.pcmania.repo.ChatLeadRepository;
import al.pcmania.repo.ComboRepository;
import al.pcmania.repo.GpuCatalogRepository;
import al.pcmania.repo.OrderRepository;
import al.pcmania.repo.PrebuiltSpecRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.repo.SpecCatalogRepository;
import al.pcmania.service.OrderService;
import al.pcmania.web.site.CheckoutForm;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.base-url=https://shop.example")
@AutoConfigureMockMvc
class ReleaseTests {

    static final int SECRET_COST = 987_654;

    private static final EmbeddedPostgres PG = LocalPostgres.startTemporary();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        LocalPostgres.properties(PG).forEach((k, v) -> registry.add(k, () -> v));
    }

    @AfterAll
    static void stop() throws IOException {
        PG.close();
    }

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired BrandRepository brands;
    @Autowired ComboRepository combos;
    @Autowired OrderRepository orders;
    @Autowired OrderService orderService;
    @Autowired SpecCatalogRepository specCatalog;
    @Autowired GpuCatalogRepository gpuCatalog;
    @Autowired PrebuiltSpecRepository prebuilts;
    @Autowired TransactionTemplate tx;
    @Autowired BuildRequestRepository builds;
    @Autowired ChatLeadRepository leads;

    @Test
    void costNeverReachesAPublicResponse() throws Exception {
        Product active = product("karta-grafike", 52_000, ProductStatus.ACTIVE);
        Product reserved = product("karta-grafike", 41_000, ProductStatus.RESERVED);
        Product sold = product("karta-grafike", 39_000, ProductStatus.SOLD);
        Product other = product("karta-grafike", 30_000, ProductStatus.ACTIVE);
        Combo combo = combo(active, other, 75_000);
        Product pc = prebuiltProduct();

        String token = active.getTitle().substring(9, 20);
        for (String path : List.of("/", "/kategori/karta-grafike", "/kategori/pc-te-gatshem", "/kerko?q=" + token,
                "/produkt/" + active.getSlug(), "/produkt/" + reserved.getSlug(), "/produkt/" + sold.getSlug(),
                "/produkt/" + pc.getSlug(), "/paketa/" + combo.getSlug(), "/porosit/" + active.getSlug(),
                "/porosit/paketa/" + combo.getSlug(), "/sitemap.xml", "/api/finder/step",
                "/api/finder/step?use=GAMING_1080P&budget=60000", "/nderro", "/se-shpejti")) {
            String body = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertFalse(body.contains(String.valueOf(SECRET_COST)), "cost in " + path);
            assertFalse(body.contains("987.654"), "formatted cost in " + path);
            assertFalse(body.contains("987 654"), "spaced cost in " + path);
            assertFalse(body.toLowerCase().contains("costlek") || body.toLowerCase().contains("cost_lek"), "cost field in " + path);
        }
    }

    @Test
    void publicViewsHaveNoCostComponent() throws Exception {
        List<Class<?>> exposed = List.of(al.pcmania.web.view.ProductCard.class, al.pcmania.web.view.ProductDetail.class,
                al.pcmania.web.view.ProductDetail.Spec.class, al.pcmania.web.view.PrebuiltView.class,
                al.pcmania.web.view.ComboView.class, al.pcmania.web.view.ComboView.Member.class,
                al.pcmania.web.view.Seo.class, al.pcmania.service.chat.ChatService.Card.class,
                al.pcmania.service.chat.ChatTools.StockItem.class, al.pcmania.web.chat.FinderApiController.Response.class,
                al.pcmania.web.site.CheckoutController.Item.class);
        for (Class<?> c : exposed) {
            assertTrue(c.isRecord(), c + " should be a record");
            for (var component : c.getRecordComponents()) {
                String name = component.getName().toLowerCase();
                assertFalse(name.contains("cost") || name.contains("margin") || name.contains("profit"), c.getSimpleName() + "." + component.getName());
            }
        }
    }

    @Test
    void soldProductsLeaveListingsButTheirPageStillResolves() throws Exception {
        Product sold = product("karta-grafike", 44_000, ProductStatus.SOLD);
        Product reserved = product("karta-grafike", 45_000, ProductStatus.RESERVED);
        Product similar = product("karta-grafike", 46_000, ProductStatus.ACTIVE);

        mvc.perform(get("/kategori/karta-grafike")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(sold.getSlug()))))
                .andExpect(content().string(containsString(reserved.getSlug())))
                .andExpect(content().string(containsString("REZERVUAR")));
        mvc.perform(get("/")).andExpect(content().string(not(containsString(sold.getSlug()))));
        mvc.perform(get("/sitemap.xml")).andExpect(content().string(not(containsString(sold.getSlug()))))
                .andExpect(content().string(containsString(reserved.getSlug())));
        mvc.perform(get("/kerko").param("q", sold.getTitle().substring(9, 22)))
                .andExpect(content().string(not(containsString(sold.getSlug()))));

        mvc.perform(get("/produkt/" + sold.getSlug())).andExpect(status().isOk())
                .andExpect(content().string(containsString("SHITUR")))
                .andExpect(content().string(containsString("Ky produkt është shitur.")))
                .andExpect(content().string(containsString(similar.getSlug())))
                .andExpect(content().string(not(containsString("href=\"/porosit/" + sold.getSlug() + "\""))));
        mvc.perform(get("/produkt/" + reserved.getSlug())).andExpect(status().isOk())
                .andExpect(content().string(containsString("REZERVUAR")))
                .andExpect(content().string(not(containsString("href=\"/porosit/" + reserved.getSlug() + "\""))));
    }

    @Test
    void productPagesCarryAbsoluteSharingTags() throws Exception {
        Product p = product("karta-grafike", 52_000, ProductStatus.ACTIVE);
        tx.executeWithoutResult(s -> {
            Product x = products.findById(p.getId()).orElseThrow();
            x.getSpecs().add(new ProductSpec(x, "vram_gb", "12", 1));
            x.getSpecs().add(new ProductSpec(x, "memory_type", "GDDR6", 2));
        });
        mvc.perform(get("/produkt/" + p.getSlug())).andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta property=\"og:title\" content=\"" + p.getTitle() + "\">")))
                .andExpect(content().string(containsString("<meta property=\"og:url\" content=\"https://shop.example/produkt/" + p.getSlug() + "\">")))
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"https://shop.example/produkt/" + p.getSlug() + "\">")))
                .andExpect(content().string(containsString("<meta property=\"og:image\" content=\"https://shop.example/")))
                .andExpect(content().string(containsString("<meta property=\"og:type\" content=\"product\">")))
                .andExpect(content().string(containsString("<meta name=\"twitter:card\" content=\"summary_large_image\">")))
                .andExpect(content().string(containsString("12 GB · GDDR6 · 52.000 Lekë")))
                .andExpect(content().string(containsString("<th>Memoria (VRAM)</th><td>12 GB</td>")));
        mvc.perform(get("/sitemap.xml")).andExpect(content().string(containsString("<loc>https://shop.example/kategori/karta-grafike</loc>")));
    }

    @Test
    void homepageWordingIsFixed() throws Exception {
        mvc.perform(get("/")).andExpect(content().string(containsString("Produktet e fundit")))
                .andExpect(content().string(not(containsString("https://www.facebook.com/\""))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void theListChangesStatusInOneClickAndHidesSoldByDefault() throws Exception {
        Product p = product("karta-grafike", 33_000, ProductStatus.ACTIVE);
        mvc.perform(post("/admin/products/" + p.getId() + "/quick-status").with(csrf()).param("to", "SOLD")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.soldAt").isNotEmpty());
        Product after = products.findById(p.getId()).orElseThrow();
        assertEquals(ProductStatus.SOLD, after.getStatus());
        assertNotNull(after.getSoldAt());

        mvc.perform(get("/admin/products").param("q", p.getTitle())).andExpect(content().string(not(containsString(p.getSlug()))));
        mvc.perform(get("/admin/products").param("q", p.getTitle()).param("all", "true")).andExpect(content().string(containsString(p.getTitle())));

        mvc.perform(post("/admin/products/" + p.getId() + "/quick-status").with(csrf()).param("to", "ACTIVE"))
                .andExpect(status().is3xxRedirection());
        assertNull(products.findById(p.getId()).orElseThrow().getSoldAt());
        mvc.perform(get("/admin")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aComboVanishesWhenOneOfItsPartsSells() throws Exception {
        Product gpu = product("karta-grafike", 50_000, ProductStatus.ACTIVE);
        Product cpu = product("procesore", 20_000, ProductStatus.ACTIVE);
        String title = "Paketë test " + UUID.randomUUID();

        mvc.perform(post("/admin/combos").with(csrf()).param("titleSq", title).param("comboPriceLek", "70000")
                        .param("active", "true").param("productId", gpu.getId().toString(), cpu.getId().toString())
                        .param("quantity", "1", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("duhet të jetë më i ulët")));

        mvc.perform(post("/admin/combos").with(csrf()).param("titleSq", title).param("comboPriceLek", "65000")
                        .param("active", "true").param("productId", gpu.getId().toString(), cpu.getId().toString())
                        .param("quantity", "1", "1"))
                .andExpect(status().is3xxRedirection());
        Combo combo = combos.findAll().stream().filter(c -> c.getTitleSq().equals(title)).findFirst().orElseThrow();

        mvc.perform(get("/")).andExpect(content().string(containsString("Oferta – Paketa")))
                .andExpect(content().string(containsString(title)))
                .andExpect(content().string(containsString("Kurseni 5 mijë lekë")));
        mvc.perform(get("/paketa/" + combo.getSlug())).andExpect(status().isOk())
                .andExpect(content().string(containsString("/porosit/paketa/" + combo.getSlug())));

        mvc.perform(post("/admin/products/" + gpu.getId() + "/quick-status").with(csrf()).param("to", "SOLD"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/")).andExpect(content().string(not(containsString(title))));
        mvc.perform(get("/admin/combos")).andExpect(status().isOk())
                .andExpect(content().string(containsString("I fshehur: " + gpu.getTitle() + " është SHITUR")));
        mvc.perform(get("/paketa/" + combo.getSlug())).andExpect(status().isOk())
                .andExpect(content().string(containsString("nuk është më e disponueshme")))
                .andExpect(content().string(not(containsString("href=\"/porosit/paketa/" + combo.getSlug() + "\""))));
        mvc.perform(get("/porosit/paketa/" + combo.getSlug())).andExpect(status().is3xxRedirection());
    }

    @Test
    void orderingAComboReservesEveryPartAtTheComboPrice() throws Exception {
        Product a = product("karta-grafike", 49_999, ProductStatus.ACTIVE);
        Product b = product("procesore", 20_001, ProductStatus.ACTIVE);
        Product c = product("memorie-ram", 7_000, ProductStatus.ACTIVE);
        Combo combo = tx.execute(s -> {
            Combo x = new Combo();
            x.setTitleSq("Tre pjesë " + UUID.randomUUID());
            x.setSlug("tre-" + UUID.randomUUID());
            x.setComboPriceLek(70_001);
            x.getItems().add(new al.pcmania.domain.ComboItem(x, products.findById(a.getId()).orElseThrow(), 1));
            x.getItems().add(new al.pcmania.domain.ComboItem(x, products.findById(b.getId()).orElseThrow(), 1));
            x.getItems().add(new al.pcmania.domain.ComboItem(x, products.findById(c.getId()).orElseThrow(), 1));
            return combos.save(x);
        });
        mvc.perform(post("/porosit/paketa/" + combo.getSlug())
                        .param("customerName", "Test Klient").param("customerPhone", "069 123 4567").param("city", "Tiranë")
                        .param("deliveryMethod", "PICKUP_TIRANA").param("paymentMethod", "CASH_ON_DELIVERY").param("quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/porosit/sukses"));

        Order o = tx.execute(s -> {
            Order x = orders.findAll().stream().filter(y -> combo.getId().equals(y.getComboId())).findFirst().orElseThrow();
            x.getItems().size();
            return x;
        });
        assertEquals(70_001, o.getSubtotalLek());
        assertEquals(70_001, o.getItems().stream().mapToInt(OrderItem::getLineTotalLek).sum());
        assertEquals(3, o.getItems().size());
        assertEquals(combo.getTitleSq(), o.getComboTitleSnapshot());
        for (Product p : List.of(a, b, c)) assertEquals(ProductStatus.RESERVED, products.findById(p.getId()).orElseThrow().getStatus());

        mvc.perform(get("/api/v1/summary").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.latestOrderId").value(o.getId()));

        assertThrows(OrderService.OutOfStockException.class, () -> orderService.placeCombo(combo.getSlug(), checkout()));
    }

    @Test
    void newRequestsShowUpForThePhoneApp() throws Exception {
        mvc.perform(post("/pc-me-porosi").param("customerName", "Ardit Test").param("customerPhone", "069 765 4321")
                        .param("budgetLek", "120000").param("useCase", "GAMING_1080P").param("games", "CS2, Fortnite"))
                .andExpect(status().is3xxRedirection());
        Long buildId = builds.latestId();
        assertEquals("CS2, Fortnite", builds.findById(buildId).orElseThrow().getGames());

        mvc.perform(post("/api/lead").contentType("application/x-www-form-urlencoded")
                        .param("name", "Lead Test").param("phone", "069 111 2233").param("wantedItem", "RTX 3070").param("source", "FORM"))
                .andExpect(status().isOk());
        Long leadId = leads.latestId();
        assertEquals("RTX 3070", leads.findById(leadId).orElseThrow().getWantedItem());

        mvc.perform(get("/api/v1/summary").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestBuildRequestId").value(buildId))
                .andExpect(jsonPath("$.latestLeadId").value(leadId))
                .andExpect(jsonPath("$.latestWishId").hasJsonPath())
                .andExpect(jsonPath("$.latestTradeId").hasJsonPath());
        mvc.perform(get("/api/v1/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aPrebuiltIsBuiltFromComponentsWithTheGpuFromTheCatalogue() throws Exception {
        Long gpuEntry = specCatalog.findByGpuCatalogId(gpuCatalog.findBySlug("rtx-3060-ti").orElseThrow().getId()).orElseThrow().getId();
        mvc.perform(get("/admin/api/spec-catalog/search").param("category", "gpu").param("q", "3060ti"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(gpuEntry))
                .andExpect(jsonPath("$[0].name").value("GeForce RTX 3060 Ti"));
        mvc.perform(get("/admin/api/spec-catalog/" + gpuEntry)).andExpect(status().isOk())
                .andExpect(jsonPath("$.values.vram_gb").value("8"));
        mvc.perform(get("/admin/api/spec-catalog/search").param("category", "cpu").param("q", "i7"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));

        String title = "PC Gaming i7 " + UUID.randomUUID();
        mvc.perform(post("/admin/products").with(csrf())
                        .param("title", title).param("categorySlug", "pc-te-gatshem").param("condition", "USED")
                        .param("priceLek", "85000").param("costLek", String.valueOf(SECRET_COST)).param("quantity", "1").param("status", "ACTIVE")
                        .param("prebuilt.cpuName", "Intel Core i7-8700").param("prebuilt.cpuCores", "6").param("prebuilt.cpuThreads", "12")
                        .param("prebuilt.gpuCatalogId", gpuEntry.toString()).param("prebuilt.ramGb", "16").param("prebuilt.ramConfig", "2x8GB")
                        .param("prebuilt.storagePrimary", "500GB NVMe").param("prebuilt.psuWatts", "650").param("prebuilt.osInstalled", "true")
                        .param("prebuilt.osName", "Windows 11").param("prebuilt.loadTempCpuC", "71").param("prebuilt.loadTempGpuC", "68")
                        .param("prebuilt.testedAt", "2026-10-01").param("prebuilt.notesSq", "Fortnite 1080p Epic ~140 FPS"))
                .andExpect(status().is3xxRedirection());
        Product pc = products.findAll().stream().filter(x -> x.getTitle().equals(title)).findFirst().orElseThrow();
        assertNull(tx.execute(s -> products.findById(pc.getId()).orElseThrow().getGpuModel()), "a prebuilt is not a GPU for the assistant");

        mvc.perform(get("/produkt/" + pc.getSlug())).andExpect(status().isOk())
                .andExpect(content().string(containsString("<th>Karta grafike</th><td>GeForce RTX 3060 Ti</td>")))
                .andExpect(content().string(containsString("Intel Core i7-8700 (6 bërthama / 12 thread-e)")))
                .andExpect(content().string(containsString("Testuar nën ngarkesë")))
                .andExpect(content().string(containsString("71°C")))
                .andExpect(content().string(containsString("01.10.2026")))
                .andExpect(content().string(containsString("Fortnite 1080p Epic ~140 FPS")));
        mvc.perform(get("/kategori/pc-te-gatshem").param("gpu", "GeForce RTX 3060 Ti").param("ram", "16").param("cmimi", "80k-120k"))
                .andExpect(status().isOk()).andExpect(content().string(containsString(pc.getSlug())));
        mvc.perform(get("/kategori/pc-te-gatshem").param("ram", "32"))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString(pc.getSlug()))));

        mvc.perform(post("/admin/products").with(csrf())
                        .param("title", "PC pa të dhëna " + UUID.randomUUID()).param("categorySlug", "pc-te-gatshem").param("condition", "USED")
                        .param("priceLek", "85000").param("costLek", "1").param("quantity", "1").param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Shkruani procesorin e PC-së.")));
        mvc.perform(get("/admin/products/" + pc.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Komponentët e PC-së")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void templateFieldsAreTypedAndCanSeedTheCatalogue() throws Exception {
        mvc.perform(get("/admin/products/new")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-key=\"vram_gb\"")))
                .andExpect(content().string(containsString("\"pc-te-gatshem\"")));

        String title = "Ryzen 5 5600 " + UUID.randomUUID();
        mvc.perform(post("/admin/products").with(csrf())
                        .param("title", title).param("categorySlug", "procesore").param("condition", "USED")
                        .param("priceLek", "15000").param("costLek", "11000").param("quantity", "1").param("status", "DRAFT")
                        .param("specKeys", "socket", "cores", "base_clock_ghz", "integrated_graphics")
                        .param("specValues", "AM4", "gjashtë", "3,5", "Jo"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("shkruani një numër të plotë")));

        mvc.perform(post("/admin/products").with(csrf())
                        .param("title", title).param("model", "Ryzen 5 5600").param("categorySlug", "procesore").param("condition", "USED")
                        .param("priceLek", "15000").param("costLek", "11000").param("quantity", "1").param("status", "DRAFT")
                        .param("specKeys", "socket", "cores", "base_clock_ghz", "integrated_graphics", "Garancia")
                        .param("specValues", "AM4", "6", "3,5", "Jo", "3 muaj"))
                .andExpect(status().is3xxRedirection());
        Product p = tx.execute(s -> {
            Product x = products.findAll().stream().filter(y -> y.getTitle().equals(title)).findFirst().orElseThrow();
            x.getSpecs().size();
            return x;
        });
        assertEquals(List.of("socket=AM4", "cores=6", "base_clock_ghz=3.5", "integrated_graphics=false", "Garancia=3 muaj"),
                p.getSpecs().stream().map(s -> s.getSpecKey() + "=" + s.getSpecValue()).toList());

        mvc.perform(post("/admin/products/" + p.getId() + "/catalog-entry").with(csrf())).andExpect(status().is3xxRedirection());
        SpecCatalogEntry e = specCatalog.findByCategorySlugOrderByNameAsc("cpu").stream()
                .filter(x -> x.getName().equals("Ryzen 5 5600")).findFirst().orElseThrow();
        assertFalse(e.isVerified());
        assertEquals("6", e.getSpecJson().get("cores"));
        assertFalse(e.getSpecJson().containsKey("Garancia"));
        mvc.perform(get("/admin/api/spec-catalog/search").param("category", "cpu").param("q", "ryzen55600"))
                .andExpect(jsonPath("$[0].name").value("Ryzen 5 5600"));
        mvc.perform(get("/admin/spec-catalog")).andExpect(status().isOk());
        mvc.perform(get("/admin/spec-catalog/" + e.getId())).andExpect(status().isOk());
        mvc.perform(get("/admin/categories")).andExpect(status().isOk());
    }

    @Test
    void categoriesWithNothingInThemStillRender() throws Exception {
        mvc.perform(get("/kategori/pc-te-gatshem")).andExpect(status().isOk());
        mvc.perform(get("/paketa/does-not-exist")).andExpect(status().isNotFound());
    }

    private Product product(String category, int price, ProductStatus status) {
        return tx.execute(s -> {
            Product p = new Product();
            p.setTitle("Test PRD " + UUID.randomUUID());
            p.setSlug("test-" + UUID.randomUUID());
            p.setBrand(brands.findBySlug("msi").orElseThrow());
            p.setCategorySlug(category);
            p.setPriceLek(price);
            p.setCostLek(SECRET_COST);
            p.setQuantity(status == ProductStatus.ACTIVE ? 1 : 0);
            p.changeStatus(ProductStatus.ACTIVE);
            p.changeStatus(status);
            return products.save(p);
        });
    }

    private Combo combo(Product a, Product b, int price) {
        return tx.execute(s -> {
            Combo c = new Combo();
            c.setTitleSq("Kombo " + UUID.randomUUID());
            c.setSlug("kombo-" + UUID.randomUUID());
            c.setComboPriceLek(price);
            c.getItems().add(new al.pcmania.domain.ComboItem(c, products.findById(a.getId()).orElseThrow(), 1));
            c.getItems().add(new al.pcmania.domain.ComboItem(c, products.findById(b.getId()).orElseThrow(), 1));
            return combos.save(c);
        });
    }

    private Product prebuiltProduct() {
        Product p = product("pc-te-gatshem", 90_000, ProductStatus.ACTIVE);
        tx.executeWithoutResult(s -> {
            al.pcmania.domain.PrebuiltSpec spec = new al.pcmania.domain.PrebuiltSpec();
            spec.setProduct(products.findById(p.getId()).orElseThrow());
            spec.setCpuName("Intel Core i5-9400F");
            spec.setRamGb(16);
            spec.setStoragePrimary("512GB NVMe");
            spec.setGpuNameOverride("GTX 1660 Super");
            prebuilts.save(spec);
        });
        return p;
    }

    private static CheckoutForm checkout() {
        CheckoutForm f = new CheckoutForm();
        f.setCustomerName("Test Klient");
        f.setCustomerPhone("069 123 4567");
        f.setCity("Tiranë");
        f.setDeliveryMethod(DeliveryMethod.PICKUP_TIRANA);
        f.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        f.setQuantity(1);
        return f;
    }
}
