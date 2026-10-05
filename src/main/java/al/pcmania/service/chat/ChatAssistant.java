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
import al.pcmania.service.chat.ChatModel.*;
import al.pcmania.web.Fmt;
import al.pcmania.web.Img;
import al.pcmania.web.view.ProductCard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The assistant's conversation loop: one customer message in, one reply out, with up to
 * {@link ChatProperties#maxToolRounds()} rounds of tool calls in between. Guardrails first (session
 * length, per-address rate, monthly spend), then the model, streaming its text to the caller as it
 * arrives and announcing each tool while it runs. Nothing is held open against the database while
 * the model is working: the customer's message is saved before, the reply after.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatAssistant {

    public enum Outcome { OK, SESSION_LIMIT, RATE_LIMIT, UNAVAILABLE }

    /** A product the reply mentions, as the widget draws it under the text. */
    public record Card(String slug, String title, String price, String condition, String image, String url) {}

    public record Reply(Outcome outcome, String text, List<Card> products) {
        static Reply of(Outcome o, String text) {
            return new Reply(o, text, List.of());
        }
    }

    /** What the caller hears while a reply is being made. */
    public interface Events {
        void status(String textSq);
        void text(String delta);
    }

    /** How a session's turns are kept, including the tool calls behind a reply. */
    record ToolCallRecord(String name, JsonNode input, List<String> products, boolean error) {}

    record ToolCallsJson(List<ToolCallRecord> calls, List<String> products) {}

    /** Earlier turns replayed to the model; older ones are dropped, the transcript stays in the database. */
    static final int HISTORY_TURNS = 20;
    static final int MAX_MESSAGE_CHARS = 1500;
    static final String FALLBACK_REPLY = "Më vjen keq, nuk arrita ta formuloj përgjigjen. Na shkruani në WhatsApp dhe ju përgjigjemi personalisht.";
    static final String LIMIT_REPLY = "Kemi biseduar gjatë dhe asistenti ndalet këtu. Për të vazhduar na shkruani në WhatsApp – ju përgjigjet një person.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectProvider<ChatModel> model;
    private final ChatProperties props;
    private final ChatPrompt prompt;
    private final ChatToolDefs toolDefs;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final ChatSpend spend;
    private final RateLimiter rateLimiter;
    private final CatalogService catalog;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    /** Whether a model is configured at all. */
    public boolean configured() {
        return model.getIfAvailable() != null;
    }

    /** Configured and under this month's spend cap: the widget is shown only then. */
    public boolean available() {
        return configured() && !spend.overCap();
    }

    /** The session behind a browser's token, or a fresh one when the token is missing or unknown. */
    public ChatSession session(String token) {
        if (token != null && token.length() >= 32 && token.length() <= 64) {
            Optional<ChatSession> existing = sessions.findBySessionToken(token);
            if (existing.isPresent()) return existing.get();
        }
        ChatSession s = new ChatSession();
        s.setSessionToken(newToken());
        return sessions.save(s);
    }

    public List<ChatMessage> transcript(ChatSession session) {
        return messages.findBySessionOrderByIdAsc(session);
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

    public Reply reply(ChatSession session, String userText, String clientAddress, Events events) {
        String text = userText == null ? "" : userText.strip();
        if (text.isEmpty()) return Reply.of(Outcome.OK, "");
        if (text.length() > MAX_MESSAGE_CHARS) text = text.substring(0, MAX_MESSAGE_CHARS);
        ChatModel m = model.getIfAvailable();
        if (m == null || spend.overCap()) return Reply.of(Outcome.UNAVAILABLE, "");
        if (session.getMessageCount() >= props.maxMessagesPerSession()) return Reply.of(Outcome.SESSION_LIMIT, LIMIT_REPLY);
        if (!rateLimiter.tryAcquire("chat:" + clientAddress, props.maxPerHourPerIp(), Duration.ofHours(1))) {
            return Reply.of(Outcome.RATE_LIMIT, "Keni dërguar shumë mesazhe në pak kohë. Provoni më vonë ose na shkruani në WhatsApp.");
        }

        // The customer's message is on record before the model is asked, whatever happens next.
        List<Msg> history = new ArrayList<>();
        final String userMessage = text;
        tx.executeWithoutResult(s -> {
            ChatSession fresh = sessions.findById(session.getId()).orElseThrow();
            fresh.setMessageCount(fresh.getMessageCount() + 1);
            fresh.setLastMessageAt(LocalDateTime.now());
            history.addAll(historyOf(fresh));
            ChatMessage um = new ChatMessage();
            um.setSession(fresh);
            um.setRole(ChatRole.USER);
            um.setContent(userMessage);
            messages.save(um);
            session.setMessageCount(fresh.getMessageCount());
        });
        history.add(Msg.user(userMessage));

        List<ToolCallRecord> calls = new ArrayList<>();
        LinkedHashSet<String> surfaced = new LinkedHashSet<>();
        StringBuilder replyText = new StringBuilder();
        Usage usage = Usage.NONE;
        long started = System.currentTimeMillis();
        try {
            List<ToolDef> defs = toolDefs.definitions();
            String system = prompt.system();
            // Rounds 0..max run the tools the model asks for; one more round answers a model that still
            // wants tools past the limit by refusing them, so it writes its reply from what it has.
            for (int round = 0; round <= props.maxToolRounds() + 1; round++) {
                Turn turn = m.complete(system, defs, history, props.maxTokens(), delta -> {
                    replyText.append(delta);
                    events.text(delta);
                });
                usage = usage.plus(turn.usage());
                if (!turn.wantsTools() || round > props.maxToolRounds()) break;
                boolean overLimit = round == props.maxToolRounds();
                history.add(new Msg(ChatRole.ASSISTANT, turn.parts()));
                List<Part> results = new ArrayList<>();
                for (ToolUse use : turn.toolUses()) {
                    if (overLimit) {
                        results.add(new ToolResult(use.id(), "{\"error\":\"Kufiri i thirrjeve të mjeteve për këtë përgjigje u arrit. Përgjigju me të dhënat që ke.\"}", true));
                        continue;
                    }
                    String status = ChatToolDefs.statusOf(use.name());
                    if (status != null) events.status(status);
                    ChatToolDefs.Execution ex = toolDefs.execute(use.name(), use.input(), session.getId());
                    calls.add(new ToolCallRecord(use.name(), use.input(), ex.productSlugs(), ex.error()));
                    surfaced.addAll(ex.productSlugs());
                    results.add(new ToolResult(use.id(), ex.resultJson(), ex.error()));
                }
                history.add(new Msg(ChatRole.USER, results));
                // Text the model wrote before calling tools stays on screen; the next round continues after it.
                if (!replyText.isEmpty() && !replyText.toString().endsWith("\n")) {
                    replyText.append('\n');
                    events.text("\n");
                }
            }
        } catch (ChatUnavailableException e) {
            log.warn("Assistant could not answer session {}: {}", session.getId(), e.getMessage());
            if (usage.inputTokens() > 0) spend.record(usage);
            if (replyText.isEmpty()) return Reply.of(Outcome.UNAVAILABLE, "");
            // Part of a reply arrived before the failure: keep it, the customer has seen it.
        }
        String finalText = replyText.toString().strip();
        if (finalText.isEmpty()) {
            finalText = FALLBACK_REPLY;
            events.text(finalText);
        }
        List<Card> cards = mentioned(finalText, cards(new ArrayList<>(surfaced)));
        persistReply(session, finalText, calls, cards);
        spend.record(usage);
        log.info("Assistant reply: session {} {} tools {} ms, tokens in {} (cached {}) out {}", session.getId(), calls.size(),
                System.currentTimeMillis() - started, usage.inputTokens(), usage.cacheReadTokens(), usage.outputTokens());
        return new Reply(Outcome.OK, finalText, cards);
    }

    private void persistReply(ChatSession session, String text, List<ToolCallRecord> calls, List<Card> cards) {
        tx.executeWithoutResult(s -> {
            ChatSession fresh = sessions.findById(session.getId()).orElseThrow();
            fresh.setLastMessageAt(LocalDateTime.now());
            ChatMessage am = new ChatMessage();
            am.setSession(fresh);
            am.setRole(ChatRole.ASSISTANT);
            am.setContent(text);
            try {
                am.setToolCallsJson(calls.isEmpty() && cards.isEmpty() ? null
                        : json.writeValueAsString(new ToolCallsJson(calls, cards.stream().map(Card::slug).toList())));
            } catch (JsonProcessingException e) {
                am.setToolCallsJson(null);
            }
            messages.save(am);
        });
    }

    /** Earlier turns as plain text, newest last, alternating roles as the API requires. */
    private List<Msg> historyOf(ChatSession session) {
        List<ChatMessage> all = messages.findBySessionOrderByIdAsc(session);
        List<ChatMessage> recent = all.size() > HISTORY_TURNS ? all.subList(all.size() - HISTORY_TURNS, all.size()) : all;
        List<Msg> out = new ArrayList<>();
        for (ChatMessage cm : recent) {
            if (cm.getContent() == null || cm.getContent().isBlank()) continue;
            if (!out.isEmpty() && out.get(out.size() - 1).role() == cm.getRole()) {
                // Two in a row from the same side (a reply that failed, say): merge them into one turn.
                Msg last = out.remove(out.size() - 1);
                out.add(new Msg(cm.getRole(), List.of(new Text(((Text) last.parts().get(0)).text() + "\n" + cm.getContent()))));
            } else {
                out.add(new Msg(cm.getRole(), List.of(new Text(cm.getContent()))));
            }
        }
        while (!out.isEmpty() && out.get(0).role() != ChatRole.USER) out.remove(0);
        if (!out.isEmpty() && out.get(out.size() - 1).role() == ChatRole.USER) {
            // The customer's last message got no reply (a failure); fold it into the new one by dropping it,
            // the transcript keeps it and the model still sees the earlier context.
            out.remove(out.size() - 1);
        }
        return out;
    }

    /**
     * The surfaced products the reply actually talks about: a tool may list ten cards while the reply
     * names two. A product counts as mentioned when its slug or its whole title is in the text, or most
     * of the title's words are (the model tends to shorten "MSI RTX 3060 Ti Ventus 2X 8GB OC").
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

    private List<Card> cards(List<String> slugs) {
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
