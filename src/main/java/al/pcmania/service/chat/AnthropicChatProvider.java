package al.pcmania.service.chat;

import al.pcmania.config.ChatProperties;
import al.pcmania.domain.Enums.ChatRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Anthropic's Messages API, streaming, over plain HTTP. Built and tested but off until
 * {@code ANTHROPIC_API_KEY} is set and {@code CHAT_PROVIDER=anthropic}: switching is only that.
 *
 * Wire format: {@code POST /v1/messages} with {@code stream: true}, headers {@code x-api-key} and
 * {@code anthropic-version: 2023-06-01}; {@code system} as text blocks and {@code tools} with JSON Schema
 * {@code input_schema}, both marked {@code cache_control: ephemeral} so repeat turns read them from the
 * cache. The SSE events are {@code message_start} (input token counts), {@code content_block_start}
 * (a text block or a {@code tool_use} block with id and name), {@code content_block_delta} ({@code text_delta}
 * or {@code input_json_delta} with partial JSON), {@code message_delta} (stop reason, output tokens) and
 * {@code error}. Tool results go back as {@code tool_result} blocks in a user message.
 */
@Component
@Slf4j
public class AnthropicChatProvider extends LlmChatProvider<AnthropicChatProvider.Conversation> {

    public static final String NAME = "anthropic";
    static final String URL = "https://api.anthropic.com/v1/messages";
    static final String VERSION = "2023-06-01";

    public static final class Conversation {
        final String system;
        final ArrayNode messages;

        Conversation(String system, ArrayNode messages) {
            this.system = system;
            this.messages = messages;
        }
    }

    private final StreamingHttp http;
    private final ObjectMapper json;
    private final ChatSpend spend;
    private final String url;

    @Autowired
    public AnthropicChatProvider(ChatProperties props, ChatPrompt prompt, ProviderUsageService usage, StreamingHttp http,
                                 ObjectMapper json, ChatSpend spend) {
        this(props, prompt, usage, http, json, spend, URL);
    }

    AnthropicChatProvider(ChatProperties props, ChatPrompt prompt, ProviderUsageService usage, StreamingHttp http,
                          ObjectMapper json, ChatSpend spend, String url) {
        super(props, prompt, usage);
        this.http = http;
        this.json = json;
        this.spend = spend;
        this.url = url;
    }

    @Override
    public String name() {
        return NAME;
    }

    /** Needs the key, and stays under the month's spend cap. */
    @Override
    public boolean isAvailable() {
        return props.anthropic().keyConfigured() && !spend.overCap();
    }

    @Override
    protected Conversation newConversation(String system, List<HistoryTurn> history, String userMessage) {
        ArrayNode messages = json.createArrayNode();
        for (HistoryTurn t : history) messages.add(message(t.role() == ChatRole.USER ? "user" : "assistant", textBlock(t.text())));
        messages.add(message("user", textBlock(userMessage)));
        return new Conversation(system, messages);
    }

    @Override
    protected ModelTurn call(Conversation conv, ToolRegistry tools, Consumer<String> onText) throws ProviderUnavailableException {
        if (!props.anthropic().keyConfigured()) throw new ProviderUnavailableException("ANTHROPIC_API_KEY is not set", false);
        if (spend.overCap()) throw new ProviderUnavailableException("Anthropic monthly spend cap reached", false);

        ObjectNode body = json.createObjectNode();
        body.put("model", props.anthropic().model());
        body.put("max_tokens", props.maxTokens());
        body.put("stream", true);
        body.set("system", json.createArrayNode().add(cached(textBlock(conv.system))));
        ArrayNode toolNodes = json.createArrayNode();
        List<ToolDef> defs = tools.definitions();
        for (int i = 0; i < defs.size(); i++) {
            ObjectNode t = tool(defs.get(i));
            toolNodes.add(i == defs.size() - 1 ? cached(t) : t); // the breakpoint on the last stable block
        }
        body.set("tools", toolNodes);
        body.set("messages", conv.messages);

        StringBuilder text = new StringBuilder();
        ArrayNode assistantBlocks = json.createArrayNode();
        Map<Integer, ObjectNode> toolBlocks = new HashMap<>();
        Map<Integer, StringBuilder> partialJson = new HashMap<>();
        String[] stop = {null};
        String[] error = {null};
        long[] tokens = {0, 0, 0, 0};
        StreamingHttp.Response res;
        try {
            res = http.postStream(url, Map.of("x-api-key", props.anthropic().apiKey().trim(), "anthropic-version", VERSION,
                    "Content-Type", "application/json"), json.writeValueAsString(body), line -> {
                if (!line.startsWith("data:")) return;
                JsonNode ev;
                try {
                    ev = json.readTree(line.substring(5).trim());
                } catch (JsonProcessingException e) {
                    return;
                }
                switch (ev.path("type").asText()) {
                    case "message_start" -> {
                        JsonNode u = ev.path("message").path("usage");
                        tokens[0] = u.path("input_tokens").asLong(0);
                        tokens[2] = u.path("cache_read_input_tokens").asLong(0);
                        tokens[3] = u.path("cache_creation_input_tokens").asLong(0);
                    }
                    case "content_block_start" -> {
                        int index = ev.path("index").asInt();
                        JsonNode block = ev.path("content_block");
                        if ("tool_use".equals(block.path("type").asText())) {
                            ObjectNode tu = json.createObjectNode();
                            tu.put("type", "tool_use");
                            tu.put("id", block.path("id").asText());
                            tu.put("name", block.path("name").asText());
                            toolBlocks.put(index, tu);
                            partialJson.put(index, new StringBuilder());
                        }
                    }
                    case "content_block_delta" -> {
                        int index = ev.path("index").asInt();
                        JsonNode delta = ev.path("delta");
                        if ("text_delta".equals(delta.path("type").asText())) {
                            String t = delta.path("text").asText();
                            text.append(t);
                            onText.accept(t);
                            mergeText(assistantBlocks, t);
                        } else if ("input_json_delta".equals(delta.path("type").asText())) {
                            partialJson.computeIfAbsent(index, k -> new StringBuilder()).append(delta.path("partial_json").asText());
                        }
                    }
                    case "content_block_stop" -> {
                        int index = ev.path("index").asInt();
                        ObjectNode tu = toolBlocks.get(index);
                        if (tu != null) {
                            tu.set("input", parseInput(partialJson.get(index).toString()));
                            assistantBlocks.add(tu);
                        }
                    }
                    case "message_delta" -> {
                        stop[0] = ev.path("delta").path("stop_reason").asText(null);
                        tokens[1] = ev.path("usage").path("output_tokens").asLong(tokens[1]);
                    }
                    case "error" -> error[0] = ev.path("error").path("message").asText("stream error");
                    default -> { }
                }
            });
        } catch (IOException e) {
            throw new ProviderUnavailableException("Anthropic unreachable: " + e.getMessage(), false, e);
        }
        if (res.status() == 429 || res.status() == 529) throw new ProviderUnavailableException("Anthropic answered " + res.status(), true);
        if (!res.ok()) {
            log.warn("Anthropic answered {}: {}", res.status(), res.errorBody() == null ? "" : res.errorBody().strip());
            throw new ProviderUnavailableException("Anthropic answered " + res.status() + ": "
                    + (res.errorBody() == null ? "" : res.errorBody().strip()), false);
        }
        if (error[0] != null && text.isEmpty() && toolBlocks.isEmpty()) {
            throw new ProviderUnavailableException("Anthropic stream error: " + error[0], "overloaded_error".equals(error[0]));
        }
        List<Call> calls = new ArrayList<>();
        for (JsonNode b : assistantBlocks) {
            if ("tool_use".equals(b.path("type").asText())) calls.add(new Call(b.get("id").asText(), b.get("name").asText(), b.get("input")));
        }
        if (assistantBlocks.isEmpty()) assistantBlocks.add(textBlock(""));
        conv.messages.add(message("assistant", assistantBlocks));
        ModelTurn turn = new ModelTurn(text.toString(), "tool_use".equals(stop[0]) ? calls : List.of(),
                "max_tokens".equals(stop[0]), tokens[0], tokens[1], tokens[2], tokens[3]);
        return turn;
    }

    @Override
    protected void onUsage(ModelTurn turn) {
        spend.record(turn.inputTokens(), turn.outputTokens(), turn.cacheReadTokens(), turn.cacheWriteTokens());
    }

    @Override
    protected void addToolResults(Conversation conv, List<Call> calls, List<String> resultsJson, List<Boolean> errors) {
        ArrayNode blocks = json.createArrayNode();
        for (int i = 0; i < calls.size(); i++) {
            ObjectNode r = json.createObjectNode();
            r.put("type", "tool_result");
            r.put("tool_use_id", calls.get(i).id());
            r.put("content", resultsJson.get(i));
            if (errors.get(i)) r.put("is_error", true);
            blocks.add(r);
        }
        conv.messages.add(message("user", blocks));
    }

    // ---- Wire helpers ----

    private ObjectNode textBlock(String text) {
        return json.createObjectNode().put("type", "text").put("text", text);
    }

    private ObjectNode cached(ObjectNode block) {
        block.set("cache_control", json.createObjectNode().put("type", "ephemeral"));
        return block;
    }

    private ObjectNode message(String role, JsonNode content) {
        ArrayNode blocks = content.isArray() ? (ArrayNode) content : json.createArrayNode().add(content);
        return (ObjectNode) json.createObjectNode().put("role", role).set("content", blocks);
    }

    private void mergeText(ArrayNode blocks, String t) {
        if (!blocks.isEmpty() && "text".equals(blocks.get(blocks.size() - 1).path("type").asText())) {
            ObjectNode last = (ObjectNode) blocks.get(blocks.size() - 1);
            last.put("text", last.get("text").asText() + t);
        } else {
            blocks.add(textBlock(t));
        }
    }

    private JsonNode parseInput(String partial) {
        if (partial == null || partial.isBlank()) return json.createObjectNode();
        try {
            JsonNode n = json.readTree(partial);
            return n.isObject() ? n : json.createObjectNode();
        } catch (JsonProcessingException e) {
            log.warn("Unreadable tool input from Anthropic: {}", e.getMessage());
            return json.createObjectNode();
        }
    }

    /** A {@link ToolDef} as an Anthropic tool with a JSON Schema input. */
    ObjectNode tool(ToolDef d) {
        ObjectNode properties = json.createObjectNode();
        d.params().forEach((name, p) -> {
            ObjectNode prop = json.createObjectNode();
            prop.put("type", p.type());
            prop.put("description", p.description());
            if (p.enumValues() != null) {
                ArrayNode values = json.createArrayNode();
                p.enumValues().forEach(values::add);
                prop.set("enum", values);
            }
            properties.set(name, prop);
        });
        ObjectNode schema = json.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", properties);
        ArrayNode required = json.createArrayNode();
        d.required().forEach(required::add);
        schema.set("required", required);
        ObjectNode t = json.createObjectNode();
        t.put("name", d.name());
        t.put("description", d.description());
        t.set("input_schema", schema);
        return t;
    }
}
