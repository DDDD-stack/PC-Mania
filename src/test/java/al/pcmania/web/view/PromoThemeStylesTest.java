package al.pcmania.web.view;

import al.pcmania.domain.Enums.PromoTheme;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromoThemeStylesTest {

    @Test
    void everyThemeHasItsColoursOnTheShopAndInTheAdmin() throws IOException {
        String site = css("/static/css/site.css");
        String admin = css("/static/css/admin.css");
        for (PromoTheme t : PromoTheme.values()) {
            assertTrue(site.contains(".promo-theme-" + t.name() + ","), "site.css is missing .promo-theme-" + t.name());
            assertTrue(site.contains(".sale-theme-" + t.name() + " {"), "site.css is missing .sale-theme-" + t.name());
            assertTrue(admin.contains(".promo-theme-" + t.name() + " {"), "admin.css is missing .promo-theme-" + t.name());
        }
    }

    private String css(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
