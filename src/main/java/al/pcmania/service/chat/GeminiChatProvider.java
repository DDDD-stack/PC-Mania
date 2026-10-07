package al.pcmania.service.chat;

import al.pcmania.config.ChatProperties;
import al.pcmania.domain.Enums.ChatRole;
import al.pcmania.service.chat.ToolDef.ParamSpec;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gemini through the Generative Language REST API, streaming with {@code alt=sse}. The free tier's
 * limits are handled here: a token bucket under the per-minute cap, and a 429 or a refused token
 * becomes a {@link ProviderUnavailableException} so the chain falls through to the guided finder
 * instead of showing an error.
 *
 * Wire format (generativelanguage.googleapis.com, v1beta): the request carries {@code systemInstruction},
 * {@code contents[]} with roles {@code user} and {@code model}, {@code tools[0].functionDeclarations[]}
 * and {@code generationConfig}; each SSE {@code data:} line is a GenerateContentResponse whose
 * {@code candidates[0].content.parts[]} hold {@code text} or {@code functionCall{name,args}} parts. A
 * function's result goes back as a {@code functionResponse{name,response}} part. The model's parts are
 * echoed back verbatim on the next round, which keeps any {@code thoughtSignature} they carry.
 */
@Component
@Slf4j
public class GeminiChatProvider extends LlmChatProvider<GeminiChatProvider.Conversation> {

    public static final String NAME = "gemini";
    static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    /** The request's {@code contents} array, built up as the loop runs. */
    public static final class Conversation {
        final String system;
        final ArrayNode contents;

        Conversation(String system, ArrayNode contents) {
            this.system = system;
            this.contents = contents;
        }
    }

    private final StreamingHttp http;
    private final ObjectMapper json;
    private final TokenBucket bucket;
    private final String baseUrl;

    @Autowired
    public GeminiChatProvider(ChatProperties props, ChatPrompt prompt, ProviderUsageService usage, StreamingHttp http, ObjectMapper json) {
        this(props, prompt, usage, http, json, BASE_URL);
    }

    GeminiChatProvider(ChatProperties props, ChatPrompt prompt, ProviderUsageService usage, StreamingHttp http, ObjectMapper json, String baseUrl) {
        super(props, prompt, usage);
        this.http = http;
        this.json = json;
        this.bucket = new TokenBucket(Math.max(1, props.gemini().requestsPerMinute()));
        this.baseUrl = baseUrl;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        return props.gemini().keyConfigured() && bucket.hasToken();
    }

    @Override
    protected Conversation newConversation(String system, List<HistoryTurn> history, String userMessage) {
        ArrayNode contents = json.createArrayNode();
        for (HistoryTurn t : history) contents.add(content(t.role() == ChatRole.USER ? "user" : "model", textPart(t.text())));
        contents.add(content("user", textPart(userMessage)));
        return new Conversation(system, contents);
    }

    @Override
    protected ModelTurn call(Conversation conv, ToolRegistry tools, Consumer<String> onText) throws ProviderUnavailableException {
        if (!props.gemini().keyConfigured()) throw new ProviderUnavailableException("GEMINI_API_KEY is not set", false);
        if (!bucket.tryAcquire()) throw new ProviderUnavailableException("Gemini: local rate limiter refused the request", true);

        ObjectNode body = json.createObjectNode();
        body.set("systemInstruction", json.createObjectNode().set("parts", json.createArrayNode().add(textPart(conv.system))));
        body.set("contents", conv.contents);
        ArrayNode declarations = json.createArrayNode();
        for (ToolDef d : tools.definitions()) declarations.add(declaration(d));
        body.set("tools", json.createArrayNode().add(json.createObjectNode().set("functionDeclarations", declarations)));
        body.set("generationConfig", json.createObjectNode().put("maxOutputTokens", props.maxTokens()));

        StringBuilder text = new StringBuilder();
        ArrayNode modelParts = json.createArrayNode();
        List<Call> calls = new ArrayList<>();
        String[] finish = {null};
        long[] tokens = {0, 0};
        StreamingHttp.Response res;
        try {
            res = http.postStream(baseUrl + props.gemini().model() + ":streamGenerateContent?alt=sse",
                    Map.of("x-goog-api-key", props.gemini().apiKey().trim(), "Content-Type", "application/json"),
                    json.writeValueAsString(body), line -> {
                        if (!line.startsWith("data:")) return;
                        JsonNode chunk;
                        try {
                            chunk = json.readTree(line.substring(5).trim());
                        } catch (JsonProcessingException e) {
                            return; // a keep-alive or a partial line
                        }
                        JsonNode candidate = chunk.path("candidates").path(0);
                        for (JsonNode part : candidate.path("content").path("parts")) {
                            if (part.has("text") && !part.path("thought").asBoolean(false)) {
                                String t = part.get("text").asText();
                                text.append(t);
                                onText.accept(t);
                                mergeText(modelParts, t);
                            } else if (part.has("functionCall")) {
                                JsonNode fc = part.get("functionCall");
                                calls.add(new Call(fc.path("id").isMissingNode() ? null : fc.get("id").asText(),
                                        fc.path("name").asText(), fc.path("args").isObject() ? fc.get("args") : json.createObjectNode()));
                                modelParts.add(part); // verbatim, thoughtSignature included
                            }
                        }
                        if (candidate.hasNonNull("finishReason")) finish[0] = candidate.get("finishReason").asText();
                        JsonNode um = chunk.path("usageMetadata");
                        if (um.isObject()) {
                            tokens[0] = um.path("promptTokenCount").asLong(tokens[0]);
                            tokens[1] = um.path("candidatesTokenCount").asLong(tokens[1]);
                        }
                    });
        } catch (IOException e) {
            throw new ProviderUnavailableException("Gemini unreachable: " + e.getMessage(), false, e);
        }
        if (res.status() == 429) throw new ProviderUnavailableException("Gemini rate limit (429)", true);
        if (!res.ok()) {
            log.warn("Gemini answered {}: {}", res.status(), abbreviate(res.errorBody()));
            throw new ProviderUnavailableException("Gemini answered " + res.status() + ": " + abbreviate(res.errorBody()), false);
        }
        if ("MALFORMED_FUNCTION_CALL".equals(finish[0])) {
            log.warn("Gemini produced a malformed function call; answering without tools this turn");
            calls.clear();
        }
        if (modelParts.isEmpty()) modelParts.add(textPart(""));
        conv.contents.add(content("model", modelParts));
        return new ModelTurn(text.toString(), calls, "MAX_TOKENS".equals(finish[0]), tokens[0], tokens[1], 0, 0);
    }

    @Override
    protected void addToolResults(Conversation conv, List<Call> calls, List<String> resultsJson, List<Boolean> errors) {
        ArrayNode parts = json.createArrayNode();
        for (int i = 0; i < calls.size(); i++) {
            ObjectNode response = json.createObjectNode();
            try {
                JsonNode parsed = json.readTree(resultsJson.get(i));
                if (parsed.isObject()) response = (ObjectNode) parsed;
                else response.set("result", parsed);
            } catch (JsonProcessingException e) {
                response.put("result", resultsJson.get(i));
            }
            ObjectNode fr = json.createObjectNode();
            fr.put("name", calls.get(i).name());
            if (calls.get(i).id() != null) fr.put("id", calls.get(i).id());
            fr.set("response", response);
            parts.add(json.createObjectNode().set("functionResponse", fr));
        }
        conv.contents.add(content("user", parts));
    }

    // ---- Wire helpers ----

    private ObjectNode textPart(String text) {
        return json.createObjectNode().put("text", text);
    }

    private ObjectNode content(String role, JsonNode part) {
        ArrayNode parts = part.isArray() ? (ArrayNode) part : json.createArrayNode().add(part);
        return (ObjectNode) json.createObjectNode().put("role", role).set("parts", parts);
    }

    /** Streamed text arrives in pieces; the echoed model turn keeps it as one part. */
    private void mergeText(ArrayNode parts, String t) {
        if (!parts.isEmpty() && parts.get(parts.size() - 1).has("text") && !parts.get(parts.size() - 1).has("thoughtSignature")) {
            ObjectNode last = (ObjectNode) parts.get(parts.size() - 1);
            last.put("text", last.get("text").asText() + t);
        } else {
            parts.add(textPart(t));
        }
    }

    /** A {@link ToolDef} as a Gemini function declaration (OpenAPI-style schema, upper-case types). */
    ObjectNode declaration(ToolDef d) {
        ObjectNode properties = json.createObjectNode();
        d.params().forEach((name, p) -> {
            ObjectNode prop = json.createObjectNode();
            prop.put("type", p.type().toUpperCase(Locale.ROOT));
            prop.put("description", p.description());
            if (p.enumValues() != null) {
                ArrayNode values = json.createArrayNode();
                p.enumValues().forEach(values::add);
                prop.set("enum", values);
            }
            properties.set(name, prop);
        });
        ObjectNode parameters = json.createObjectNode();
        parameters.put("type", "OBJECT");
        parameters.set("properties", properties);
        ArrayNode required = json.createArrayNode();
        d.required().forEach(required::add);
        if (!required.isEmpty()) parameters.set("required", required);
        ObjectNode decl = json.createObjectNode();
        decl.put("name", d.name());
        decl.put("description", d.description());
        decl.set("parameters", parameters);
        return decl;
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    /** For tests and the admin: the schema type names a ParamSpec maps to. */
    static String geminiType(ParamSpec p) {
        return p.type().toUpperCase(Locale.ROOT);
    }
}
