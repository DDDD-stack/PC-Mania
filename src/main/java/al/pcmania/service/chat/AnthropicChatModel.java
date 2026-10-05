package al.pcmania.service.chat;

import al.pcmania.config.ChatProperties;
import al.pcmania.domain.Enums.ChatRole;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@link ChatModel} on the Anthropic Messages API through the official Java SDK. Only created when
 * an API key is configured (see {@link ChatConfig}); the system prompt and the tool list are sent
 * first and marked for prompt caching, so repeat turns pay the cached rate for them.
 */
@Slf4j
public class AnthropicChatModel implements ChatModel {

    private final AnthropicClient client;
    private final ChatProperties props;
    private final ObjectMapper json;

    public AnthropicChatModel(AnthropicClient client, ChatProperties props, ObjectMapper json) {
        this.client = client;
        this.props = props;
        this.json = json;
    }

    @Override
    public Turn complete(String system, List<ToolDef> tools, List<Msg> messages, int maxTokens, Consumer<String> onText) {
        MessageCreateParams params = params(system, tools, messages, maxTokens);
        MessageAccumulator acc = MessageAccumulator.create();
        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
            stream.stream().forEach(event -> {
                acc.accumulate(event);
                event.contentBlockDelta().flatMap(d -> d.delta().text()).ifPresent(t -> onText.accept(t.text()));
            });
        } catch (RateLimitException e) {
            throw new ChatUnavailableException("Anthropic rate limit", true, e);
        } catch (AnthropicServiceException e) {
            log.warn("Anthropic API error {} {}: {}", e.statusCode(), e.errorType().map(Object::toString).orElse(""), e.getMessage());
            throw new ChatUnavailableException("Anthropic API error " + e.statusCode(), e.statusCode() >= 500, e);
        } catch (AnthropicIoException e) {
            throw new ChatUnavailableException("Anthropic unreachable", true, e);
        }
        Message msg = acc.message();
        List<Part> parts = new ArrayList<>();
        for (ContentBlock block : msg.content()) {
            block.text().ifPresent(t -> parts.add(new Text(t.text())));
            block.toolUse().ifPresent(u -> parts.add(new ToolUse(u.id(), u.name(), input(u._input()))));
        }
        Usage usage = new Usage(msg.usage().inputTokens(), msg.usage().outputTokens(),
                msg.usage().cacheReadInputTokens().orElse(0L), msg.usage().cacheCreationInputTokens().orElse(0L));
        return new Turn(parts, msg.stopReason().map(Object::toString).orElse("end_turn"), usage);
    }

    /** The request as the SDK will send it; package-private so a test can check how the parts are built. */
    MessageCreateParams params(String system, List<ToolDef> tools, List<Msg> messages, int maxTokens) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(props.model())
                .maxTokens(maxTokens)
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(system)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()));
        for (ToolDef t : tools) b.addTool(tool(t));
        for (Msg m : messages) b.addMessage(message(m));
        return b.build();
    }

    private JsonNode input(JsonValue v) {
        try {
            Map<String, Object> map = v.convert(new TypeReference<Map<String, Object>>() {});
            return json.valueToTree(map == null ? Map.of() : map);
        } catch (RuntimeException e) {
            // Tool inputs are parsed, never string-matched: an unreadable one becomes an empty object
            // and the tool answers with a validation message the model can act on.
            log.warn("Unreadable tool input: {}", e.toString());
            return json.createObjectNode();
        }
    }

    private Tool tool(ToolDef t) {
        Tool.InputSchema.Builder schema = Tool.InputSchema.builder();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) t.inputSchema().getOrDefault("properties", Map.of());
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        properties.forEach((k, v) -> props.putAdditionalProperty(k, JsonValue.from(v)));
        schema.properties(props.build());
        Object required = t.inputSchema().get("required");
        if (required instanceof List<?> list) schema.required(list.stream().map(Object::toString).toList());
        return Tool.builder().name(t.name()).description(t.description()).inputSchema(schema.build()).build();
    }

    private MessageParam message(Msg m) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (Part p : m.parts()) {
            if (p instanceof Text t) {
                if (!t.text().isEmpty()) blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(t.text()).build()));
            } else if (p instanceof ToolUse u) {
                ToolUseBlockParam.Input.Builder in = ToolUseBlockParam.Input.builder();
                u.input().fields().forEachRemaining(f -> in.putAdditionalProperty(f.getKey(),
                        JsonValue.from(json.convertValue(f.getValue(), Object.class))));
                blocks.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder().id(u.id()).name(u.name()).input(in.build()).build()));
            } else if (p instanceof ToolResult r) {
                blocks.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(r.toolUseId()).content(r.content()).isError(r.error()).build()));
            }
        }
        return MessageParam.builder()
                .role(m.role() == ChatRole.USER ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT)
                .content(MessageParam.Content.ofBlockParams(blocks))
                .build();
    }
}
