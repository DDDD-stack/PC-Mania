package al.pcmania.service;

import al.pcmania.config.AppProperties;
import al.pcmania.domain.Category;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MetaTextTest {

    @Test
    void capNeverCutsAWordOrEndsOnAnEllipsis() {
        String long1 = "Karta është në gjendje të shkëlqyer dhe e testuar me Furmark para nisjes, me video të testit dhe foto "
                + "fizike të kartës që të shihni gjendjen e saj përpara se ta blini, pa asnjë surprizë";
        String capped = MetaText.cap(long1, 155);
        assertTrue(capped.length() <= 155, capped);
        assertTrue(capped.endsWith("."));
        assertFalse(capped.contains("…"));
        assertTrue(long1.startsWith(capped.substring(0, capped.length() - 1)), "cut on a word boundary: " + capped);
        assertEquals(' ', long1.charAt(capped.length() - 1));

        String sentences = "Fjalia e parë është mjaft e gjatë që të zërë pjesën më të madhe të hapësirës së lejuar. "
                + "E dyta nuk hyn dot e gjitha brenda kufirit prandaj duhet të hiqet krejt dhe jo të pritet në mes.";
        assertEquals("Fjalia e parë është mjaft e gjatë që të zërë pjesën më të madhe të hapësirës së lejuar.", MetaText.cap(sentences, 120));
        assertEquals("E shkurtër.", MetaText.cap("<p>E   shkurtër.</p>", 155));
    }

    @Test
    void fitAddsPartsInOrderWhileTheyFit() {
        String out = MetaText.fit(List.of("RTX 3060 (e përdorur) – 30.000 Lekë.", "Pagesë në dorëzim", "",
                "Një përshkrim shumë i gjatë që nuk hyn dot i gjithi, prandaj pritet në fund të një fjale dhe mbyllet me pikë si duhet."), 120);
        assertTrue(out.startsWith("RTX 3060 (e përdorur) – 30.000 Lekë. Pagesë në dorëzim. Një"), out);
        assertTrue(out.length() <= 120 && out.endsWith("."), out);
    }

    @Test
    void generatedCategoryTitlesStayUnderSixtyCharacters() {
        SeoService seo = new SeoService(props("https://pcmania.al", null), mock(ImageStorage.class), new ObjectMapper());
        assertEquals("Karta grafike të Reja dhe të Përdorura në Tiranë | PCMania", seo.categoryTitle(category("Karta grafike")));
        assertEquals("Pjesë për ftohjen e ujit të Përdorura në Tiranë | PCMania", seo.categoryTitle(category("Pjesë për ftohjen e ujit")));
        assertEquals("Aksesorë dhe pajisje periferike gaming të Përdorura në Tiranë",
                seo.categoryTitle(category("Aksesorë dhe pajisje periferike gaming")));
        Category custom = category("Karta grafike");
        custom.setMetaTitle("  Karta grafike në Tiranë  ");
        assertEquals("Karta grafike në Tiranë", seo.categoryTitle(custom));
        assertTrue(seo.categoryDescription(category("Furnizues energjie")).length() <= 155);
    }

    @Test
    void onlyAnAddressOnTheShopsOwnDomainIsPublic() {
        assertNull(props("https://pcmania.al", "drobi840@gmail.com").publicEmail());
        assertNull(props("https://pcmania.al", "").publicEmail());
        assertNull(props("https://pcmania.al", null).publicEmail());
        assertEquals("info@pcmania.al", props("https://pcmania.al", "info@pcmania.al").publicEmail());
        assertEquals("info@pcmania.al", props("https://www.pcmania.al/", "info@pcmania.al").publicEmail());
        assertNull(props("https://pc-mania.onrender.com", "info@pcmania.al").publicEmail());
    }

    @Test
    void barcodesNeedTheRightLengthAndCheckDigit() {
        assertTrue(Gtin.isValid("4006381333931"));
        assertTrue(Gtin.isValid("036000291452"));
        assertTrue(Gtin.isValid("96385074"));
        assertTrue(Gtin.isValid("4006381 333931"));
        assertTrue(Gtin.isValid(null) && Gtin.isValid("  "));
        assertFalse(Gtin.isValid("4006381333932"));
        assertFalse(Gtin.isValid("40063813339"));
        assertFalse(Gtin.isValid("ABC6381333931"));
        assertEquals("4006381333931", Gtin.normalize(" 4006381-333931 "));
        assertNull(Gtin.normalize(" "));
    }

    private static Category category(String name) {
        Category c = new Category();
        c.setNameSq(name);
        c.setSlug("x");
        return c;
    }

    private static AppProperties props(String base, String email) {
        return new AppProperties(base, "PCMania", "./uploads", null, null, "355600000000", "+355 60", email, null, 500, null, null);
    }
}
