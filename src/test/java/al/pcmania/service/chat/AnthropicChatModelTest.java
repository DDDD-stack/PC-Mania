package al.pcmania.service.chat;

import al.pcmania.config.ChatProperties;
import al.pcmania.domain.Enums.ChatRole;
import al.pcmania.service.chat.ChatModel.*;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * The request the SDK is handed: the model, the cached system prompt, every tool's schema and a
 * conversation with tool calls and results in it. No network: the client is never called here.
 */
class AnthropicChatModelTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ChatProperties props = new ChatProperties("sk-test", "claude-haiku-4-5-20251001", 25, 4, 1024, 30, 25, 1, 5, .1, 1.25);
    private final AnthropicChatModel model = new AnthropicChatModel(mock(AnthropicClient.class), props, json);

    @Test
    void buildsTheRequestWithToolsAndToolTurns() throws Exception {
        ChatToolDefs defs = new ChatToolDefs(mock(ChatTools.class), json);
        List<Msg> history = List.of(
                Msg.user("Kam 40 mijë lekë"),
                new Msg(ChatRole.ASSISTANT, List.of(new Text("Po shikoj."), new ToolUse("toolu_1", "searchStock",
                        json.readTree("{\"budgetMaxLek\":40000,\"useCase\":\"AAA_1080P\"}")))),
                new Msg(ChatRole.USER, List.of(new ToolResult("toolu_1", "{\"items\":[]}", false))),
                Msg.assistant("Asgjë për këtë buxhet."),
                Msg.user("Po me 50?"));

        MessageCreateParams p = model.params("SYSTEM", defs.definitions(), history, 1024);

        assertEquals("claude-haiku-4-5-20251001", p.model().asString());
        assertEquals(1024, p.maxTokens());
        assertEquals(6, p.tools().orElseThrow().size());
        assertEquals(5, p.messages().size());
        assertEquals(MessageParam.Role.USER, p.messages().get(0).role());
        assertEquals(MessageParam.Role.ASSISTANT, p.messages().get(1).role());
        // Serialising is what the SDK does on send: every builder must have been given what it needs.
        String body = json.writeValueAsString(p._body());
        assertTrue(body.contains("\"cache_control\":{\"type\":\"ephemeral\""), body);
        assertTrue(body.contains("\"name\":\"recommendUpgrade\""), body);
        assertTrue(body.contains("\"required\":[\"currentCardQuery\"]"), body);
        assertTrue(body.contains("\"enum\":[\"ESPORTS_1080P\",\"AAA_1080P\",\"AAA_1440P\"]"), body);
        assertTrue(body.contains("\"type\":\"tool_use\"") && body.contains("\"budgetMaxLek\":40000"), body);
        assertTrue(body.contains("\"type\":\"tool_result\"") && body.contains("\"tool_use_id\":\"toolu_1\""), body);
    }

    @Test
    void toolSchemasAreObjectsWithTheirRequiredFields() {
        ChatToolDefs defs = new ChatToolDefs(mock(ChatTools.class), json);
        for (ToolDef d : defs.definitions()) {
            assertEquals("object", d.inputSchema().get("type"));
            assertTrue(d.inputSchema().get("properties") instanceof Map<?, ?>);
            assertTrue(d.inputSchema().get("required") instanceof List<?>);
        }
    }
}
