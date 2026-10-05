package al.pcmania.service.chat;

import al.pcmania.domain.Enums.GpuVendor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The neutral descriptors and the dispatch: what every provider is handed, and what a call produces. */
class ToolRegistryTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ChatTools tools = mock(ChatTools.class);
    private final ToolRegistry registry = new ToolRegistry(tools, json);

    @Test
    void sixToolsAndNoneTakesPersonalDetails() {
        List<ToolDef> defs = registry.definitions();
        assertEquals(List.of("searchStock", "getProduct", "compareProducts", "recommendUpgrade", "checkFit", "requestContactForm"),
                defs.stream().map(ToolDef::name).toList());
        for (ToolDef d : defs) {
            for (String p : d.params().keySet()) {
                String lower = p.toLowerCase();
                assertFalse(lower.contains("name") || lower.contains("phone") || lower.contains("email") || lower.contains("address"), d.name() + "." + p);
            }
        }
        assertEquals(List.of("currentCardQuery"), registry.get("recommendUpgrade").orElseThrow().required());
        assertEquals(List.of("slugA", "slugB"), registry.get("compareProducts").orElseThrow().required());
        assertEquals(List.of("ESPORTS_1080P", "AAA_1080P", "AAA_1440P"), registry.get("searchStock").orElseThrow().params().get("useCase").enumValues());
    }

    @Test
    void dispatchParsesArgumentsAndCollectsSlugs() throws Exception {
        ChatTools.StockItem item = new ChatTools.StockItem("msi-3060-ti", "MSI RTX 3060 Ti", 38_000, "E përdorur", 90, 8, 11, 600,
                "1x 8-pin", 242, "FurMark ok", true, false, "GeForce RTX 3060 Ti", 300, 92, 64);
        when(tools.searchStock(isNull(), eq(40000), eq(ChatTools.UseCase.AAA_1080P), isNull(), eq(650), eq(GpuVendor.NVIDIA)))
                .thenReturn(List.of(item));
        ToolRegistry.Execution ex = registry.execute("searchStock",
                json.readTree("{\"budgetMaxLek\":\"40000\",\"useCase\":\"aaa 1080p\",\"maxPsuWatts\":650,\"vendor\":\"NVIDIA\"}"));
        assertFalse(ex.error());
        assertEquals(List.of("msi-3060-ti"), ex.productSlugs());
        assertTrue(ex.resultJson().contains("\"lengthMm\":242"), ex.resultJson());
        assertNull(ex.action());

        when(tools.searchStock(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        ToolRegistry.Execution empty = registry.execute("searchStock", json.readTree("{}"));
        assertTrue(empty.resultJson().contains("Asnjë kartë"), empty.resultJson());
        assertTrue(empty.productSlugs().isEmpty());
    }

    @Test
    void contactFormIsASignalForTheSiteNotAWrite() throws Exception {
        when(tools.requestContactForm("RTX 4070", 80_000, null)).thenReturn(new ChatTools.LeadFormSignal("show_lead_form", "RTX 4070", 80_000, null, "note"));
        ToolRegistry.Execution ex = registry.execute("requestContactForm", json.readTree("{\"wantedItem\":\"RTX 4070\",\"budgetLek\":80000}"));
        assertFalse(ex.error());
        assertEquals("show_lead_form", ex.action().get("action").asText());
        assertEquals("RTX 4070", ex.action().get("wantedItem").asText());
        verify(tools).requestContactForm("RTX 4070", 80_000, null);
        verifyNoMoreInteractions(tools);
    }

    @Test
    void badCallsBecomeErrorResultsNotExceptions() throws Exception {
        assertTrue(registry.execute("nope", json.readTree("{}")).error());
        when(tools.getProduct(any())).thenReturn(Optional.empty());
        ToolRegistry.Execution missing = registry.execute("getProduct", json.readTree("{\"slug\":\"x\"}"));
        assertTrue(missing.error());
        assertTrue(missing.resultJson().contains("Nuk ka produkt"));
        ToolRegistry.Execution noQuery = registry.execute("recommendUpgrade", json.readTree("{}"));
        assertTrue(noQuery.error());
        ToolRegistry.Execution badNumber = registry.execute("checkFit", json.readTree("{\"slug\":\"x\",\"psuWatts\":\"99999999999999\"}"));
        assertTrue(badNumber.error());
        assertTrue(badNumber.resultJson().contains("nuk është numër"), badNumber.resultJson());
        // Text where a number was expected is simply no filter.
        when(tools.checkFit(eq("x"), isNull(), isNull())).thenReturn(Optional.empty());
        assertTrue(registry.execute("checkFit", json.readTree("{\"slug\":\"x\",\"psuWatts\":\"shumë\"}")).resultJson().contains("Nuk ka produkt"));
    }
}
