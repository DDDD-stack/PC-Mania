package al.pcmania.service.chat;

import al.pcmania.domain.Enums.ChatRole;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The language model behind the assistant, reduced to what the tool-call loop needs: send the system
 * prompt, the tools and the conversation, stream the text back, and learn which tools it wants run.
 * {@link AnthropicChatModel} is the real one; tests plug in a scripted fake, so the SDK stays out of them.
 */
public interface ChatModel {

    /** A tool the model may call: its JSON Schema object goes to the API as {@code input_schema}. */
    record ToolDef(String name, String description, Map<String, Object> inputSchema) {}

    sealed interface Part permits Text, ToolUse, ToolResult {}

    record Text(String text) implements Part {}

    record ToolUse(String id, String name, JsonNode input) implements Part {}

    record ToolResult(String toolUseId, String content, boolean error) implements Part {}

    record Msg(ChatRole role, List<Part> parts) {
        public static Msg user(String text) {
            return new Msg(ChatRole.USER, List.of(new Text(text)));
        }

        public static Msg assistant(String text) {
            return new Msg(ChatRole.ASSISTANT, List.of(new Text(text)));
        }
    }

    record Usage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {
        public static final Usage NONE = new Usage(0, 0, 0, 0);

        public Usage plus(Usage o) {
            return new Usage(inputTokens + o.inputTokens, outputTokens + o.outputTokens,
                    cacheReadTokens + o.cacheReadTokens, cacheWriteTokens + o.cacheWriteTokens);
        }
    }

    /** One assistant turn as the API returned it. */
    record Turn(List<Part> parts, String stopReason, Usage usage) {
        public String text() {
            StringBuilder b = new StringBuilder();
            for (Part p : parts) if (p instanceof Text t) b.append(t.text());
            return b.toString();
        }

        public List<ToolUse> toolUses() {
            return parts.stream().filter(p -> p instanceof ToolUse).map(p -> (ToolUse) p).toList();
        }

        public boolean wantsTools() {
            return "tool_use".equals(stopReason) && !toolUses().isEmpty();
        }
    }

    /**
     * One request. {@code onText} receives the reply text as it streams; the returned turn holds the
     * whole of it plus any tool calls.
     *
     * @throws ChatUnavailableException when the API could not be reached or refused the request
     */
    Turn complete(String system, List<ToolDef> tools, List<Msg> messages, int maxTokens, Consumer<String> onText);
}
