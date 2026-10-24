package al.pcmania;

import al.pcmania.domain.Category;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;
import al.pcmania.domain.ProductImage;
import al.pcmania.repo.BrandRepository;
import al.pcmania.repo.CategoryRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.repo.ProductSlugHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.base-url=https://shop.example", "app.contact-email=pronari.personal@gmail.com",
        "app.facebook-url=https://www.facebook.com/pcmania.test", "app.price-range=5000-80000 ALL"})
@AutoConfigureMockMvc
class SeoTests {

    private static final String BASE = "https://shop.example";

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
    @Autowired CategoryRepository categories;
    @Autowired ProductSlugHistoryRepository slugHistory;
    @Autowired TransactionTemplate tx;
    @Autowired ObjectMapper json;

    @Test
    void aCategoryPageHasItsNameAsTheHeadingAnIntroAndStructuredData() throws Exception {
        Product monitor = product("monitore", "Monitor Test " + UUID.randomUUID(), 3);
        Category c = show("monitore");
        assertEquals("Monitorë të Rinj dhe të Përdorur në Tiranë | PCMania", c.getMetaTitle(), "seeded for the masculine plural");

        mvc.perform(post("/admin/categories/" + c.getId() + "/seo").with(csrf()).with(user("admin").roles("ADMIN"))
                        .param("metaTitle", c.getMetaTitle()).param("metaDescription", "")
                        .param("introHtml", "<p>Monitorë <strong>të testuar</strong>, shih edhe <a href=\"/kategori/karta-grafike\" onclick=\"x()\">kartat</a>.</p>"
                                + "<script>alert(1)</script><img src=x onerror=alert(1)><h2>Titull</h2>"))
                .andExpect(status().is3xxRedirection());

        Document page = page("/kategori/monitore");
        assertEquals(1, page.select("h1").size());
        assertEquals("Monitorë", page.selectFirst("h1").text());
        assertTrue(page.select("h1 small, h1 span").isEmpty(), "the product count is outside the heading");

        Element intro = page.selectFirst("section.category-intro");
        assertNotNull(intro, "intro is rendered");
        assertEquals("të testuar", intro.selectFirst("strong").text());
        assertEquals("/kategori/karta-grafike", intro.selectFirst("a").attr("href"));
        assertFalse(intro.selectFirst("a").hasAttr("onclick"));
        assertTrue(intro.select("script, img, h2").isEmpty(), intro.html());
        assertTrue(page.selectFirst("h1").parent().nextElementSibling().is("section.category-intro"), "intro sits right under the heading");

        assertEquals("Monitorë të Rinj dhe të Përdorur në Tiranë | PCMania", page.title());
        assertEquals(BASE + "/kategori/monitore", page.selectFirst("link[rel=canonical]").attr("href"));
        String description = page.selectFirst("meta[name=description]").attr("content");
        assertTrue(description.length() <= 155 && !description.contains("…"), description);

        JsonNode ld = jsonLd(page);
        assertEquals(List.of("CollectionPage", "ItemList", "BreadcrumbList"), types(ld));
        JsonNode collection = ld.get(0);
        assertEquals("Monitorë", collection.path("name").asText());
        assertEquals(description, collection.path("description").asText());
        assertEquals(BASE + "/kategori/monitore", collection.path("url").asText());
        JsonNode items = ld.get(1).path("itemListElement");
        assertTrue(items.size() >= 1);
        JsonNode first = null;
        for (JsonNode i : items) {
            assertEquals("ListItem", i.path("@type").asText());
            assertTrue(i.path("position").asInt() >= 1);
            assertTrue(i.path("url").asText().startsWith(BASE + "/produkt/"));
            assertFalse(i.path("name").asText().isBlank());
            if (i.path("url").asText().endsWith(monitor.getSlug())) first = i;
        }
        assertNotNull(first, "the listed product is in the ItemList");
        assertTrue(first.path("image").asText().startsWith(BASE + "/img/p/full/"), first.toString());
        JsonNode crumbs = ld.get(2).path("itemListElement");
        assertEquals(2, crumbs.size());
        assertEquals(BASE + "/", crumbs.get(0).path("item").asText());
        assertEquals(BASE + "/kategori/monitore", crumbs.get(1).path("item").asText());
    }

    @Test
    void categoriesWithoutTheirOwnTitleGetTheGeneratedOne() throws Exception {
        Document page = page("/kategori/karta-grafike");
        assertEquals("Karta grafike të Reja dhe të Përdorura në Tiranë | PCMania", page.title());
        assertTrue(page.title().length() <= 60);

        Category c = show("kasa");
        mvc.perform(post("/admin/categories/" + c.getId() + "/seo").with(csrf()).with(user("admin").roles("ADMIN"))
                        .param("metaTitle", "Kasa kompjuteri në Tiranë | PCMania").param("metaDescription", "Kasa ATX dhe Micro-ATX.")
                        .param("introHtml", "  "))
                .andExpect(status().is3xxRedirection());
        Document kasa = page("/kategori/kasa");
        assertEquals("Kasa kompjuteri në Tiranë | PCMania", kasa.title());
        assertEquals("Kasa ATX dhe Micro-ATX.", kasa.selectFirst("meta[name=description]").attr("content"));
        assertNull(kasa.selectFirst("section.category-intro"), "an empty intro renders nothing");
        mvc.perform(get("/admin/categories/" + c.getId() + "/seo").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("data-limit=\"155\"")));
    }

    @Test
    void productDescriptionsLeadWithTheOfferAndFitInGoogle() throws Exception {
        Product p = product("karta-grafike", "RTX 2060 Super 8GB " + UUID.randomUUID().toString().substring(0, 6), 0);
        tx.executeWithoutResult(s -> {
            Product x = products.findById(p.getId()).orElseThrow();
            x.setShortDescription("GeForce RTX 2060 Super (Turing TU106, 2019) – 8 GB GDDR6, TDP 175 W, PSU min. 550 W. Mbështet DLSS 4, "
                    + "FSR 2, ray tracing. Lojëra AAA ~72 FPS në 1080p, ~48 FPS në 1440p, esports mbi 200 FPS në shumicën e titujve.");
            x.setWarrantyDays(90);
            x.setTransportIncluded(true);
        });

        Document page = page("/produkt/" + p.getSlug());
        String description = page.selectFirst("meta[name=description]").attr("content");
        assertTrue(description.startsWith(p.getTitle() + " (e përdorur) – 14.500 Lekë."), description);
        assertTrue(description.length() >= 120 && description.length() <= 155, description.length() + ": " + description);
        assertTrue(description.endsWith("."), description);
        assertFalse(description.contains("…"), description);
        assertSameEverywhere(page, description);

        tx.executeWithoutResult(s -> products.findById(p.getId()).orElseThrow()
                .setMetaDescription("Kartë e testuar për 1080p, me garanci 3 muaj. Pagesë në dorëzim."));
        Document custom = page("/produkt/" + p.getSlug());
        assertSameEverywhere(custom, "Kartë e testuar për 1080p, me garanci 3 muaj. Pagesë në dorëzim.");

        JsonNode product = jsonLd(custom).get(0);
        assertEquals("Product", product.path("@type").asText());
        assertFalse(product.path("name").asText().isBlank());
        assertTrue(product.path("image").isArray() && product.path("image").size() > 0);
        JsonNode offer = product.path("offers");
        assertEquals("ALL", offer.path("priceCurrency").asText());
        assertEquals(14_500, offer.path("price").asInt());
        assertTrue(offer.path("availability").asText().startsWith("https://schema.org/"));
        assertTrue(offer.path("itemCondition").asText().startsWith("https://schema.org/"));
        assertEquals(BASE + "/produkt/" + p.getSlug(), offer.path("url").asText());
    }

    @Test
    void theAdminCanWriteTheGoogleDescription() throws Exception {
        Product p = product("kasa", "Kase Test " + UUID.randomUUID(), 0);
        mvc.perform(post("/admin/products/" + p.getId()).with(csrf()).with(user("admin").roles("ADMIN"))
                        .param("title", p.getTitle()).param("categorySlug", "kasa").param("condition", "NEW")
                        .param("priceLek", "14500").param("costLek", "9000").param("quantity", "1").param("status", "ACTIVE")
                        .param("slug", p.getSlug()).param("metaDescription", "  Kasë e re me airflow të mirë.  "))
                .andExpect(status().is3xxRedirection());
        assertEquals("Kasë e re me airflow të mirë.", products.findById(p.getId()).orElseThrow().getMetaDescription());
        mvc.perform(get("/admin/products/" + p.getId()).with(user("admin").roles("ADMIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("e re me airflow t")));
    }

    @Test
    void aRenamedProductAnswersOnItsOldAddressWithA301() throws Exception {
        show("kasa");
        Product p = product("kasa", "Kase Rename " + UUID.randomUUID(), 0);
        String a = p.getSlug(), b = a + "-b", c = a + "-c";

        rename(p, b);
        mvc.perform(get("/produkt/" + a)).andExpect(status().isMovedPermanently()).andExpect(redirectedUrl("/produkt/" + b));
        mvc.perform(get("/produkt/" + b)).andExpect(status().isOk());

        rename(p, c);
        mvc.perform(get("/produkt/" + a)).andExpect(status().isMovedPermanently()).andExpect(redirectedUrl("/produkt/" + c));
        mvc.perform(get("/produkt/" + b)).andExpect(status().isMovedPermanently()).andExpect(redirectedUrl("/produkt/" + c));

        rename(p, a);
        mvc.perform(get("/produkt/" + a)).andExpect(status().isOk());
        mvc.perform(get("/produkt/" + c)).andExpect(status().isMovedPermanently()).andExpect(redirectedUrl("/produkt/" + a));
        assertTrue(slugHistory.findByOldSlug(a).isEmpty(), "the current slug is not also an old one");

        mvc.perform(get("/produkt/nuk-ekziston-" + UUID.randomUUID())).andExpect(status().isNotFound());

        tx.executeWithoutResult(s -> products.findById(p.getId()).orElseThrow().changeStatus(ProductStatus.DRAFT));
        mvc.perform(get("/produkt/" + c)).andExpect(status().isNotFound());
    }

    @Test
    void everyImageHasAnAltAndDecorativeOnesSaySo() throws Exception {
        Product p = product("karta-grafike", "Alt Test " + UUID.randomUUID(), 3);
        for (String path : List.of("/produkt/" + p.getSlug(), "/kategori/karta-grafike", "/")) {
            Document page = page(path);
            for (Element img : page.select("img")) {
                assertTrue(img.hasAttr("alt"), "no alt on " + img + " in " + path);
                if (img.attr("alt").isEmpty()) {
                    assertEquals("true", img.attr("aria-hidden"), "empty alt must be marked decorative: " + img + " in " + path);
                }
            }
        }
        Document page = page("/produkt/" + p.getSlug());
        List<String> alts = page.select(".gallery-thumbs img").eachAttr("alt");
        assertEquals(3, alts.size());
        assertTrue(alts.stream().allMatch(alt -> alt.startsWith(p.getTitle() + " – foto ")), alts.toString());
    }

    @Test
    void thePersonalEmailIsNeverPublished() throws Exception {
        Product p = product("karta-grafike", "Email Test " + UUID.randomUUID(), 0);
        for (String path : List.of("/", "/kontakt", "/kushtet-e-perdorimit", "/rreth-nesh", "/transporti-dhe-pagesa",
                "/kategori/karta-grafike", "/produkt/" + p.getSlug())) {
            String body = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("gmail.com") || body.contains("pronari.personal"), "personal e-mail in " + path);
        }

        JsonNode store = jsonLd(page("/"));
        assertEquals("Store", store.path("@type").asText());
        assertTrue(store.path("email").isMissingNode());
        assertEquals("https://www.facebook.com/pcmania.test", store.path("sameAs").get(0).asText());
        assertEquals("5000-80000 ALL", store.path("priceRange").asText());
        assertEquals("Country", store.path("areaServed").path("@type").asText());
        assertEquals("Albania", store.path("areaServed").path("name").asText());
    }

    @Test
    void everyPublicPageHasOneHeadingAndAnAbsoluteCanonical() throws Exception {
        Product p = product("karta-grafike", "Heading Test " + UUID.randomUUID(), 1);
        for (String path : List.of("/", "/kategori/karta-grafike", "/kategori/pc-te-gatshem", "/produkt/" + p.getSlug(),
                "/kerko?q=test", "/kontakt", "/rreth-nesh", "/transporti-dhe-pagesa", "/kushtet-e-perdorimit", "/nderro",
                "/pc-me-porosi", "/kerko-produkt", "/se-shpejti")) {
            Document page = page(path);
            assertEquals(1, page.select("h1").size(), "h1 count on " + path);
            String canonical = page.selectFirst("link[rel=canonical]").attr("href");
            assertTrue(canonical.startsWith(BASE + "/"), path + " → " + canonical);
        }
    }

    @Test
    void theSitemapListsWhatIsForSaleAndLeavesSoldPagesReachable() throws Exception {
        Product active = product("karta-grafike", "Sitemap Active " + UUID.randomUUID(), 0);
        Product sold = product("karta-grafike", "Sitemap Sold " + UUID.randomUUID(), 0);
        tx.executeWithoutResult(s -> products.findById(sold.getId()).orElseThrow().changeStatus(ProductStatus.SOLD));

        String sitemap = mvc.perform(get("/sitemap.xml")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(sitemap.contains(BASE + "/produkt/" + active.getSlug()));
        assertFalse(sitemap.contains(sold.getSlug()));
        for (Category c : categories.findByVisibleTrueOrderBySortOrderAsc()) {
            assertTrue(sitemap.contains(BASE + "/kategori/" + c.getSlug()), c.getSlug());
        }
        Document soldPage = page("/produkt/" + sold.getSlug());
        assertTrue(soldPage.selectFirst("meta[name=description]").attr("content").startsWith("E shitur: "));
    }

    private Category show(String slug) throws Exception {
        Category c = categories.findBySlug(slug).orElseThrow();
        mvc.perform(post("/admin/categories/" + c.getId() + "/visible").with(csrf()).with(user("admin").roles("ADMIN"))
                .param("value", "true")).andExpect(status().is3xxRedirection());
        return c;
    }

    private void rename(Product p, String slug) throws Exception {
        mvc.perform(post("/admin/products/" + p.getId()).with(csrf()).with(user("admin").roles("ADMIN"))
                        .param("title", p.getTitle()).param("categorySlug", p.getCategorySlug()).param("condition", "USED")
                        .param("priceLek", "14500").param("costLek", "9000").param("quantity", "1").param("status", "ACTIVE")
                        .param("slug", slug))
                .andExpect(status().is3xxRedirection());
    }

    private void assertSameEverywhere(Document page, String description) throws Exception {
        assertEquals(description, page.selectFirst("meta[name=description]").attr("content"));
        assertEquals(description, page.selectFirst("meta[property=og:description]").attr("content"));
        assertEquals(description, page.selectFirst("meta[name=twitter:description]").attr("content"));
        assertEquals(description, jsonLd(page).get(0).path("description").asText());
    }

    private Document page(String path) throws Exception {
        String html = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return Jsoup.parse(html, BASE + path);
    }

    private JsonNode jsonLd(Document page) throws Exception {
        Element script = page.selectFirst("script[type=application/ld+json]");
        assertNotNull(script, "no JSON-LD on " + page.location());
        return json.readTree(script.data());
    }

    private static List<String> types(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.path("@type").asText()));
        return out;
    }

    private Product product(String category, String title, int images) {
        return tx.execute(s -> {
            Product p = new Product();
            p.setTitle(title);
            p.setSlug("seo-" + UUID.randomUUID());
            p.setBrand(brands.findBySlug("msi").orElseThrow());
            p.setCategorySlug(category);
            p.setPriceLek(14_500);
            p.setCostLek(9_000);
            p.setQuantity(1);
            p.changeStatus(ProductStatus.ACTIVE);
            for (int i = 0; i < images; i++) p.getImages().add(new ProductImage(p, UUID.randomUUID() + ".jpg", i, i == 0));
            return products.save(p);
        });
    }
}
