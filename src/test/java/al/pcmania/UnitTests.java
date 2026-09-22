package al.pcmania;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.service.DashboardService;
import al.pcmania.service.Slugs;
import al.pcmania.web.Fmt;
import al.pcmania.web.view.CatalogFilter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnitTests {

    @Test
    void slugTransliteratesAlbanian() {
        assertEquals("karte-grafike-rtx-3060-12gb", Slugs.of("Kartë Grafike RTX 3060 (12GB)"));
        assertEquals("procesore-cele", Slugs.of("  Procesorë çelë!! "));
        assertEquals("produkt", Slugs.of("???"));
    }

    @Test
    void lekFormatting() {
        assertEquals("125.000 Lekë", Fmt.lek(125000));
        assertEquals("0 Lekë", Fmt.lek(0));
        assertEquals("1.250.000 Lekë", Fmt.lek(1_250_000L));
        assertEquals("1 vit garanci", new Fmt().warranty(365));
        assertEquals("3 muaj garanci", new Fmt().warranty(90));
        assertNull(new Fmt().warranty(null));
    }

    @Test
    void catalogFilterParsesAndBuildsUrls() {
        var f = CatalogFilter.of(0, 100000, List.of("USED", "BOGUS"), List.of("msi"),
                List.of("VRAM:12 GB", "VRAM:8 GB", "broken", "Bus:PCIe 4.0 x16"), "cmimi-rritje", 2);
        assertNull(f.min());
        assertEquals(Set_of(Condition.USED), f.conditions());
        assertTrue(f.hasSpec("VRAM", "8 GB"));
        assertEquals(2, f.specs().size());
        assertEquals(6, f.activeCount()); // price range + condition + brand + 3 spec values
        String url = f.pageUrl("/kategori/karta-grafike", 3);
        assertTrue(url.contains("spec=VRAM:12%20GB"), url);
        assertTrue(url.endsWith("rendit=cmimi-rritje&faqe=3"), url);
        assertFalse(f.pageUrl("/k", 1).contains("faqe"));
    }

    @Test
    void priceBandsAreHalfOpen() {
        var bands = DashboardService.BANDS;
        assertEquals("0 – 100k", bandFor(99_999).label());
        assertEquals("100k – 150k", bandFor(100_000).label());
        assertEquals("250k+", bandFor(900_000).label());
        assertEquals(5, bands.size());
    }

    @Test
    void totalsWeightMarginByRevenueAndDaysByUnit() throws Exception {
        LocalDateTime t = LocalDateTime.now();
        var items = List.of(
                new DashboardService.SoldItem(1L, "A", 1L, "a", 2, 80, 100, t, t, 10L),  // profit 40, rev 200
                new DashboardService.SoldItem(2L, "B", 2L, "b", 1, 900, 1000, t, t, 40L), // profit 100, rev 1000
                new DashboardService.SoldItem(3L, "C", null, "c", 1, 50, 60, null, t, null));
        var m = DashboardService.class.getDeclaredMethod("totals", List.class);
        m.setAccessible(true);
        var totals = (DashboardService.Totals) m.invoke(null, items);
        assertEquals(4, totals.units());
        assertEquals(1260, totals.revenue());
        assertEquals(150, totals.profit());
        assertEquals(100.0 * 150 / 1260, totals.marginPct(), 1e-9);
        assertEquals(20.0, totals.avgDaysToSell(), 1e-9); // (10*2 + 40) / 3 units with known days
    }

    private static DashboardService.Band bandFor(int price) {
        return DashboardService.BANDS.stream().filter(b -> price >= b.min() && (b.max() == null || price < b.max())).findFirst().orElseThrow();
    }

    private static java.util.Set<Condition> Set_of(Condition c) {
        return java.util.EnumSet.of(c);
    }
}
