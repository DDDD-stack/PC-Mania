package al.pcmania.service.chat;

import al.pcmania.config.ChatProperties;
import al.pcmania.domain.ChatMessage;
import al.pcmania.domain.ChatSession;
import al.pcmania.domain.Enums.ChatRole;
import al.pcmania.repo.ChatMessageRepository;
import al.pcmania.repo.ChatSessionRepository;
import al.pcmania.service.CatalogService;
import al.pcmania.service.ImageStorage;
import al.pcmania.service.RateLimiter;
import al.pcmania.web.Fmt;
import al.pcmania.web.Img;
import al.pcmania.web.view.ProductCard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * One customer message in, one reply out. Guardrails first (session length, per-address rate), then
 * the message is stripped of contact details, stored, and handed to the chosen provider; when that
 * provider is unavailable or rate-limited the guided finder answers instead and the fallback is
 * counted. Nothing is held open against the database while a provider works: the customer's message
 * is saved before, the reply after.
 */
@Service
@Slf4j
public class ChatService {

    /** A product the reply mentions, as the widget draws it under the text. */
    public record Card(String slug, String title, String price, String condition, String image, String url) {}

    record ToolCallsJson(List<Map<String, Object>> calls, List<String> products) {}

    static final int MAX_MESSAGE_CHARS = 1500;
    static final String LIMIT_REPLY = "Kemi biseduar gjatë dhe asistenti ndalet këtu. Për të vazhduar na shkruani në WhatsApp – ju përgjigjet një person.";
    static final String RATE_REPLY = "Keni dërguar shumë mesazhe në pak kohë. Provoni më vonë ose na shkruani në WhatsApp.";
    static final String PII_NOTICE = "Hoqa numrin e telefonit / emailin nga mesazhi: të dhënat e kontaktit shkruhen vetëm në formularin e faqes, jo në bisedë.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ChatProperties props;
    private final Map<String, ChatProvider> providers = new HashMap<>();
    private final GuidedFinderProvider guided;
    private final ToolRegistry tools;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final ProviderUsageService usage;
    private final RateLimiter rateLimiter;
    private final CatalogService catalog;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public ChatService(ChatProperties props, List<ChatProvider> providerBeans, GuidedFinderProvider guided, ToolRegistry tools,
                       ChatSessionRepository sessions, ChatMessageRepository messages, ProviderUsageService usage,
                       RateLimiter rateLimiter, CatalogService catalog, TransactionTemplate tx, ObjectMapper json) {
        this.props = props;
        for (ChatProvider p : providerBeans) providers.put(p.name(), p);
        this.guided = guided;
        this.tools = tools;
        this.sessions = sessions;
        this.messages = messages;
        this.usage = usage;
        this.rateLimiter = rateLimiter;
        this.catalog = catalog;
        this.tx = tx;
        this.json = json;
    }

    /** The provider {@code CHAT_PROVIDER} names; an unknown name means the finder. */
    public ChatProvider primary() {
        ChatProvider p = providers.get(props.provider() == null ? "" : props.provider().trim().toLowerCase());
        if (p == null) {
            log.warn("CHAT_PROVIDER={} is not a provider; using the guided finder", props.provider());
            return guided;
        }
        return p;
    }

    /** The session behind a browser's cookie, or a fresh one when the cookie is missing or unknown. */
    public ChatSession session(String token) {
        if (token != null && token.length() >= 32 && token.length() <= 64) {
            Optional<ChatSession> existing = sessions.findBySessionToken(token);
            if (existing.isPresent()) return existing.get();
        }
        ChatSession s = new ChatSession();
        s.setSessionToken(newToken());
        return sessions.save(s);
    }

    /** The session for a cookie that exists, without creating one. */
    public Optional<ChatSession> existingSession(String token) {
        if (token == null || token.length() < 32 || token.length() > 64) return Optional.empty();
        return sessions.findBySessionToken(token);
    }

    public List<ChatMessage> transcript(ChatSession session) {
        return messages.findBySessionOrderByIdAsc(session);
    }

    public boolean limitReached(ChatSession session) {
        return session.getMessageCount() >= props.maxMessagesPerSession();
    }

    /** Cards for the slugs an assistant message surfaced, for replaying a transcript. */
    public List<Card> cardsOf(ChatMessage m) {
        if (m.getToolCallsJson() == null) return List.of();
        try {
            return cards(json.readValue(m.getToolCallsJson(), ToolCallsJson.class).products());
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    /** Answers into {@code out} and completes it. Runs on the chat executor, off the request thread. */
    public void reply(ChatSession session, String rawMessage, String clientAddress, ChatStream out) {
        try {
            String text = rawMessage == null ? "" : rawMessage.strip();
            if (text.length() > MAX_MESSAGE_CHARS) text = text.substring(0, MAX_MESSAGE_CHARS);
            if (text.isEmpty()) {
                out.event("done", Map.of());
                return;
            }
            if (limitReached(session)) {
                out.event("limit", Map.of("text", LIMIT_REPLY));
                return;
            }
            if (!rateLimiter.tryAcquire("chat:" + clientAddress, props.maxPerHourPerIp(), Duration.ofHours(1))) {
                out.event("error", Map.of("message", RATE_REPLY));
                return;
            }
            PiiStripper.Result stripped = PiiStripper.strip(text);
            if (stripped.stripped()) out.notice(PII_NOTICE);
            String message = stripped.text();

            // The customer's message is on record before any provider is asked, whatever happens next.
            List<ChatMessage> history = new ArrayList<>();
            tx.executeWithoutResult(s -> {
                ChatSession fresh = sessions.findById(session.getId()).orElseThrow();
                fresh.setMessageCount(fresh.getMessageCount() + 1);
                fresh.setLastMessageAt(LocalDateTime.now());
                history.addAll(messages.findBySessionOrderByIdAsc(fresh));
                ChatMessage um = new ChatMessage();
                um.setSession(fresh);
                um.setRole(ChatRole.USER);
                um.setContent(message);
                messages.save(um);
                session.setMessageCount(fresh.getMessageCount());
            });

            long started = System.currentTimeMillis();
            String provider = answer(session, message, history, out);
            List<Card> cards = mentioned(out.text(), cards(out.surfacedSlugs()));
            if (!cards.isEmpty()) out.products(cards);
            persistReply(session, out, cards, provider);
            out.event("done", Map.of("remaining", Math.max(0, props.maxMessagesPerSession() - session.getMessageCount())));
            log.info("Assistant reply: session {} by {} with {} tool calls in {} ms", session.getId(), provider,
                    out.toolCalls().size(), System.currentTimeMillis() - started);
        } catch (RuntimeException e) {
            log.error("Assistant reply failed for session {}", session.getId(), e);
            out.event("error", Map.of("message", "Diçka shkoi keq. Provo përsëri ose na shkruaj në WhatsApp."));
        } finally {
            out.complete();
        }
    }

    /** The chain: the chosen provider, then the finder. Returns who answered. */
    private String answer(ChatSession session, String message, List<ChatMessage> history, ChatStream out) {
        ChatProvider p = primary();
        if (p != guided) {
            if (p.isAvailable()) {
                try {
                    p.stream(session, message, history, tools, out);
                    return p.name();
                } catch (ProviderUnavailableException e) {
                    log.warn("{} unavailable ({}); the guided finder answers session {}", p.name(), e.getMessage(), session.getId());
                }
            } else {
                log.info("{} is not available; the guided finder answers session {}", p.name(), session.getId());
            }
            usage.fallback();
            guided.stream(out, true);
        } else {
            usage.request(GuidedFinderProvider.NAME);
            guided.stream(out, false);
        }
        return GuidedFinderProvider.NAME;
    }

    private void persistReply(ChatSession session, ChatStream out, List<Card> cards, String provider) {
        tx.executeWithoutResult(s -> {
            ChatSession fresh = sessions.findById(session.getId()).orElseThrow();
            fresh.setLastMessageAt(LocalDateTime.now());
            fresh.setProviderUsed(provider);
            ChatMessage am = new ChatMessage();
            am.setSession(fresh);
            am.setRole(ChatRole.ASSISTANT);
            am.setContent(out.finderShown() && out.text().isBlank() ? "[kërkimi i shpejtë]" : out.text().strip());
            am.setProviderUsed(provider);
            try {
                am.setToolCallsJson(out.toolCalls().isEmpty() && cards.isEmpty() ? null
                        : json.writeValueAsString(new ToolCallsJson(out.toolCalls(), cards.stream().map(Card::slug).toList())));
            } catch (JsonProcessingException e) {
                am.setToolCallsJson(null);
            }
            messages.save(am);
        });
    }

    /**
     * The surfaced products the reply actually talks about: a tool may list six cards while the reply
     * names two. A product counts as mentioned when its slug or its whole title is in the text, or most
     * of the title's words are (models tend to shorten "MSI RTX 3060 Ti Ventus 2X 8GB OC").
     */
    static List<Card> mentioned(String text, List<Card> cards) {
        String t = text.toLowerCase(Locale.ROOT);
        List<Card> out = new ArrayList<>();
        for (Card c : cards) {
            String title = c.title().toLowerCase(Locale.ROOT);
            if (t.contains(c.slug()) || t.contains(title)) {
                out.add(c);
                continue;
            }
            String[] words = title.split("\\s+");
            int significant = 0, found = 0;
            for (String w : words) {
                if (w.length() < 2) continue;
                significant++;
                if (t.contains(w)) found++;
            }
            if (significant > 0 && found * 10 >= significant * 6) out.add(c);
        }
        return out;
    }

    public List<Card> cards(List<String> slugs) {
        if (slugs.isEmpty()) return List.of();
        Map<String, ProductCard> bySlug = new HashMap<>();
        for (ProductCard c : catalog.cardsBySlugs(slugs)) bySlug.put(c.slug(), c);
        List<Card> out = new ArrayList<>();
        for (String slug : slugs) {
            ProductCard c = bySlug.get(slug);
            if (c == null) continue;
            String image = c.imageFilename() == null ? Img.PLACEHOLDER : ImageStorage.url(ImageStorage.Size.thumb, c.imageFilename());
            out.add(new Card(c.slug(), c.title(), Fmt.lek(c.priceLek()), c.condition().label, image, "/produkt/" + c.slug()));
        }
        return out;
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
