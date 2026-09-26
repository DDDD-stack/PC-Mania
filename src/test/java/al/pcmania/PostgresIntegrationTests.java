package al.pcmania;

import al.pcmania.domain.Category;
import al.pcmania.domain.Enums.DeliveryMethod;
import al.pcmania.domain.Enums.OrderStatus;
import al.pcmania.domain.Enums.PaymentMethod;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Order;
import al.pcmania.domain.Product;
import al.pcmania.repo.BrandRepository;
import al.pcmania.repo.CategoryRepository;
import al.pcmania.repo.OrderRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.service.CategoryAdminService;
import al.pcmania.service.ImageStorage;
import al.pcmania.service.OrderService;
import al.pcmania.web.site.CheckoutForm;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Year;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Runs the application against a real Postgres - the database the live shop uses - with the Flyway
 * migrations applied from scratch. SQL that only works on MySQL fails here instead of at a customer's checkout.
 */
@SpringBootTest(properties = {"spring.jpa.properties.hibernate.generate_statistics=true",
        "app.mobile-api-key=" + PostgresIntegrationTests.APP_KEY})
@AutoConfigureMockMvc
class PostgresIntegrationTests {

    static final String APP_KEY = "test-app-key-0123456789abcdef0123456789";

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
    @Autowired OrderService orders;
    @Autowired OrderRepository orderRepo;
    @Autowired ProductRepository products;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired CategoryAdminService categoryAdmin;
    @Autowired TransactionTemplate tx;
    @Autowired EntityManagerFactory emf;
    @Autowired ImageStorage images;

    private Statistics stats;

    @BeforeEach
    void statistics() {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
    }

    @Test
    void orderNumbersCountUpWithinTheYear() {
        Product p = product(5);
        Order first = orders.place(p.getSlug(), checkout(1));
        Order second = orders.place(p.getSlug(), checkout(2));

        String prefix = "PM-" + Year.now().getValue() + "-";
        assertTrue(first.getOrderNumber().startsWith(prefix), first.getOrderNumber());
        assertEquals(number(first) + 1, number(second));
        assertEquals(2, products.findById(p.getId()).orElseThrow().getQuantity());
    }

    @Test
    void checkoutFormPlacesTheOrderAndReservesTheLastUnit() throws Exception {
        Product p = product(1);
        mvc.perform(post("/porosit/" + p.getSlug())
                        .param("customerName", "Test Klient")
                        .param("customerPhone", "069 123 4567")
                        .param("city", "Tiranë")
                        .param("deliveryMethod", "PICKUP_TIRANA")
                        .param("paymentMethod", "CASH_ON_DELIVERY")
                        .param("quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/porosit/sukses"));

        Product after = products.findById(p.getId()).orElseThrow();
        assertEquals(0, after.getQuantity());
        assertEquals(ProductStatus.RESERVED, after.getStatus());
    }

    @Test
    void offlineSaleIsRecordedAsADeliveredOrder() {
        Product p = product(1);
        Order o = orders.recordOfflineSale(p.getId(), 1, 50_000, null, "Facebook");
        assertEquals(OrderStatus.DELIVERED, o.getStatus());
        assertEquals(ProductStatus.SOLD, products.findById(p.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancellingPutsTheUnitBackOnSale() {
        Product p = product(1);
        Order o = orders.place(p.getSlug(), checkout(1));
        orders.changeStatus(o.getId(), OrderStatus.CANCELLED);

        Product after = products.findById(p.getId()).orElseThrow();
        assertEquals(1, after.getQuantity());
        assertEquals(ProductStatus.ACTIVE, after.getStatus());
    }

    @Test
    void publicPagesRender() throws Exception {
        String slug = product(1).getSlug();
        for (String path : new String[]{"/", "/kategori/karta-grafike", "/kategori/karta-grafike?rendit=cmimi-rritje&gjendja=USED",
                "/produkt/" + slug, "/porosit/" + slug, "/pc-me-porosi", "/se-shpejti", "/rreth-nesh", "/kontakt",
                "/transporti-dhe-pagesa", "/kushtet-e-perdorimit", "/sitemap.xml", "/robots.txt"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/produkt/does-not-exist")).andExpect(status().isNotFound());
        mvc.perform(get("/kategori/procesore")).andExpect(status().isNotFound()); // hidden by V3
    }

    @Test
    void adminAndApiRequireAuthentication() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/v1/summary")).andExpect(status().isUnauthorized());
    }

    /** The phone app carries the key instead of signing in; it opens the API and nothing else. */
    @Test
    void theAppKeyOpensTheApiButNotTheWebAdmin() throws Exception {
        mvc.perform(get("/api/v1/summary").header("Authorization", "Bearer " + APP_KEY))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders?group=new").header("Authorization", "Bearer " + APP_KEY))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/summary").header("Authorization", "Bearer " + APP_KEY + "x"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/summary").header("Authorization", "Bearer " + APP_KEY.substring(1)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/admin").header("Authorization", "Bearer " + APP_KEY))
                .andExpect(status().is3xxRedirection());
    }

    /** The navigation is cached; an admin change must still show on the very next page view. */
    @Test
    void categoryChangesShowUpWithoutARestart() throws Exception {
        mvc.perform(get("/kategori/monitore")).andExpect(status().isNotFound()); // hidden by V3, and now cached
        Long id = categories.findBySlug("monitore").orElseThrow().getId();
        categoryAdmin.setVisible(id, true);
        try {
            mvc.perform(get("/kategori/monitore")).andExpect(status().isOk());
        } finally {
            categoryAdmin.setVisible(id, false);
        }
        mvc.perform(get("/kategori/monitore")).andExpect(status().isNotFound());
    }

    @Test
    void repeatPageViewsDoNotReloadTheNavigation() throws Exception {
        String path = "/produkt/" + product(1).getSlug();
        mvc.perform(get(path)).andExpect(status().isOk());
        stats.clear();
        mvc.perform(get(path)).andExpect(status().isOk());
        assertEquals(0, stats.getEntityStatistics(Category.class.getName()).getLoadCount(), "categories loaded");
        long queries = stats.getPrepareStatementCount();
        assertTrue(queries <= 6, "queries for a product page: " + queries);
    }

    @Test
    void uploadsAreStoredInThreeSizesWithoutUpscaling() throws Exception {
        String big = images.store(png(3000, 2000, true));
        assertEquals(1600, width(big, ImageStorage.Size.full));
        assertEquals(800, width(big, ImageStorage.Size.medium));
        assertEquals(400, width(big, ImageStorage.Size.thumb));
        // JPEG has no transparency: the transparent PNG must come out white, not black.
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(images.content(ImageStorage.Size.thumb, big).orElseThrow().getData()));
        assertTrue((thumb.getRGB(2, 2) & 0xffffff) > 0xf0f0f0, "corner should be white");

        String small = images.store(png(300, 200, false));
        for (ImageStorage.Size size : ImageStorage.Size.values()) assertEquals(300, width(small, size));

        mvc.perform(get(ImageStorage.url(ImageStorage.Size.medium, big)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));
    }

    private int width(String filename, ImageStorage.Size size) throws IOException {
        byte[] data = images.content(size, filename).orElseThrow().getData();
        return ImageIO.read(new ByteArrayInputStream(data)).getWidth();
    }

    private static MockMultipartFile png(int w, int h, boolean transparentCorner) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 44, 52));
        g.fillRect(transparentCorner ? w / 4 : 0, transparentCorner ? h / 4 : 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new MockMultipartFile("files", "photo.png", "image/png", out.toByteArray());
    }

    /** A repeat view of a photo is answered from the ETag alone: that is what keeps it off the connection pool. */
    @Test
    void cachedPhotoRequestsDoNotQueryTheDatabase() throws Exception {
        mvc.perform(get("/img/p/thumb/abc.jpg").header("If-None-Match", "\"abc.jpg\""))
                .andExpect(status().isNotModified());
        assertEquals(0, stats.getPrepareStatementCount(), "queries run for a 304 photo response");
    }

    /** Asset links carry a content hash, and both the hashed and the plain paths are served. */
    @Test
    void staticAssetsAreFingerprintedAndServed() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        java.util.regex.Matcher css = java.util.regex.Pattern.compile("/css/site-[0-9a-f]{32}[.]css").matcher(html);
        assertTrue(css.find(), "stylesheet link should carry a content hash");
        mvc.perform(get(css.group())).andExpect(status().isOk());
        for (String path : new String[]{"/css/site.css", "/js/site.js", "/favicon.svg", "/favicon.ico",
                "/apple-touch-icon.png", "/manifest.json", "/images/logo-mark.svg", "/images/og-default.png"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void healthCheckAnswersWithoutTheDatabase() throws Exception {
        mvc.perform(get("/healthz")).andExpect(status().isOk()).andExpect(content().string("ok"));
        assertEquals(0, stats.getPrepareStatementCount(), "queries run for a health check");
    }

    private Product product(int quantity) {
        return tx.execute(s -> {
            Product p = new Product();
            p.setTitle("Test GPU " + UUID.randomUUID());
            p.setSlug("test-" + UUID.randomUUID());
            p.setBrand(brands.findBySlug("msi").orElseThrow());
            p.setCategorySlug("karta-grafike");
            p.setPriceLek(50_000);
            p.setCostLek(40_000);
            p.setQuantity(quantity);
            p.changeStatus(ProductStatus.ACTIVE);
            return products.save(p);
        });
    }

    private static CheckoutForm checkout(int quantity) {
        CheckoutForm f = new CheckoutForm();
        f.setCustomerName("Test Klient");
        f.setCustomerPhone("069 123 4567");
        f.setCity("Tiranë");
        f.setDeliveryMethod(DeliveryMethod.COURIER);
        f.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        f.setQuantity(quantity);
        return f;
    }

    private static int number(Order o) {
        String n = o.getOrderNumber();
        return Integer.parseInt(n.substring(n.lastIndexOf('-') + 1));
    }
}
