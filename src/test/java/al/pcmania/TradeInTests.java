package al.pcmania;

import al.pcmania.domain.Enums.ContactMethod;
import al.pcmania.domain.Enums.OrderStatus;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Enums.TradeMediaType;
import al.pcmania.domain.Enums.TradeStatus;
import al.pcmania.domain.Order;
import al.pcmania.domain.Product;
import al.pcmania.domain.TradeRequest;
import al.pcmania.repo.BrandRepository;
import al.pcmania.repo.OrderRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.repo.TradeRequestRepository;
import al.pcmania.service.FileStorage;
import al.pcmania.service.TradeMedia;
import al.pcmania.service.TradeService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TradeInTests {

    private static final EmbeddedPostgres PG = LocalPostgres.startTemporary();

    private static final int SECRET_CAP = 4_321_987;

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
    @Autowired OrderRepository orders;
    @Autowired TradeRequestRepository trades;
    @Autowired TradeService tradeService;
    @Autowired FileStorage files;
    @Autowired TransactionTemplate tx;

    @Test
    void aTradeRequestIsAQuoteNotAnOrder() throws Exception {
        Product p = tradeProduct(1);
        long ordersBefore = orders.count();

        String location = mvc.perform(form(p).file(jpeg()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        String number = numberFrom(location);
        assertTrue(number.startsWith("TR-" + Year.now().getValue() + "-"), number);

        TradeRequest t = trades.findByRequestNumber(number).orElseThrow();
        assertEquals(TradeStatus.NEW, t.getStatus());
        assertEquals(TradeMediaType.IMAGE, t.getMediaType());
        assertNotNull(t.getMediaFilename());
        assertEquals("069 123 4567", t.getCustomerPhone());
        assertNull(t.getCustomerEmail());

        assertEquals(ordersBefore, orders.count());
        Product after = products.findById(p.getId()).orElseThrow();
        assertEquals(1, after.getQuantity());
        assertEquals(ProductStatus.ACTIVE, after.getStatus());

        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(content().string(containsString(number)));
    }

    @Test
    void exactlyOneContactIsKeptAndItMustMatchTheChosenMethod() throws Exception {
        Product p = tradeProduct(1);
        long before = trades.count();

        mvc.perform(form(p, "EMAIL").file(jpeg()).param("customerPhone", "069 123 4567").param("customerEmail", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Shkruani një email të vlefshëm")));
        assertEquals(before, trades.count());

        String location = mvc.perform(form(p, "EMAIL").file(jpeg()).param("customerPhone", "069 123 4567").param("customerEmail", "klient@example.com"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        TradeRequest t = trades.findByRequestNumber(numberFrom(location)).orElseThrow();
        assertEquals(ContactMethod.EMAIL, t.getContactMethod());
        assertEquals("klient@example.com", t.getCustomerEmail());
        assertNull(t.getCustomerPhone());
    }

    @Test
    void proofIsRequiredUnlessItComesByWhatsapp() throws Exception {
        Product p = tradeProduct(1);
        long before = trades.count();
        mvc.perform(form(p)).andExpect(status().isOk())
                .andExpect(content().string(containsString("ose zgjidhni ta dërgoni në WhatsApp")));
        assertEquals(before, trades.count());

        String location = mvc.perform(form(p).param("whatsappInstead", "true"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        TradeRequest t = trades.findByRequestNumber(numberFrom(location)).orElseThrow();
        assertTrue(t.isWhatsappInstead());
        assertNull(t.getMediaFilename());
    }

    @Test
    void mediaIsRecognisedByItsBytesNotItsName() throws Exception {
        Product p = tradeProduct(1);

        MockMultipartFile fake = new MockMultipartFile("media", "furmark.mp4", "video/mp4", "not a video at all".getBytes(StandardCharsets.UTF_8));
        mvc.perform(form(p).file(fake)).andExpect(status().isOk())
                .andExpect(content().string(containsString("video MP4/MOV ose foto JPG/PNG")));

        byte[] mp4 = new byte[2048];
        System.arraycopy("\0\0\0\u0018ftypisom".getBytes(StandardCharsets.ISO_8859_1), 0, mp4, 0, 12);
        String location = mvc.perform(form(p).file(new MockMultipartFile("media", "clip.bin", "application/octet-stream", mp4)))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        TradeRequest t = trades.findByRequestNumber(numberFrom(location)).orElseThrow();
        assertEquals(TradeMediaType.VIDEO, t.getMediaType());
        assertEquals("video/mp4", files.meta(t.getMediaFilename()).orElseThrow().getContentType());
    }

    @Test
    void theScriptedFormGetsJsonBack() throws Exception {
        Product p = tradeProduct(1);
        mvc.perform(form(p).file(jpeg()).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.redirect").value(org.hamcrest.Matchers.startsWith("/nderro/derguar?nr=TR-")));
        mvc.perform(form(p, "PHONE").file(jpeg()).param("customerPhone", "abc").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.customerPhone").value("Shkruani një numër telefoni të vlefshëm"));
    }

    @Test
    void onlyEligibleProductsInStockCanBeAskedAbout() throws Exception {
        Product plain = tradeProduct(1);
        tx.executeWithoutResult(s -> products.findById(plain.getId()).orElseThrow().setTradeEligible(false));
        mvc.perform(get("/nderro/" + plain.getSlug())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/produkt/" + plain.getSlug()));
        mvc.perform(form(plain).file(jpeg())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/produkt/" + plain.getSlug()));
    }

    @Test
    void publicPagesShowTheTradeOptionButNeverTheInternalCap() throws Exception {
        Product p = tradeProduct(1);
        String cap = String.valueOf(SECRET_CAP);
        String capFormatted = "4.321.987";
        for (String path : new String[]{"/nderro", "/produkt/" + p.getSlug(), "/nderro/" + p.getSlug(),
                "/kategori/karta-grafike", "/kategori/karta-grafike?nderrim=true", "/"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString(cap))))
                    .andExpect(content().string(not(containsString(capFormatted))));
        }
        mvc.perform(get("/produkt/" + p.getSlug())).andExpect(content().string(containsString("href=\"/nderro/" + p.getSlug() + "\"")));
        mvc.perform(get("/nderro")).andExpect(content().string(containsString(p.getTitle())));
        mvc.perform(get("/sitemap.xml")).andExpect(content().string(containsString("/nderro</loc>")));
    }

    @Test
    void theNderrimFilterShowsOnlyTradeEligibleProducts() throws Exception {
        Product eligible = tradeProduct(1);
        Product plain = tradeProduct(1);
        tx.executeWithoutResult(s -> products.findById(plain.getId()).orElseThrow().setTradeEligible(false));
        mvc.perform(get("/kategori/karta-grafike").param("nderrim", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(eligible.getTitle())))
                .andExpect(content().string(not(containsString(plain.getTitle()))))
                .andExpect(content().string(containsString("Nderrim")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void quoteAcceptConvertAndTakeTheItemIntoStock() throws Exception {
        Product p = tradeProduct(1);
        TradeRequest t = submitted(p);

        mvc.perform(get("/admin/trades")).andExpect(status().isOk()).andExpect(content().string(containsString(t.getRequestNumber())));
        mvc.perform(get("/admin/trades/" + t.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("4.321.987")));
        mvc.perform(get("/admin/trades/" + t.getId() + "/media")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));

        mvc.perform(post("/admin/trades/" + t.getId() + "/quote").with(csrf()).param("quotedValueLek", "12000").param("quoteNotes", "Karta në gjendje të mirë"))
                .andExpect(status().is3xxRedirection());
        TradeRequest quoted = trades.findById(t.getId()).orElseThrow();
        assertEquals(TradeStatus.QUOTED, quoted.getStatus());
        assertEquals(12_000, quoted.getQuotedValueLek());
        assertTrue(quoted.getQuoteExpiresAt().isAfter(LocalDateTime.now().plusDays(6)));
        assertTrue(quoted.isNeedsCall(), "a phone customer must be flagged to call");
        mvc.perform(get("/admin/trades")).andExpect(content().string(containsString("Telefono")));

        mvc.perform(post("/admin/trades/" + t.getId() + "/accept").with(csrf())).andExpect(status().is3xxRedirection());
        assertEquals(TradeStatus.ACCEPTED, trades.findById(t.getId()).orElseThrow().getStatus());

        mvc.perform(post("/admin/trades/" + t.getId() + "/convert").with(csrf())
                        .param("phone", "069 123 4567").param("city", "Durrës")
                        .param("deliveryMethod", "COURIER").param("paymentMethod", "CASH_ON_DELIVERY"))
                .andExpect(status().is3xxRedirection());
        TradeRequest converted = trades.findById(t.getId()).orElseThrow();
        assertEquals(TradeStatus.CONVERTED, converted.getStatus());
        Order o = orders.findWithItems(converted.getOrderId()).orElseThrow();
        assertEquals(t.getId(), o.getTradeRequestId());
        assertEquals(12_000, o.getTradeCreditLek());
        assertEquals(o.getSubtotalLek() + o.getShippingLek() - 12_000, o.getTotalLek());
        assertEquals(50_000 + 500 - 12_000, o.getTotalLek());
        assertEquals(OrderStatus.CONFIRMED, o.getStatus());
        Product reserved = products.findById(p.getId()).orElseThrow();
        assertEquals(0, reserved.getQuantity());
        assertEquals(ProductStatus.RESERVED, reserved.getStatus());
        mvc.perform(get("/admin/orders/" + o.getId())).andExpect(status().isOk()).andExpect(content().string(containsString("−12.000 Lekë")));

        mvc.perform(get("/admin")).andExpect(content().string(containsString("Stok i ardhur nga këmbimet")));
        mvc.perform(post("/admin/trades/" + t.getId() + "/stock").with(csrf())).andExpect(status().is3xxRedirection());
        Product stock = products.findById(trades.findById(t.getId()).orElseThrow().getStockProductId()).orElseThrow();
        assertEquals(ProductStatus.DRAFT, stock.getStatus());
        assertEquals(12_000, stock.getCostLek());
        assertEquals("karta-grafike", stock.getCategorySlug());
        mvc.perform(get("/admin")).andExpect(content().string(not(containsString("Stok i ardhur nga këmbimet"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void quotesAreValidatedAndDeclinesNeedAReason() throws Exception {
        TradeRequest t = submitted(tradeProduct(1));
        mvc.perform(post("/admin/trades/" + t.getId() + "/quote").with(csrf()).param("quotedValueLek", "50000"))
                .andExpect(flash().attribute("error", "Vlera e këmbimit duhet të jetë më e vogël se çmimi i produktit."));
        mvc.perform(post("/admin/trades/" + t.getId() + "/accept").with(csrf()))
                .andExpect(flash().attribute("error", "Vetëm një ofertë e dhënë mund të pranohet."));
        mvc.perform(post("/admin/trades/" + t.getId() + "/decline").with(csrf()).param("reason", ""))
                .andExpect(flash().attribute("error", "Shkruani arsyen e refuzimit."));
        mvc.perform(post("/admin/trades/" + t.getId() + "/decline").with(csrf()).param("reason", "Artefakte në test"))
                .andExpect(status().is3xxRedirection());
        TradeRequest declined = trades.findById(t.getId()).orElseThrow();
        assertEquals(TradeStatus.DECLINED, declined.getStatus());
        assertNotNull(declined.getClosedAt());
    }

    @Test
    void lapsedQuotesExpireAndOldProofIsDeleted() throws Exception {
        TradeRequest lapsing = submitted(tradeProduct(1));
        TradeRequest old = submitted(tradeProduct(1));
        String oldMedia = old.getMediaFilename();
        tx.executeWithoutResult(s -> {
            TradeRequest a = trades.findById(lapsing.getId()).orElseThrow();
            a.setStatus(TradeStatus.QUOTED);
            a.setQuotedValueLek(1000);
            a.setQuoteExpiresAt(LocalDateTime.now().minusMinutes(1));
            TradeRequest b = trades.findById(old.getId()).orElseThrow();
            b.setStatus(TradeStatus.DECLINED);
            b.setClosedAt(LocalDateTime.now().minusDays(31));
        });

        tradeService.housekeeping();

        assertEquals(TradeStatus.EXPIRED, trades.findById(lapsing.getId()).orElseThrow().getStatus());
        assertNull(trades.findById(old.getId()).orElseThrow().getMediaFilename());
        assertFalse(files.exists(oldMedia));
        assertNotNull(trades.findById(lapsing.getId()).orElseThrow().getMediaFilename(), "media is kept for 30 days after closing");
    }

    @Test
    void mediaTypesAreDetected() throws IOException {
        assertEquals(TradeMediaType.VIDEO, TradeMedia.detect(new ByteArrayInputStream("\0\0\0\u0014ftypqt  ".getBytes(StandardCharsets.ISO_8859_1))).type());
        assertEquals("video/quicktime", TradeMedia.detect(new ByteArrayInputStream("\0\0\0\u0014ftypqt  ".getBytes(StandardCharsets.ISO_8859_1))).contentType());
        assertEquals("image/jpeg", TradeMedia.detect(new ByteArrayInputStream(jpeg().getBytes())).contentType());
        assertNull(TradeMedia.detect(new ByteArrayInputStream("hello, world".getBytes(StandardCharsets.UTF_8))));
    }

    private Product tradeProduct(int quantity) {
        return tx.execute(s -> {
            Product p = new Product();
            p.setTitle("Trade GPU " + UUID.randomUUID());
            p.setSlug("trade-" + UUID.randomUUID());
            p.setBrand(brands.findBySlug("msi").orElseThrow());
            p.setCategorySlug("karta-grafike");
            p.setPriceLek(50_000);
            p.setCostLek(40_000);
            p.setQuantity(quantity);
            p.setTradeEligible(true);
            p.setMaxTradeValueLek(SECRET_CAP);
            p.changeStatus(ProductStatus.ACTIVE);
            return products.save(p);
        });
    }

    private MockMultipartHttpServletRequestBuilder form(Product p) {
        MockMultipartHttpServletRequestBuilder b = form(p, "PHONE");
        b.param("customerPhone", "069 123 4567");
        return b;
    }

    private MockMultipartHttpServletRequestBuilder form(Product p, String contactMethod) {
        String ip = "10.9." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        MockMultipartHttpServletRequestBuilder b = multipart("/nderro/" + p.getSlug());
        b.with(request -> { request.setRemoteAddr(ip); return request; });
        b.param("customerName", "Test Klient")
                .param("contactMethod", contactMethod)
                .param("itemType", "GPU")
                .param("manufacturer", "MSI")
                .param("model", "GTX 1660 Super");
        return b;
    }

    private TradeRequest submitted(Product p) throws Exception {
        String location = mvc.perform(form(p).file(jpeg())).andReturn().getResponse().getRedirectedUrl();
        return trades.findByRequestNumber(numberFrom(location)).orElseThrow();
    }

    private static String numberFrom(String location) {
        assertNotNull(location, "expected a redirect");
        Matcher m = Pattern.compile("nr=(TR-\\d{4}-\\d+)").matcher(location);
        assertTrue(m.find(), location);
        return m.group(1);
    }

    private static MockMultipartFile jpeg() throws IOException {
        BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return new MockMultipartFile("media", "gpu-z.jpg", "image/jpeg", out.toByteArray());
    }
}
