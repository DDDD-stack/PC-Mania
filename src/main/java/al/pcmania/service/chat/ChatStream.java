package al.pcmania.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
public class ChatStream extends SseEmitter {

    private final ObjectMapper json;
    private final StringBuilder text = new StringBuilder();
    private final LinkedHashSet<String> surfaced = new LinkedHashSet<>();
    private final List<Map<String, Object>> toolCalls = new ArrayList<>();
    private JsonNode action;
    private boolean finderShown;

    public ChatStream(ObjectMapper json, long timeoutMs) {
        super(timeoutMs);
        this.json = json;
    }

    public void delta(String s) {
        if (s == null || s.isEmpty()) return;
        text.append(s);
        send("delta", Map.of("text", s));
    }

    public void status(String textSq) {
        send("status", Map.of("text", textSq));
    }

    public void surfaced(List<String> slugs) {
        surfaced.addAll(slugs);
    }

    public void toolCall(String name, JsonNode args, List<String> slugs, boolean error) {
        toolCalls.add(Map.of("name", name, "input", args == null ? json.createObjectNode() : args, "products", slugs, "error", error));
    }

    public void action(JsonNode action) {
        this.action = action;
        send("action", action);
    }

    public void notice(String textSq) {
        text.append(text.isEmpty() ? "" : "\n").append(textSq);
        send("notice", Map.of("text", textSq));
    }

    public void finder(Object firstStep) {
        finderShown = true;
        send("finder", firstStep);
    }

    public void products(List<?> cards) {
        send("products", Map.of("items", cards));
    }

    public void event(String name, Object data) {
        send(name, data);
    }

    public String text() {
        return text.toString();
    }

    public List<String> surfacedSlugs() {
        return new ArrayList<>(surfaced);
    }

    public List<Map<String, Object>> toolCalls() {
        return toolCalls;
    }

    public JsonNode action() {
        return action;
    }

    public boolean finderShown() {
        return finderShown;
    }

    private void send(String event, Object data) {
        try {
            super.send(SseEmitter.event().name(event).data(json.writeValueAsString(data), MediaType.TEXT_PLAIN));
        } catch (IOException | IllegalStateException e) {

            log.debug("SSE send failed: {}", e.toString());
        }
    }
}
