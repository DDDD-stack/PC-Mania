package al.pcmania.service.notify;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TelegramNotifierTest {

    @Test
    void postsTheMessageToTheBotApi() {
        List<String> calls = new ArrayList<>();
        TelegramNotifier t = new TelegramNotifier("123:abc", "-1001", (uri, form) -> {
            calls.add(uri + " " + form);
            return 200;
        });
        assertTrue(t.isEnabled());
        t.notifyAdmin("Porosi e re PM-2026-0001", "Klienti: Ana\nTotali: 50.000 Lekë");
        assertEquals(1, calls.size());
        assertTrue(calls.get(0).startsWith("https://api.telegram.org/bot123:abc/sendMessage "), calls.get(0));
        String form = URLDecoder.decode(calls.get(0).substring(calls.get(0).indexOf(' ') + 1), StandardCharsets.UTF_8);
        assertTrue(form.contains("chat_id=-1001"), form);
        assertTrue(form.contains("text=Porosi e re PM-2026-0001\n\nKlienti: Ana\nTotali: 50.000 Lekë"), form);
    }

    @Test
    void isOffWithoutBothSettingsAndReportsFailuresWithoutTheToken() {
        assertFalse(new TelegramNotifier("", "1", (u, f) -> 200).isEnabled());
        assertFalse(new TelegramNotifier("t", null, (u, f) -> 200).isEnabled());
        TelegramNotifier failing = new TelegramNotifier("secret-token", "1", (u, f) -> 401);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> failing.notifyAdmin("a", "b"));
        assertFalse(e.getMessage().contains("secret-token"));
        TelegramNotifier unreachable = new TelegramNotifier("secret-token", "1", (u, f) -> { throw new java.io.IOException("connect to " + u); });
        IllegalStateException e2 = assertThrows(IllegalStateException.class, () -> unreachable.notifyAdmin("a", "b"));
        assertFalse(e2.getMessage().contains("secret-token"));
    }

    @Test
    void longMessagesAreCutToTelegramsLimit() {
        List<String> forms = new ArrayList<>();
        new TelegramNotifier("t", "1", (u, f) -> { forms.add(f); return 200; }).notifyAdmin("s", "x".repeat(10_000));
        String text = URLDecoder.decode(forms.get(0), StandardCharsets.UTF_8);
        assertTrue(text.length() < 4100, String.valueOf(text.length()));
    }
}
