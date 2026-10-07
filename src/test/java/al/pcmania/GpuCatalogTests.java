package al.pcmania;

import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.GpuCatalog;
import al.pcmania.domain.Product;
import al.pcmania.repo.BrandRepository;
import al.pcmania.repo.GpuCatalogRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.service.GpuCatalogService;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GpuCatalogTests {

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
    @Autowired GpuCatalogRepository catalog;
    @Autowired GpuCatalogService service;
    @Autowired ProductRepository products;
    @Autowired BrandRepository brands;
    @Autowired TransactionTemplate tx;

    @Test
    void seedLoadsTheCatalogue() {
        assertEquals(21, catalog.count());
        GpuCatalog g = catalog.findBySlug("rtx-3060-ti").orElseThrow();
        assertEquals("GeForce RTX 3060 Ti", g.getName());
        assertEquals(11, g.getTier());
        assertEquals(600, g.getPsuMinWatts());
        assertTrue(g.isRayTracing());
        assertEquals("V4", g.getSupportsDlss().name());
        assertEquals(List.of("3060 ti", "3060ti"), g.aliasList());
        assertTrue(g.missingFields().isEmpty());
    }

    @Test
    void matcherToleratesMissingSpacesAndWholeTitles() {
        assertEquals("rtx-3060-ti", service.search("3060ti", 8).get(0).gpu().getSlug());
        assertEquals("rtx-3060-ti", service.search("RTX 3060 Ti", 8).get(0).gpu().getSlug());
        assertEquals("rtx-3060-ti", service.search("MSI RTX 3060 Ti Ventus 2X 8GB OC", 8).get(0).gpu().getSlug());
        assertEquals("rtx-3060-12gb", service.search("MSI RTX 3060 Ventus 2X 12GB OC", 8).get(0).gpu().getSlug());
        assertEquals("rtx-2060-super", service.search("Gigabyte RTX 2060 Super Windforce", 8).get(0).gpu().getSlug());
        assertEquals("gtx-1660-ti", service.search("Palit GTX 1660 Ti Dual", 8).get(0).gpu().getSlug());
        assertEquals("rtx-3060-12gb", service.search("3060", 8).get(0).gpu().getSlug());
        assertEquals("rx-6600-xt", service.search("rx 6600 xt", 8).get(0).gpu().getSlug());
        assertEquals("gtx-1660-super", service.search("1660s", 8).get(0).gpu().getSlug());
        assertTrue(service.search("x", 8).isEmpty());
        assertTrue(service.search("tastiere", 8).isEmpty());
        assertTrue(service.search("rtx", 8).size() <= 8);

        assertEquals("rtx-2060-super", service.resolve("Gigabyte RTX 2060 Super Windforce").orElseThrow().getSlug());
        assertEquals("rtx-2060", service.resolve("2060").orElseThrow().getSlug());
        assertTrue(service.resolve("a graphics card").isEmpty());
    }

    @Test
    void blurbIsWrittenFromTheRow() {
        String s = GpuCatalogService.shortDescriptionSq(catalog.findBySlug("rx-6750-xt").orElseThrow());
        assertTrue(s.startsWith("Radeon RX 6750 XT (RDNA 2, 2022) – 12 GB GDDR6, TDP 250 W, PSU min. 650 W."), s);
        assertTrue(s.contains("FSR 3, ray tracing"), s);
        assertTrue(s.contains("~100 FPS në 1080p, ~70 FPS në 1440p"), s);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminSearchAndPagesWork() throws Exception {
        mvc.perform(get("/admin/api/gpu-catalog/search").param("q", "3070ti"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("GeForce RTX 3070 Ti"))
                .andExpect(jsonPath("$[0].tier").value(13))
                .andExpect(jsonPath("$[0].vramGb").value(8));
        Long id = catalog.findBySlug("rtx-3070-ti").orElseThrow().getId();
        mvc.perform(get("/admin/api/gpu-catalog/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.psuMinWatts").value(750))
                .andExpect(jsonPath("$.pcieConnectors").value("2x 8-pin"))
                .andExpect(jsonPath("$.shortDescription").value(containsString("GeForce RTX 3070 Ti")));

        mvc.perform(get("/admin/gpu-catalog")).andExpect(status().isOk())
                .andExpect(content().string(containsString("GeForce RTX 3080 10GB")));
        mvc.perform(get("/admin/gpu-catalog/" + id)).andExpect(status().isOk());
        mvc.perform(post("/admin/gpu-catalog/" + id).with(csrf())
                        .param("name", "GeForce RTX 3070 Ti").param("vendor", "NVIDIA").param("tier", "13")
                        .param("psuMinWatts", "700").param("supportsDlss", "V4").param("supportsFsr", "V2")
                        .param("driverStatus", "ACTIVE").param("miningRisk", "HIGH").param("aliases", "3070 ti,3070ti"))
                .andExpect(status().is3xxRedirection());
        assertEquals(700, catalog.findById(id).orElseThrow().getPsuMinWatts());

        mvc.perform(get("/admin/gpu-catalog")).andExpect(content().string(containsString("VRAM, TDP, Konektorët")));
    }

    @Test
    void searchIsAdminOnly() throws Exception {
        mvc.perform(get("/admin/api/gpu-catalog/search").param("q", "3060")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void productFormStampsTheModel() throws Exception {
        Long gpu = catalog.findBySlug("rx-6600").orElseThrow().getId();
        String title = "Sapphire RX 6600 Pulse " + UUID.randomUUID();
        mvc.perform(post("/admin/products").with(csrf())
                        .param("title", title).param("categorySlug", "karta-grafike").param("condition", "USED")
                        .param("priceLek", "25000").param("costLek", "20000").param("quantity", "1").param("status", "ACTIVE")
                        .param("gpuModelId", String.valueOf(gpu))
                        .param("specKeys", "VRAM").param("specValues", "8 GB"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(1));
        Product p = tx.execute(s -> products.findAll().stream().filter(x -> x.getTitle().equals(title)).findFirst()
                .map(x -> { x.getGpuModel().getName(); return x; }).orElseThrow());
        assertEquals("Radeon RX 6600", p.getGpuModel().getName());
        assertTrue(products.findInStockWithGpuModel(ProductStatus.ACTIVE).stream().anyMatch(x -> x.getId().equals(p.getId())));

        mvc.perform(post("/admin/products").with(csrf())
                        .param("title", "Kartë pa model " + UUID.randomUUID()).param("categorySlug", "karta-grafike").param("condition", "USED")
                        .param("priceLek", "25000").param("costLek", "20000").param("quantity", "1").param("status", "DRAFT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("warning", "Pa model nga katalogu, asistenti nuk do ta rekomandojë këtë produkt."));

        mvc.perform(get("/admin/products/" + p.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Radeon RX 6600")));
    }
}
