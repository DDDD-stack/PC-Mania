package al.pcmania.web.chat;

import al.pcmania.domain.ChatMessage;
import al.pcmania.domain.ChatSession;
import al.pcmania.domain.Enums.ChatRole;
import al.pcmania.service.SeoService;
import al.pcmania.service.chat.ChatService;
import al.pcmania.service.chat.ChatService.Card;
import al.pcmania.service.chat.ChatStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api/chat")
public class ChatApiController {

    public record Ask(String message) {}

    private static final long TIMEOUT_MS = 120_000;
    private static final String WHATSAPP_TEXT = "Përshëndetje PCMania! Po shkruaja me asistentin dhe dua të vazhdoj me një person.";

    private final ChatService chat;
    private final ChatCookie cookie;
    private final SeoService seo;
    private final ExecutorService executor;
    private final ObjectMapper json;

    public ChatApiController(ChatService chat, ChatCookie cookie, SeoService seo,
                             @Qualifier("chatExecutor") ExecutorService executor, ObjectMapper json) {
        this.chat = chat;
        this.cookie = cookie;
        this.seo = seo;
        this.executor = executor;
        this.json = json;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public ResponseEntity<ChatStream> ask(@RequestBody Ask ask, HttpServletRequest request) {
        if (ask == null || ask.message() == null || ask.message().isBlank()) return ResponseEntity.badRequest().build();
        String token = cookie.read(request);
        ChatSession session = chat.session(token);
        String address = request.getRemoteAddr();
        ChatStream out = new ChatStream(json, TIMEOUT_MS);
        executor.submit(() -> chat.reply(session, ask.message(), address, out));
        ResponseEntity.BodyBuilder res = ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (!session.getSessionToken().equals(token)) res.header(HttpHeaders.SET_COOKIE, cookie.build(session.getSessionToken()).toString());
        return res.body(out);
    }

    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> history(HttpServletRequest request) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("whatsapp", seo.whatsappLink(WHATSAPP_TEXT));
        out.put("provider", chat.primary().name());
        Optional<ChatSession> session = chat.existingSession(cookie.read(request));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (session.isPresent()) {
            for (ChatMessage m : chat.transcript(session.get())) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("role", m.getRole() == ChatRole.USER ? "user" : "assistant");
                row.put("content", m.getContent());
                List<Card> cards = chat.cardsOf(m);
                if (!cards.isEmpty()) row.put("products", cards);
                rows.add(row);
            }
        }
        out.put("messages", rows);
        out.put("limitReached", session.isPresent() && chat.limitReached(session.get()));
        return out;
    }
}
