package al.pcmania.web.chat;

import al.pcmania.domain.ChatMessage;
import al.pcmania.domain.ChatSession;
import al.pcmania.domain.Enums.ChatRole;
import al.pcmania.config.ChatProperties;
import al.pcmania.service.SeoService;
import al.pcmania.service.chat.ChatAssistant;
import al.pcmania.service.chat.ChatAssistant.Card;
import al.pcmania.service.chat.ChatAssistant.Outcome;
import al.pcmania.service.chat.ChatAssistant.Reply;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * The widget's API. {@code POST /api/chat} answers as a stream of server-sent events:
 * {@code session} (the token to keep), {@code status} (what the assistant is doing), {@code delta}
 * (reply text as it arrives), {@code products} (cards for what it mentioned), then {@code done},
 * {@code limit} or {@code error}. {@code GET /api/chat/history} replays a conversation after a page load.
 */
@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatApiController {

    public record Ask(String token, String message) {}

    private static final long TIMEOUT_MS = 120_000;
    private static final String WHATSAPP_TEXT = "Përshëndetje PCMania! Po shkruaja me asistentin dhe dua të vazhdoj me një person.";

    private final ChatAssistant assistant;
    private final ChatProperties props;
    private final SeoService seo;
    private final ExecutorService executor;
    private final ObjectMapper json;

    public ChatApiController(ChatAssistant assistant, ChatProperties props, SeoService seo,
                             @Qualifier("chatExecutor") ExecutorService executor, ObjectMapper json) {
        this.assistant = assistant;
        this.props = props;
        this.seo = seo;
        this.executor = executor;
        this.json = json;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public ResponseEntity<SseEmitter> ask(@RequestBody Ask ask, HttpServletRequest request) {
        if (ask == null || ask.message() == null || ask.message().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        if (!assistant.available()) {
            send(emitter, "error", Map.of("message", "Asistenti nuk është i disponueshëm tani. Na shkruani në WhatsApp.", "whatsapp", whatsapp()));
            emitter.complete();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(emitter);
        }
        ChatSession session = assistant.session(ask.token());
        String address = request.getRemoteAddr();
        send(emitter, "session", Map.of("token", session.getSessionToken()));
        executor.submit(() -> {
            try {
                Reply reply = assistant.reply(session, ask.message(), address, new ChatAssistant.Events() {
                    @Override
                    public void status(String textSq) {
                        send(emitter, "status", Map.of("text", textSq));
                    }

                    @Override
                    public void text(String delta) {
                        if (!delta.isEmpty()) send(emitter, "delta", Map.of("text", delta));
                    }
                });
                switch (reply.outcome()) {
                    case OK -> {
                        if (!reply.products().isEmpty()) send(emitter, "products", Map.of("items", reply.products()));
                        send(emitter, "done", Map.of("remaining", Math.max(0, props.maxMessagesPerSession() - session.getMessageCount())));
                    }
                    case SESSION_LIMIT -> send(emitter, "limit", Map.of("text", reply.text(), "whatsapp", whatsapp()));
                    case RATE_LIMIT -> send(emitter, "error", Map.of("message", reply.text(), "whatsapp", whatsapp()));
                    case UNAVAILABLE -> send(emitter, "error", Map.of("message",
                            "Asistenti nuk po përgjigjet për momentin. Provoni përsëri pas pak ose na shkruani në WhatsApp.", "whatsapp", whatsapp()));
                }
                emitter.complete();
            } catch (RuntimeException e) {
                log.error("Assistant reply failed for session {}", session.getId(), e);
                send(emitter, "error", Map.of("message", "Diçka shkoi keq. Na shkruani në WhatsApp.", "whatsapp", whatsapp()));
                emitter.complete();
            }
        });
        return ResponseEntity.ok().header("X-Accel-Buffering", "no").header("Cache-Control", "no-store").body(emitter);
    }

    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> history(@RequestParam(required = false) String token) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean available = assistant.available();
        out.put("available", available);
        out.put("whatsapp", whatsapp());
        if (!available || token == null || token.isBlank()) {
            out.put("messages", List.of());
            return out;
        }
        ChatSession session = assistant.session(token);
        out.put("token", session.getSessionToken());
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage m : assistant.transcript(session)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", m.getRole() == ChatRole.USER ? "user" : "assistant");
            row.put("content", m.getContent());
            List<Card> cards = assistant.cardsOf(m);
            if (!cards.isEmpty()) row.put("products", cards);
            messages.add(row);
        }
        out.put("messages", messages);
        out.put("limitReached", session.getMessageCount() >= props.maxMessagesPerSession());
        return out;
    }

    private String whatsapp() {
        return seo.whatsappLink(WHATSAPP_TEXT);
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(json.writeValueAsString(data), MediaType.TEXT_PLAIN));
        } catch (IOException | IllegalStateException e) {
            // The browser went away (closed the panel, navigated): nothing to do, the reply is still saved.
            log.debug("SSE send failed: {}", e.toString());
        }
    }
}
