package al.pcmania;

import al.pcmania.domain.Product;
import al.pcmania.repo.ProductRepository;
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

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OriginalBoxTests {

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

    private Product createUsedProductWithBox(String box) throws Exception {
        return createProductWithBox("USED", box);
    }

    private Product createProductWithBox(String condition, String box) throws Exception {
        String title = "Kuti test " + UUID.randomUUID();
        mvc.perform(post("/admin/products").with(csrf())
                        .param("title", title).param("categorySlug", "karta-grafike").param("condition", condition)
                        .param("priceLek", "25000").param("costLek", "20000").param("quantity", "1")
                        .param("status", "ACTIVE").param("hasOriginalBox", box))
                .andExpect(status().is3xxRedirection());
        return products.findAll().stream().filter(p -> p.getTitle().equals(title)).findFirst().orElseThrow();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aUsedProductRemembersThatItHasItsBox() throws Exception {
        assertEquals(Boolean.TRUE, createUsedProductWithBox("true").getHasOriginalBox());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aUsedProductRemembersThatItHasNoBox() throws Exception {
        assertEquals(Boolean.FALSE, createUsedProductWithBox("false").getHasOriginalBox());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anUnansweredBoxStaysUnknown() throws Exception {
        assertNull(createUsedProductWithBox("").getHasOriginalBox());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aNewProductAlsoRemembersItsBox() throws Exception {
        assertEquals(Boolean.TRUE, createProductWithBox("NEW", "true").getHasOriginalBox());
        assertEquals(Boolean.FALSE, createProductWithBox("NEW", "false").getHasOriginalBox());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anOpenBoxProductAlsoRemembersItsBox() throws Exception {
        assertEquals(Boolean.TRUE, createProductWithBox("OPEN_BOX", "true").getHasOriginalBox());
    }
}
