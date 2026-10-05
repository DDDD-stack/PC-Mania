package al.pcmania.service.chat;

import al.pcmania.config.ChatProperties;
import al.pcmania.domain.ChatMessage;
import al.pcmania.domain.ChatSession;
import al.pcmania.domain.Enums.ChatRole;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The tool-call loop every language-model provider shares: ask the model, run the tools it asks for,
 * ask again, up to the configured number of rounds, streaming its text out as it arrives. A provider
 * only supplies the wire format: how a conversation is represented, how one request is made and how
 * tool results are appended.
 *
 * @param <C> the provider's own conversation representation (its request's message list)
 */
@Slf4j
public abstract class LlmChatProvider<C> implements ChatProvider {

    /** A tool the model asked for. {@code id} is null on providers that match results by name. */
    public record Call(String id, String name, JsonNode args) {}

    /** One answer from the model: its text (already streamed), the tools it wants, and the token counts. */
    public record ModelTurn(String text, List<Call> calls, boolean truncated, long inputTokens, long outputTokens,
                            long cacheReadTokens, long cacheWriteTokens) {}

    static final String LIMIT_RESULT = "{\"error\":\"Kufiri i thirrjeve të mjeteve për këtë përgjigje u arrit. Përgjigju me të dhënat që ke.\"}";
    static final String FALLBACK_REPLY = "Më vjen keq, nuk arrita ta formuloj përgjigjen. Provo përsëri ose na shkruaj në WhatsApp.";
    /** Earlier turns replayed to the model; older ones are dropped, the transcript stays in the database. */
    static final int HISTORY_TURNS = 20;

    protected final ChatProperties props;
    protected final ChatPrompt prompt;
    protected final ProviderUsageService usage;

    protected LlmChatProvider(ChatProperties props, ChatPrompt prompt, ProviderUsageService usage) {
        this.props = props;
        this.prompt = prompt;
        this.usage = usage;
    }

    /** The conversation to send: the system prompt, the earlier turns and the new message. */
    protected abstract C newConversation(String system, List<HistoryTurn> history, String userMessage);

    /**
     * One request. Streams the text through {@code onText} and appends the model's turn to the
     * conversation so the loop can continue after the tools run.
     *
     * @throws ProviderUnavailableException on a rate limit, an outage, or a rejected request
     */
    protected abstract ModelTurn call(C conversation, ToolRegistry tools, Consumer<String> onText) throws ProviderUnavailableException;

    /** Appends the tools' results, in the order the calls were made, as the provider's format wants them. */
    protected abstract void addToolResults(C conversation, List<Call> calls, List<String> resultsJson, List<Boolean> errors);

    /** Called after each request with the token counts, for providers that track spend. */
    protected void onUsage(ModelTurn turn) {
    }

    @Override
    public void stream(ChatSession s, String userMessage, List<ChatMessage> history, ToolRegistry tools, ChatStream out)
            throws ProviderUnavailableException {
        C conv = newConversation(prompt.system(), historyTurns(history), userMessage);
        try {
            // Rounds 0..max run the tools the model asks for; one more round answers a model that still
            // wants tools past the limit by refusing them, so it writes its reply from what it has.
            for (int round = 0; round <= props.maxToolRounds() + 1; round++) {
                usage.request(name());
                ModelTurn turn;
                try {
                    turn = call(conv, tools, out::delta);
                } catch (ProviderUnavailableException e) {
                    if (e.isRateLimited()) usage.rateLimitHit(name());
                    else usage.error(name());
                    throw e;
                }
                onUsage(turn);
                if (turn.calls().isEmpty()) break;
                boolean overLimit = round == props.maxToolRounds();
                if (round > props.maxToolRounds()) break;
                List<String> results = new ArrayList<>();
                List<Boolean> errors = new ArrayList<>();
                for (Call c : turn.calls()) {
                    if (overLimit) {
                        results.add(LIMIT_RESULT);
                        errors.add(true);
                        continue;
                    }
                    String status = ToolRegistry.statusOf(c.name());
                    if (status != null) out.status(status);
                    ToolRegistry.Execution ex = tools.execute(c.name(), c.args());
                    out.toolCall(c.name(), c.args(), ex.productSlugs(), ex.error());
                    out.surfaced(ex.productSlugs());
                    if (ex.action() != null) out.action(ex.action());
                    results.add(ex.resultJson());
                    errors.add(ex.error());
                }
                addToolResults(conv, turn.calls(), results, errors);
                // Text the model wrote before calling tools stays on screen; the next round continues after it.
                if (!out.text().isEmpty() && !out.text().endsWith("\n")) out.delta("\n");
            }
        } catch (ProviderUnavailableException e) {
            // Nothing shown yet: let the chain fall through. Something shown: keep it and say so.
            if (out.text().isBlank()) throw e;
            log.warn("{} failed mid-reply for session {}: {}", name(), s.getId(), e.getMessage());
            out.notice("Lidhja me asistentin u ndërpre. Pjesën tjetër mund ta pyesësh përsëri.");
            return;
        }
        if (out.text().isBlank()) out.delta(FALLBACK_REPLY);
    }

    /** An earlier turn as plain text; tool calls are not replayed, only what the customer saw. */
    public record HistoryTurn(ChatRole role, String text) {}

    /** Earlier turns, newest last, alternating roles as every API requires, at most {@link #HISTORY_TURNS}. */
    static List<HistoryTurn> historyTurns(List<ChatMessage> all) {
        List<ChatMessage> recent = all.size() > HISTORY_TURNS ? all.subList(all.size() - HISTORY_TURNS, all.size()) : all;
        List<HistoryTurn> out = new ArrayList<>();
        for (ChatMessage cm : recent) {
            if (cm.getContent() == null || cm.getContent().isBlank()) continue;
            if (!out.isEmpty() && out.get(out.size() - 1).role() == cm.getRole()) {
                HistoryTurn last = out.remove(out.size() - 1);
                out.add(new HistoryTurn(cm.getRole(), last.text() + "\n" + cm.getContent()));
            } else {
                out.add(new HistoryTurn(cm.getRole(), cm.getContent()));
            }
        }
        while (!out.isEmpty() && out.get(0).role() != ChatRole.USER) out.remove(0);
        // A customer message that got no reply (a failure) is dropped from the replay; the new one follows it.
        if (!out.isEmpty() && out.get(out.size() - 1).role() == ChatRole.USER) out.remove(out.size() - 1);
        return out;
    }
}
