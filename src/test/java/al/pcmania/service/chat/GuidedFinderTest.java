package al.pcmania.service.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GuidedFinderTest {

    private final ChatTools tools = mock(ChatTools.class);
    private final GuidedFinder finder = new GuidedFinder(tools);

    @Test
    void asksTheFourQuestionsInOrderAndIgnoresNonsense() {
        GuidedFinder.Step s1 = finder.step(Map.of());
        assertEquals("use", s1.key());
        assertEquals(1, s1.number());
        assertEquals(3, s1.options().size());
        GuidedFinder.Step s2 = finder.step(Map.of("use", "AAA"));
        assertEquals("res", s2.key());
        assertEquals("aaa", s2.answers().get("use"));
        GuidedFinder.Step s3 = finder.step(Map.of("use", "aaa", "res", "1440p"));
        assertEquals("budget", s3.key());
        assertEquals("range", s3.kind());
        assertEquals(80_000, s3.min());
        GuidedFinder.Step s4 = finder.step(Map.of("use", "aaa", "res", "1440p", "budget", "150000"));
        assertEquals("psu", s4.key());
        assertEquals(4, s4.options().size());

        assertEquals("res", finder.step(Map.of("use", "aaa", "res", "4k")).key());
    }

    @Test
    void searchesWithTheAnswersAndOffersTheFormWhenNothingFits() {
        ChatTools.StockItem item = new ChatTools.StockItem("rx-6600", "Sapphire RX 6600", 24_000, "E përdorur", 90, 8, 8, 450,
                "1x 8-pin", 200, null, false, false, "Radeon RX 6600", 240, 68, 45);
        when(tools.searchStock(isNull(), eq(150_000), eq(ChatTools.UseCase.AAA_1440P), isNull(), eq(550), isNull())).thenReturn(List.of(item));
        GuidedFinder.Step done = finder.step(Map.of("use", "aaa", "res", "1440p", "budget", "150", "psu", "550"));
        assertTrue(done.done());
        assertEquals(1, done.results().size());
        assertFalse(done.showLeadForm());
        assertEquals(150_000, done.budgetLek());
        assertEquals(550, done.psuWatts());

        when(tools.searchStock(isNull(), eq(80_000), isNull(), eq(8), isNull(), isNull())).thenReturn(List.of());
        GuidedFinder.Step none = finder.step(Map.of("use", "work", "res", "1080p", "budget", "80000", "psu", "unknown"));
        assertTrue(none.done());
        assertTrue(none.showLeadForm());
        assertNull(none.psuWatts());
        assertTrue(none.wantedItem().contains("punë / AI"), none.wantedItem());
        assertTrue(none.resultNote().contains("Lër të dhënat"), none.resultNote());
    }
}
