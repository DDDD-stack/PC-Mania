package al.pcmania.service.chat;

import al.pcmania.config.AppProperties;
import al.pcmania.config.ChatProperties;
import al.pcmania.domain.ChatMessage;
import al.pcmania.domain.ChatSession;
import al.pcmania.domain.Enums.ChatRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Gemini's wire format, request and response, and the free-tier handling, with the network scripted. */
class GeminiChatProviderTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ScriptedHttp http = new ScriptedHttp();
    private final ProviderUsageService usage = mock(ProviderUsageService.class);
    private final ChatTools tools = mock(ChatTools.class);
    private final ToolRegistry registry = new ToolRegistry(tools, json);
    private final ChatPrompt prompt = new ChatPrompt(new AppProperties("http://localhost", "PCMania", "./uploads", null, null,
            "355688343551", "+355 68 83 43 551", null, null, 500, new AppProperties.Admin("admin", null), null));

    private GeminiChatProvider provider(String key, int rpm) {
        ChatProperties props = new ChatProperties("gemini", 25, 4, 1024, 30,
                new ChatProperties.Gemini(key, "gemini-2.5-flash", rpm, 1500),
                new ChatProperties.Anthropic(null, "claude-haiku-4-5-20251001", 25, 1, 5, .1, 1.25));
        return new GeminiChatProvider(props, prompt, usage, http, json);
    }

    private static ChatMessage msg(ChatRole role, String text) {
        ChatMessage m = new ChatMessage();
        m.setRole(role);
        m.setContent(text);
        return m;
    }

    @Test
    void sendsTheDocumentedShapeAndRunsTheFunctionCallLoop() throws Exception {
        ChatTools.StockItem item = new ChatTools.StockItem("msi-3060-ti", "MSI RTX 3060 Ti", 38_000, "E përdorur", 90, 8, 11, 600,
                "1x 8-pin", 242, null, false, false, "GeForce RTX 3060 Ti", 300, 92, 64);
        when(tools.searchStock(any(), any(), any(), any(), any(), any())).thenReturn(List.of(item));
        http.stream("data: {\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"searchStock\",\"args\":{\"budgetMaxLek\":40000}},\"thoughtSignature\":\"sig-1\"}],\"role\":\"model\"},\"index\":0}],\"usageMetadata\":{\"promptTokenCount\":700,\"candidatesTokenCount\":20}}")
            .stream("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Kemi MSI \"}],\"role\":\"model\"}}]}",
                    "",
                    "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"RTX 3060 Ti për 38.000 Lekë.\"}],\"role\":\"model\"},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":900,\"candidatesTokenCount\":30}}");

        ChatStream out = new ChatStream(json, 10_000);
        GeminiChatProvider p = provider("gk-test", 12);
        assertTrue(p.isAvailable());
        p.stream(new ChatSession(), "Kam 40 mijë", List.of(msg(ChatRole.USER, "Hej"), msg(ChatRole.ASSISTANT, "Tungjatjeta!")), registry, out);

        assertEquals("Kemi MSI RTX 3060 Ti për 38.000 Lekë.", out.text().strip());
        assertEquals(List.of("msi-3060-ti"), out.surfacedSlugs());
        assertEquals(2, http.sent.size());
        ScriptedHttp.Sent first = http.sent.get(0);
        assertEquals(GeminiChatProvider.BASE_URL + "gemini-2.5-flash:streamGenerateContent?alt=sse", first.url());
        assertEquals("gk-test", first.headers().get("x-goog-api-key"));
        JsonNode body = json.readTree(first.body());
        assertTrue(body.path("systemInstruction").path("parts").get(0).path("text").asText().contains("NEVER ask for a name"));
        assertEquals(1024, body.path("generationConfig").path("maxOutputTokens").asInt());
        JsonNode decl = body.path("tools").get(0).path("functionDeclarations");
        assertEquals(6, decl.size());
        assertEquals("OBJECT", decl.get(0).path("parameters").path("type").asText());
        assertEquals("INTEGER", decl.get(0).path("parameters").path("properties").path("budgetMaxLek").path("type").asText());
        assertEquals("currentCardQuery", decl.get(3).path("parameters").path("required").get(0).asText());
        // History: user, model, then the new message as user.
        assertEquals(3, body.path("contents").size());
        assertEquals("user", body.path("contents").get(0).path("role").asText());
        assertEquals("model", body.path("contents").get(1).path("role").asText());
        assertEquals("Kam 40 mijë", body.path("contents").get(2).path("parts").get(0).path("text").asText());

        // Second request: the model's call echoed verbatim (signature kept) and the functionResponse after it.
        JsonNode second = json.readTree(http.sent.get(1).body()).path("contents");
        assertEquals(5, second.size());
        JsonNode modelTurn = second.get(3);
        assertEquals("model", modelTurn.path("role").asText());
        assertEquals("sig-1", modelTurn.path("parts").get(0).path("thoughtSignature").asText());
        assertEquals("searchStock", modelTurn.path("parts").get(0).path("functionCall").path("name").asText());
        JsonNode toolTurn = second.get(4);
        assertEquals("user", toolTurn.path("role").asText());
        JsonNode fr = toolTurn.path("parts").get(0).path("functionResponse");
        assertEquals("searchStock", fr.path("name").asText());
        assertEquals("msi-3060-ti", fr.path("response").path("items").get(0).path("slug").asText());
        assertFalse(http.sent.get(1).body().contains("costLek") || http.sent.get(1).body().contains("maxTradeValue"));
        verify(usage, times(2)).request("gemini");
    }

    @Test
    void rateLimitAndMissingKeyFallThrough() {
        http.status(429, "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}");
        GeminiChatProvider p = provider("gk-test", 12);
        ProviderUnavailableException e = assertThrows(ProviderUnavailableException.class,
                () -> p.stream(new ChatSession(), "Hej", List.of(), registry, new ChatStream(json, 10_000)));
        assertTrue(e.isRateLimited());
        verify(usage).rateLimitHit("gemini");

        assertFalse(provider(null, 12).isAvailable());
        assertFalse(assertThrows(ProviderUnavailableException.class,
                () -> provider("", 12).stream(new ChatSession(), "Hej", List.of(), registry, new ChatStream(json, 10_000))).isRateLimited());

        // The local limiter: one token a minute, the second request is refused before any network call.
        GeminiChatProvider tight = provider("gk-test", 1);
        http.stream("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Po.\"}],\"role\":\"model\"},\"finishReason\":\"STOP\"}]}");
        assertDoesNotThrow(() -> tight.stream(new ChatSession(), "Hej", List.of(), registry, new ChatStream(json, 10_000)));
        assertFalse(tight.isAvailable());
        int before = http.sent.size();
        assertTrue(assertThrows(ProviderUnavailableException.class,
                () -> tight.stream(new ChatSession(), "Hej", List.of(), registry, new ChatStream(json, 10_000))).isRateLimited());
        assertEquals(before, http.sent.size());
    }

    @Test
    void serverErrorsAndOutagesAreNotRateLimits() {
        http.status(500, "boom");
        assertFalse(assertThrows(ProviderUnavailableException.class,
                () -> provider("gk", 12).stream(new ChatSession(), "Hej", List.of(), registry, new ChatStream(json, 10_000))).isRateLimited());
        // The body travels with the error: that string is what Admin > Asistenti shows the operator.
        verify(usage).error(eq("gemini"), contains("boom"));
        http.fail("connection reset");
        assertThrows(ProviderUnavailableException.class,
                () -> provider("gk", 12).stream(new ChatSession(), "Hej", List.of(), registry, new ChatStream(json, 10_000)));
    }

    @Test
    void toolRoundsAreCappedAndTheModelIsToldToAnswer() throws Exception {
        when(tools.searchStock(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        for (int i = 0; i < 5; i++) {
            http.stream("data: {\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"searchStock\",\"args\":{}}}],\"role\":\"model\"}}]}");
        }
        http.stream("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Ja çfarë kemi.\"}],\"role\":\"model\"},\"finishReason\":\"STOP\"}]}");
        ChatStream out = new ChatStream(json, 10_000);
        provider("gk", 60).stream(new ChatSession(), "Çfarë keni?", List.of(), registry, out);
        assertEquals(6, http.sent.size());
        assertEquals("Ja çfarë kemi.", out.text().strip());
        String last = http.sent.get(5).body();
        assertTrue(last.contains("Kufiri i thirrjeve"), last);
        assertEquals(4, out.toolCalls().size());
    }
}
