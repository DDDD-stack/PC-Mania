package al.pcmania.service;

import al.pcmania.domain.Combo;
import al.pcmania.domain.ComboItem;
import al.pcmania.domain.Enums.FieldType;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;
import al.pcmania.service.notify.AdminNotifications;
import al.pcmania.service.notify.AdminNotifier;
import al.pcmania.web.Fmt;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseUnitTests {

    @Test
    void comboLinesAlwaysAddUpToTheComboPrice() {
        Random r = new Random(42);
        for (int run = 0; run < 2000; run++) {
            int n = 2 + r.nextInt(4);
            int[] prices = new int[n], qty = new int[n];
            long original = 0;
            for (int i = 0; i < n; i++) {
                prices[i] = 1_000 + r.nextInt(150_000);
                qty[i] = run % 3 == 0 ? 2 + r.nextInt(2) : 1 + r.nextInt(2);
                original += (long) prices[i] * qty[i];
            }
            int total = (int) (original * (50 + r.nextInt(49)) / 100);
            List<ComboService.Priced> lines = ComboService.allocate(prices, qty, total);
            assertEquals(total, lines.stream().mapToLong(l -> (long) l.unitPriceLek() * l.quantity()).sum());
            for (int i = 0; i < n; i++) {
                final int idx = i;
                assertEquals(qty[i], lines.stream().filter(l -> l.index() == idx).mapToInt(ComboService.Priced::quantity).sum());
            }
            assertTrue(lines.stream().allMatch(l -> l.unitPriceLek() >= 0 && l.quantity() > 0));
        }
    }

    @Test
    void hiddenCombosSayWhyInPlainAlbanian() {
        Combo c = new Combo();
        c.setActive(true);
        Product sold = product("RTX 3060 Ti Manli", ProductStatus.SOLD, 0);
        Product short1 = product("Ryzen 5 5600", ProductStatus.ACTIVE, 1);
        c.getItems().add(new ComboItem(c, sold, 1));
        c.getItems().add(new ComboItem(c, short1, 2));
        LocalDateTime now = LocalDateTime.of(2026, 10, 18, 12, 0);
        c.setEndsAt(now.minusDays(1));
        List<String> reasons = ComboService.reasons(c, now);
        assertTrue(reasons.contains("RTX 3060 Ti Manli është SHITUR"), reasons.toString());
        assertTrue(reasons.contains("Ryzen 5 5600 ka vetëm 1 copë (duhen 2)"), reasons.toString());
        assertTrue(reasons.contains("përfundoi më 17.10.2026 12:00"), reasons.toString());
        c.setEndsAt(null);
        c.getItems().clear();
        c.getItems().add(new ComboItem(c, product("A", ProductStatus.ACTIVE, 1), 1));
        assertTrue(ComboService.reasons(c, now).isEmpty());
    }

    @Test
    void savingsReadAsThousandsOfLek() {
        assertEquals("Kurseni 45 mijë lekë", new Fmt().savings(45_000));
        assertEquals("Kurseni 4,5 mijë lekë", new Fmt().savings(4_500));
        assertEquals("Kurseni 800 lekë", new Fmt().savings(800));
    }

    @Test
    void templateValuesAreNormalisedAndShownWithTheirUnit() {
        var ram = new FieldTemplateService.Field(1L, "capacity_gb", "Kapaciteti", null, FieldType.INTEGER, "GB", List.of(), true, true, true, 1);
        var clock = new FieldTemplateService.Field(2L, "boost_clock_ghz", "Boost", null, FieldType.DECIMAL, "GHz", List.of(), false, false, false, 2);
        var rgb = new FieldTemplateService.Field(3L, "rgb", "RGB", null, FieldType.BOOLEAN, null, List.of(), false, false, false, 3);
        var type = new FieldTemplateService.Field(4L, "type", "Tipi", null, FieldType.ENUM, null, List.of("DDR4", "DDR5"), false, false, false, 4);
        var hours = new FieldTemplateService.Field(5L, "power_on_hours", "Orë pune", null, FieldType.INTEGER, "orë", List.of(), false, false, false, 5);
        assertEquals("16", FieldTemplateService.normalize(ram, "16 GB"));
        assertEquals("16", FieldTemplateService.normalize(ram, " 16gb "));
        assertEquals("4.6", FieldTemplateService.normalize(clock, "4,6 GHz"));
        assertEquals("true", FieldTemplateService.normalize(rgb, "Po"));
        assertEquals("DDR5", FieldTemplateService.normalize(type, "ddr5"));
        assertEquals("12345", FieldTemplateService.normalize(hours, "12.345"));
        assertThrows(IllegalArgumentException.class, () -> FieldTemplateService.normalize(ram, "gjashtëmbëdhjetë"));
        assertThrows(IllegalArgumentException.class, () -> FieldTemplateService.normalize(type, "DDR3"));
        assertEquals("16 GB", ram.display("16"));
        assertEquals("4,6 GHz", clock.display("4.6"));
        assertEquals("Po", rgb.display("true"));
        assertEquals("12.345 orë", hours.display("12345"));
    }

    @Test
    void oneFailingNotifierDoesNotStopTheOthers() {
        List<String> delivered = new ArrayList<>();
        AdminNotifier broken = notifier("Broken", true, (s, b) -> { throw new IllegalStateException("down"); });
        AdminNotifier off = notifier("Off", false, (s, b) -> delivered.add("off"));
        AdminNotifier ok = notifier("Ok", true, (s, b) -> delivered.add(s));
        AdminNotifications all = new AdminNotifications(List.of(broken, off, ok));
        assertEquals(1, all.notifyAll("Porosi e re", "trupi"));
        assertEquals(List.of("Porosi e re"), delivered);
        assertEquals(List.of("Broken", "Ok"), all.enabled());
    }

    private static AdminNotifier notifier(String name, boolean enabled, java.util.function.BiConsumer<String, String> send) {
        return new AdminNotifier() {
            public String name() { return name; }
            public boolean isEnabled() { return enabled; }
            public void notifyAdmin(String subject, String body) { send.accept(subject, body); }
        };
    }

    private static Product product(String title, ProductStatus status, int qty) {
        Product p = new Product();
        p.setTitle(title);
        p.setStatus(status);
        p.setQuantity(qty);
        p.setPriceLek(10_000);
        return p;
    }
}
