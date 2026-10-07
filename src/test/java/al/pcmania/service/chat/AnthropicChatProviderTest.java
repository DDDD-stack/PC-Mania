package al.pcmania.service.chat;

import al.pcmania.config.AppProperties;
import al.pcmania.config.ChatProperties;
import al.pcmania.domain.ChatSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The Anthropic path compiles and works against a scripted network, although no key is configured in production. */
class AnthropicChatProviderTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ScriptedHttp http = new ScriptedHttp();
    private final ProviderUsageService usage = mock(ProviderUsageService.class);
    private final ChatSpend spend = mock(ChatSpend.class);
    private final ChatTools tools = mock(ChatTools.class);
    private final ToolRegistry registry = new ToolRegistry(tools, json);
    private final ChatPrompt prompt = new ChatPrompt(new AppProperties("http://localhost", "PCMania", "./uploads", null, null,
            "355688343551", "+355 68 83 43 551", null, null, 500, new AppProperties.Admin("admin", null), null));

    private AnthropicChatProvider provider(String key) {
        ChatProperties props = new ChatProperties("anthropic", 25, 4, 1024, 30,
                new ChatProperties.Gemini(null, "gemini-3.8-flash", 12, 1500),
                new ChatProperties.Anthropic(key, "claude-haiku-4-5-20251001", 25, 1, 5, .1, 1.25));
        return new AnthropicChatProvider(props, prompt, usage, http, json, spend);
    }

    @Test
    void sendsTheMessagesApiShapeAndRunsTheToolLoop() throws Exception {
        ChatTools.ProductInfo info = new ChatTools.ProductInfo("msi-3060-ti", "MSI RTX 3060 Ti", "MSI", 38_000, "E përdorur", "ACTIVE",
                true, 1, 90, null, null, false, false, false, List.of(), null, "/produkt/msi-3060-ti");
        when(tools.getProduct("msi-3060-ti")).thenReturn(Optional.of(info));
        http.stream("event: message_start",
                        "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":1200,\"cache_read_input_tokens\":1000,\"cache_creation_input_tokens\":0}}}",
                        "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"getProduct\",\"input\":{}}}",
                        "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"slug\\\": \\\"msi-\"}}",
                        "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"3060-ti\\\"}\"}}",
                        "data: {\"type\":\"content_block_stop\",\"index\":0}",
                        "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":40}}")
            .stream("data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":1300}}}",
                        "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
                        "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"MSI RTX 3060 Ti \"}}",
                        "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"kushton 38.000 Lekë.\"}}",
                        "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":25}}");

        ChatStream out = new ChatStream(json, 10_000);
        AnthropicChatProvider p = provider("sk-ant-test");
        assertTrue(p.isAvailable());
        p.stream(new ChatSession(), "Sa kushton 3060 ti?", List.of(), registry, out);

        assertEquals("MSI RTX 3060 Ti kushton 38.000 Lekë.", out.text().strip());
        assertEquals(List.of("msi-3060-ti"), out.surfacedSlugs());
        ScriptedHttp.Sent first = http.sent.get(0);
        assertEquals(AnthropicChatProvider.URL, first.url());
        assertEquals("sk-ant-test", first.headers().get("x-api-key"));
        assertEquals("2023-06-01", first.headers().get("anthropic-version"));
        JsonNode body = json.readTree(first.body());
        assertEquals("claude-haiku-4-5-20251001", body.path("model").asText());
        assertTrue(body.path("stream").asBoolean());
        assertEquals(1024, body.path("max_tokens").asInt());
        assertEquals("ephemeral", body.path("system").get(0).path("cache_control").path("type").asText());
        JsonNode toolsNode = body.path("tools");
        assertEquals(6, toolsNode.size());
        assertEquals("object", toolsNode.get(0).path("input_schema").path("type").asText());
        assertEquals("integer", toolsNode.get(0).path("input_schema").path("properties").path("budgetMaxLek").path("type").asText());
        assertEquals("ephemeral", toolsNode.get(5).path("cache_control").path("type").asText());
        assertFalse(toolsNode.get(0).has("cache_control"));

        JsonNode second = json.readTree(http.sent.get(1).body()).path("messages");
        assertEquals(3, second.size());
        JsonNode toolUse = second.get(1).path("content").get(0);
        assertEquals("tool_use", toolUse.path("type").asText());
        assertEquals("toolu_1", toolUse.path("id").asText());
        assertEquals("msi-3060-ti", toolUse.path("input").path("slug").asText());
        JsonNode result = second.get(2).path("content").get(0);
        assertEquals("tool_result", result.path("type").asText());
        assertEquals("toolu_1", result.path("tool_use_id").asText());
        assertTrue(result.path("content").asText().contains("\"slug\":\"msi-3060-ti\""));
        verify(spend).record(1200, 40, 1000, 0);
        verify(spend).record(1300, 25, 0, 0);
    }

    @Test
    void offWithoutAKeyOrOverTheCap() {
        assertFalse(provider(null).isAvailable());
        assertFalse(assertThrows(ProviderUnavailableException.class,
                () -> provider("").stream(new ChatSession(), "Hej", List.of(), registry, new ChatStream(json, 10_000))).isRateLimited());
        when(spend.overCap()).thenReturn(true);
        assertFalse(provider("sk").isAvailable());
        assertTrue(http.sent.isEmpty());
    }

    @Test
    void rateLimitsAndOverloadsFallThrough() {
        http.status(429, "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\"}}");
        assertTrue(assertThrows(ProviderUnavailableException.class,
                () -> provider("sk").stream(new ChatSession(), "Hej", List.of(), registry, new ChatStream(json, 10_000))).isRateLimited());
        http.status(529, "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\"}}");
        assertTrue(assertThrows(ProviderUnavailableException.class,
                () -> provider("sk").stream(new ChatSession(), "Hej", List.of(), registry, new ChatStream(json, 10_000))).isRateLimited());
        http.status(401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"}}");
        assertFalse(assertThrows(ProviderUnavailableException.class,
                () -> provider("sk").stream(new ChatSession(), "Hej", List.of(), registry, new ChatStream(json, 10_000))).isRateLimited());
        verify(usage, times(2)).rateLimitHit("anthropic");
        verify(usage).error(eq("anthropic"), contains("authentication_error"));
    }
}
