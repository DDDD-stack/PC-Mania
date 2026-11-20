package al.pcmania;

import al.pcmania.domain.Combo;
import al.pcmania.domain.ComboItem;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Order;
import al.pcmania.domain.Product;
import al.pcmania.domain.Promotion;
import al.pcmania.repo.BrandRepository;
import al.pcmania.repo.ComboRepository;
import al.pcmania.repo.OrderRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.repo.PromotionRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PromotionTests {

    private static final EmbeddedPostgres PG = LocalPostgres.startTemporary();
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final int COST = 19_137;
    private static final AtomicInteger ORDERS = new AtomicInteger();

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
    @Autowired PromotionRepository promotions;
    @Autowired ComboRepository combos;
    @Autowired OrderRepository orders;
    @Autowired BrandRepository brands;
    @Autowired TransactionTemplate tx;

    @Test
    @WithMockUser(roles = "ADMIN")
    void aLiveCutIsShownEverywhereAndIsWhatTheOrderCharges() throws Exception {
        Product p = product(30_000);
        Promotion promo = createPromotion("Black Friday " + UUID.randomUUID(), "20", null, LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(3), p);

        mvc.perform(get("/produkt/" + p.getSlug())).andExpect(status().isOk())
                .andExpect(content().string(containsString("24.000 Lekë")))
                .andExpect(content().string(containsString("30.000 Lekë")))
                .andExpect(content().string(containsString("sale-ribbon")))
                .andExpect(content().string(containsString("Kurseni")))
                .andExpect(content().string(containsString("\"price\":24000")))
                .andExpect(content().string(containsString("https://schema.org/StrikethroughPrice")))
                .andExpect(content().string(containsString("\"priceValidUntil\"")))
                .andExpect(content().string(not(containsString("19.137"))))
                .andExpect(content().string(not(containsString(String.valueOf(COST)))));

        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-promo-bar=\"" + promo.getId() + "\"")))
                .andExpect(content().string(containsString("deri -20%")))
                .andExpect(content().string(containsString("promo-stage")))
                .andExpect(content().string(containsString("-20%")));

        mvc.perform(get("/oferta")).andExpect(status().isOk())
                .andExpect(content().string(containsString(p.getTitle())))
                .andExpect(content().string(not(containsString("noindex"))))
                .andExpect(content().string(not(containsString("19.137"))));

        mvc.perform(get("/porosit/" + p.getSlug())).andExpect(status().isOk())
                .andExpect(content().string(containsString("24.000 Lekë")))
                .andExpect(content().string(containsString("Kurseni")));

        Order o = placeOrder(p);
        assertEquals(24_000, o.getItems().getFirst().getPriceLekSnapshot());
        assertEquals(24_000, o.getSubtotalLek());

        mvc.perform(get("/sitemap.xml")).andExpect(content().string(containsString("/oferta</loc>")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aScheduledCutChangesNothingYet() throws Exception {
        Product p = product(40_000);
        createPromotion("Nesër " + UUID.randomUUID(), "25", null, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3), p);

        mvc.perform(get("/produkt/" + p.getSlug())).andExpect(status().isOk())
                .andExpect(content().string(containsString("\"price\":40000")))
                .andExpect(content().string(not(containsString("StrikethroughPrice"))))
                .andExpect(content().string(not(containsString("sale-ribbon"))));
        assertEquals(40_000, placeOrder(p).getItems().getFirst().getPriceLekSnapshot());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void endingOrPausingACutRestoresThePriceImmediately() throws Exception {
        Product paused = product(20_000);
        Product ended = product(22_000);
        Promotion a = createPromotion("Pauzë " + UUID.randomUUID(), "10", null, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(1), paused);
        Promotion b = createPromotion("Fund " + UUID.randomUUID(), "10", null, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(1), ended);
        mvc.perform(get("/produkt/" + paused.getSlug())).andExpect(content().string(containsString("\"price\":18000")));
        mvc.perform(get("/produkt/" + ended.getSlug())).andExpect(content().string(containsString("\"price\":19800")));

        mvc.perform(post("/admin/promotions/" + a.getId() + "/toggle").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/promotions/" + b.getId() + "/end").with(csrf())).andExpect(status().is3xxRedirection());

        mvc.perform(get("/produkt/" + paused.getSlug())).andExpect(content().string(containsString("\"price\":20000")))
                .andExpect(content().string(not(containsString("sale-ribbon"))));
        mvc.perform(get("/produkt/" + ended.getSlug())).andExpect(content().string(containsString("\"price\":22000")))
                .andExpect(content().string(not(containsString("sale-ribbon"))));
        assertEquals(20_000, placeOrder(paused).getItems().getFirst().getPriceLekSnapshot());
        assertEquals(22_000, placeOrder(ended).getItems().getFirst().getPriceLekSnapshot());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aCutMustActuallyLowerThePrice() throws Exception {
        Product p = product(25_000);
        mvc.perform(send("Jo ofertë " + UUID.randomUUID(), "10", LocalDateTime.now(), LocalDateTime.now().plusDays(1), p)
                        .param("fixed_" + p.getId(), "26000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("duhet të jetë më i ulët")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sellingBelowCostIsAllowedButTheAdminIsWarned() throws Exception {
        Product p = product(25_000);
        mvc.perform(send("Nën kosto " + UUID.randomUUID(), "10", LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1), p)
                        .param("fixed_" + p.getId(), "18000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("warning", containsString("nën kosto")));
        assertEquals(18_000, placeOrder(p).getItems().getFirst().getPriceLekSnapshot());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aComboThatSalesMadePointlessIsHidden() throws Exception {
        Product gpu = product(30_000);
        Product psu = product(10_000);
        Combo combo = tx.execute(s -> {
            Combo c = new Combo();
            c.setTitleSq("Paketë oferte " + UUID.randomUUID());
            c.setSlug("paketa-oferte-" + UUID.randomUUID());
            c.setComboPriceLek(36_000);
            c.getItems().add(new ComboItem(c, products.findById(gpu.getId()).orElseThrow(), 1));
            c.getItems().add(new ComboItem(c, products.findById(psu.getId()).orElseThrow(), 1));
            return combos.save(c);
        });
        mvc.perform(get("/oferta")).andExpect(content().string(containsString(combo.getTitleSq())));

        createPromotion("Prerje e thellë " + UUID.randomUUID(), "30", null, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1), gpu);

        mvc.perform(get("/oferta")).andExpect(content().string(not(containsString(combo.getTitleSq()))));
        mvc.perform(get("/paketa/" + combo.getSlug())).andExpect(content().string(containsString("nuk është më e disponueshme")));
        mvc.perform(get("/admin/promotions")).andExpect(status().isOk())
                .andExpect(content().string(containsString("pjesët veç e veç kushtojnë më pak se paketa")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void theAdminPagesRender() throws Exception {
        Product p = product(15_000);
        Promotion promo = createPromotion("Faqja e adminit " + UUID.randomUUID(), "15", "Deri -15% sot!", LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusDays(1), p);
        mvc.perform(get("/admin/promotions")).andExpect(status().isOk())
                .andExpect(content().string(containsString(promo.getName())))
                .andExpect(content().string(containsString("Live tani")));
        String fresh = mvc.perform(get("/admin/promotions/new").param("product", p.getId().toString())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(Pattern.compile("name=\"productId\" value=\"" + p.getId() + "\"[^>]*checked=\"checked\"").matcher(fresh).find(),
                "the product the admin came from is ticked");
        mvc.perform(get("/admin/promotions/" + promo.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Deri -15% sot!")));
        mvc.perform(get("/admin/combos")).andExpect(redirectedUrl("/admin/promotions#paketat"));
        mvc.perform(get("/admin/promotions")).andExpect(content().string(containsString("id=\"paketat\"")));
    }

    private Promotion createPromotion(String name, String percent, String headline, LocalDateTime from, LocalDateTime to, Product p) throws Exception {
        var request = send(name, percent, from, to, p);
        if (headline != null) request.param("headline", headline);
        mvc.perform(request).andExpect(status().is3xxRedirection());
        return promotions.findAll().stream().filter(x -> x.getName().equals(name)).findFirst().orElseThrow();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder send(String name, String percent, LocalDateTime from,
                                                                                           LocalDateTime to, Product p) {
        return post("/admin/promotions").with(csrf())
                .param("name", name).param("discountType", "PERCENT").param("discountValue", percent)
                .param("startsAt", INPUT.format(from)).param("endsAt", INPUT.format(to))
                .param("theme", "BLACK").param("showBanner", "true").param("active", "true")
                .param("productId", p.getId().toString());
    }

    private Order placeOrder(Product p) throws Exception {
        String address = "10.24.0." + ORDERS.incrementAndGet();
        ResultActions r = mvc.perform(post("/porosit/" + p.getSlug())
                .with(request -> { request.setRemoteAddr(address); return request; })
                .param("customerName", "Test Klient").param("customerPhone", "069 123 4567").param("city", "Tiranë")
                .param("deliveryMethod", "PICKUP_TIRANA").param("paymentMethod", "CASH_ON_DELIVERY").param("quantity", "1"));
        r.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/porosit/sukses"));
        return tx.execute(s -> {
            Order o = orders.findAll().stream()
                    .filter(x -> x.getItems().stream().anyMatch(i -> i.getProduct() != null && i.getProduct().getId().equals(p.getId())))
                    .reduce((first, second) -> second).orElseThrow();
            o.getItems().size();
            return o;
        });
    }

    private Product product(int price) {
        return tx.execute(s -> {
            Product p = new Product();
            p.setTitle("Promo PRD " + UUID.randomUUID());
            p.setSlug("promo-" + UUID.randomUUID());
            p.setBrand(brands.findBySlug("msi").orElseThrow());
            p.setCategorySlug("karta-grafike");
            p.setPriceLek(price);
            p.setCostLek(COST);
            p.setQuantity(3);
            p.changeStatus(ProductStatus.ACTIVE);
            return products.save(p);
        });
    }
}
