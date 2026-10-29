package al.pcmania.service.chat;

import al.pcmania.service.chat.Compatibility.Check;
import al.pcmania.service.chat.Compatibility.Part;
import al.pcmania.service.chat.Compatibility.Verdict;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityTest {

    @Test
    void partsAreRecognisedFromHowCustomersNameThem() {
        assertEquals("motherboard", Compatibility.kindFromText("MSI B450 Tomahawk"));
        Part board = Compatibility.fromText("MSI B450 Tomahawk", "motherboard");
        assertEquals(Map.of("chipset", "B450", "socket", "AM4", "ram_type", "DDR4"), board.values());

        Part ryzen = Compatibility.fromText("Ryzen 5 3600", Compatibility.kindFromText("Ryzen 5 3600"));
        assertEquals("cpu", ryzen.kind());
        assertEquals("AM4", ryzen.get("socket"));
        assertEquals("Ryzen 3000", ryzen.get("generation"));
        assertEquals("AM5", Compatibility.fromText("ryzen 7 7800x3d", "cpu").get("socket"));
        assertEquals("true", Compatibility.fromText("Ryzen 5 3400G", "cpu").get("zen_plus_apu"));
        assertNull(Compatibility.fromText("Ryzen 5 5600G", "cpu").get("zen_plus_apu"));

        Part intel = Compatibility.fromText("Intel i5-12400F", Compatibility.kindFromText("Intel i5-12400F"));
        assertEquals("LGA1700", intel.get("socket"));
        assertEquals("Intel gjenerata 12", intel.get("generation"));
        assertEquals("LGA1151", Compatibility.fromText("i7 9700K", "cpu").get("socket"));

        assertEquals("psu", Compatibility.kindFromText("Corsair CV550 550W"));
        assertEquals("550", Compatibility.fromText("Corsair CV550 550W", "psu").get("wattage"));

        assertEquals("ram", Compatibility.kindFromText("HyperX Fury 2x8GB DDR4"));
        Part ram = Compatibility.fromText("HyperX Fury 2x8GB DDR4", "ram");
        assertEquals("DDR4", ram.get("type"));
        assertEquals("16", ram.get("capacity_gb"));
        assertEquals("2x8GB", ram.get("kit_config"));

        assertEquals("Micro-ATX", Compatibility.fromText("Gigabyte A520M K V2", "motherboard").get("form_factor"));
        assertEquals("gpu", Compatibility.kindFromText("RX 580 8GB"));
        assertNull(Compatibility.kindFromText("diçka tjetër"));
    }

    @Test
    void coolerSocketListsAreReadLikeTheBoxPrintsThem() {
        Set<String> s = Compatibility.coolerSockets("Intel LGA1851/1700/1200/115x, AMD AM5/AM4");
        assertEquals(Set.of("LGA1851", "LGA1700", "LGA1200", "LGA115X", "AM5", "AM4"), s);
        assertTrue(Compatibility.coolerFits(s, "LGA1151"));
        assertTrue(Compatibility.coolerFits(s, "am4"));
        assertFalse(Compatibility.coolerFits(s, "AM3"));
        assertFalse(Compatibility.coolerFits(s, "LGA2066"));
    }

    @Test
    void processorAndMotherboard() {
        Part r5000 = part("Ryzen 7 5700X", "cpu", "socket", "AM4", "generation", "Ryzen 5000 (Zen 3)");
        assertEquals(Verdict.OK, only(r5000, part("Gigabyte A520M K V2", "motherboard", "socket", "AM4", "chipset", "A520")).verdict());

        Check bios = only(r5000, Compatibility.fromText("MSI B450 Tomahawk", "motherboard"));
        assertEquals(Verdict.TIGHT, bios.verdict());
        assertTrue(bios.reason().contains("BIOS"), bios.reason());

        assertEquals(Verdict.NO, only(Compatibility.fromText("Ryzen 7 7700", "cpu"), Compatibility.fromText("B450", "motherboard")).verdict());
        assertEquals(Verdict.NO, only(Compatibility.fromText("Ryzen 5 3400G", "cpu"), Compatibility.fromText("A520M", "motherboard")).verdict());
        assertEquals(Verdict.NO, only(Compatibility.fromText("Ryzen 5 2600", "cpu"), Compatibility.fromText("B550", "motherboard")).verdict());
        assertEquals(Verdict.TIGHT, only(Compatibility.fromText("i5-13400F", "cpu"), Compatibility.fromText("B660M", "motherboard")).verdict());
        assertEquals(Verdict.OK, only(Compatibility.fromText("i5-13400F", "cpu"), Compatibility.fromText("B760", "motherboard")).verdict());
        assertEquals(Verdict.UNKNOWN, only(r5000, part("Pllakë pa të dhëna", "motherboard")).verdict());
    }

    @Test
    void memoryAndMotherboard() {
        Part board = part("A520M", "motherboard", "socket", "AM4", "ram_type", "DDR4", "max_ram_gb", "64", "ram_slots", "2");
        assertEquals(Verdict.OK, Compatibility.worst(Compatibility.check(List.of(part("HyperX 16GB", "ram", "type", "DDR4", "capacity_gb", "16", "kit_config", "2x8GB"), board))));
        assertEquals(Verdict.NO, Compatibility.worst(Compatibility.check(List.of(part("Kingston 32GB", "ram", "type", "DDR5", "capacity_gb", "32"), board))));
        assertEquals(Verdict.NO, Compatibility.worst(Compatibility.check(List.of(part("Kit 4x16", "ram", "type", "DDR4", "capacity_gb", "64", "kit_config", "4x16GB"), board))));
        assertEquals(Verdict.NO, Compatibility.worst(Compatibility.check(List.of(part("Kit 2x48", "ram", "type", "DDR4", "capacity_gb", "96", "kit_config", "2x48GB"), board))));
    }

    @Test
    void coolersAgainstProcessorAndCase() {
        Part ak400 = part("AK400 Digital SE", "cooling", "type", "Ajër", "sockets", "Intel LGA1851/1700/1200/115x, AMD AM5/AM4", "height_mm", "157");
        assertEquals(Verdict.OK, only(ak400, part("Ryzen 7 5700X", "cpu", "socket", "AM4")).verdict());
        assertEquals(Verdict.NO, only(ak400, part("Threadripper", "cpu", "socket", "sTRX4")).verdict());
        assertEquals(Verdict.NO, only(ak400, part("Kasë e vogël", "case", "max_cooler_height_mm", "150")).verdict());
        assertEquals(Verdict.TIGHT, only(ak400, part("Kasë", "case", "max_cooler_height_mm", "160")).verdict());
        assertEquals(Verdict.OK, only(ak400, part("Kasë", "case", "max_cooler_height_mm", "170")).verdict());
        Part aio = part("LE360 V2", "cooling", "type", "Ujë (AIO)", "radiator_mm", "360");
        assertEquals(Verdict.UNKNOWN, only(aio, part("Kasë", "case", "max_gpu_length_mm", "380")).verdict());
    }

    @Test
    void graphicsCardAgainstCaseAndPowerSupply() {
        Part gpu = part("RTX 3070 Ti", "gpu", "length_mm", "285", "psu_min_watts", "750", "pcie_connectors", "2x 8-pin");
        assertEquals(Verdict.OK, only(gpu, part("Kasë", "case", "max_gpu_length_mm", "320")).verdict());
        assertEquals(Verdict.TIGHT, only(gpu, part("Kasë", "case", "max_gpu_length_mm", "290")).verdict());
        assertEquals(Verdict.NO, only(gpu, part("Kasë", "case", "max_gpu_length_mm", "270")).verdict());
        assertEquals(Verdict.OK, only(gpu, part("Corsair 850W", "psu", "wattage", "850")).verdict());
        assertEquals(Verdict.TIGHT, only(gpu, part("Corsair 700W", "psu", "wattage", "700")).verdict());
        Check weak = only(gpu, Compatibility.fromText("Corsair CV550 550W", "psu"));
        assertEquals(Verdict.NO, weak.verdict());
        assertTrue(weak.reason().contains("750"), weak.reason());
    }

    @Test
    void motherboardSizeAndDisks() {
        assertEquals(Verdict.OK, only(part("A520M", "motherboard", "form_factor", "Micro-ATX"), part("H6 Flow", "case", "form_factor", "Mid Tower")).verdict());
        assertEquals(Verdict.NO, only(part("B550 ATX", "motherboard", "form_factor", "ATX"), part("Kasë mATX", "case", "form_factor", "Micro-ATX")).verdict());
        assertEquals(Verdict.NO, only(part("NV2 1TB", "storage", "type", "NVMe"), part("Pllakë e vjetër", "motherboard", "m2_slots", "0")).verdict());
        assertEquals(Verdict.OK, only(part("NV2 1TB", "storage", "type", "NVMe"), part("A520M", "motherboard", "m2_slots", "1")).verdict());
        assertTrue(Compatibility.check(List.of(part("HDD 1TB", "storage", "type", "HDD"), part("A520M", "motherboard", "m2_slots", "1"))).isEmpty());
        assertTrue(Compatibility.check(List.of(part("RAM", "ram", "type", "DDR4"), part("RTX", "gpu"))).isEmpty(), "no direct check between RAM and a card");
    }

    private static Check only(Part a, Part b) {
        List<Check> checks = Compatibility.check(List.of(a, b));
        assertEquals(1, checks.size(), checks.toString());
        return checks.get(0);
    }

    private static Part part(String name, String kind, String... kv) {
        Map<String, String> v = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) v.put(kv[i], kv[i + 1]);
        return new Part(name, kind, Compatibility.STOCK, v);
    }
}
