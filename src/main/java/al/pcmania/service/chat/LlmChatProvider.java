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

@Slf4j
public abstract class LlmChatProvider<C> implements ChatProvider {

    public record Call(String id, String name, JsonNode args) {}

    public record ModelTurn(String text, List<Call> calls, boolean truncated, long inputTokens, long outputTokens,
                            long cacheReadTokens, long cacheWriteTokens) {}

    static final String LIMIT_RESULT = "{\"error\":\"Kufiri i thirrjeve të mjeteve për këtë përgjigje u arrit. Përgjigju me të dhënat që ke.\"}";
    static final String FALLBACK_REPLY = "Më vjen keq, nuk arrita ta formuloj përgjigjen. Provo përsëri ose na shkruaj në WhatsApp.";

    static final int HISTORY_TURNS = 20;

    protected final ChatProperties props;
    protected final ChatPrompt prompt;
    protected final ProviderUsageService usage;

    protected LlmChatProvider(ChatProperties props, ChatPrompt prompt, ProviderUsageService usage) {
        this.props = props;
        this.prompt = prompt;
        this.usage = usage;
    }

    protected abstract C newConversation(String system, List<HistoryTurn> history, String userMessage);

    protected abstract ModelTurn call(C conversation, ToolRegistry tools, Consumer<String> onText) throws ProviderUnavailableException;

    protected abstract void addToolResults(C conversation, List<Call> calls, List<String> resultsJson, List<Boolean> errors);

    protected void onUsage(ModelTurn turn) {
    }

    @Override
    public void stream(ChatSession s, String userMessage, List<ChatMessage> history, ToolRegistry tools, ChatStream out)
            throws ProviderUnavailableException {
        C conv = newConversation(prompt.system(), historyTurns(history), userMessage);
        try {

            for (int round = 0; round <= props.maxToolRounds() + 1; round++) {
                usage.request(name());
                ModelTurn turn;
                try {
                    turn = call(conv, tools, out::delta);
                } catch (ProviderUnavailableException e) {
                    if (e.isRateLimited()) usage.rateLimitHit(name());
                    else usage.error(name(), e.getMessage());
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

                if (!out.text().isEmpty() && !out.text().endsWith("\n")) out.delta("\n");
            }
        } catch (ProviderUnavailableException e) {

            if (out.text().isBlank()) throw e;
            log.warn("{} failed mid-reply for session {}: {}", name(), s.getId(), e.getMessage());
            out.notice("Lidhja me asistentin u ndërpre. Pjesën tjetër mund ta pyesësh përsëri.");
            return;
        }
        if (out.text().isBlank()) out.delta(FALLBACK_REPLY);
    }

    public record HistoryTurn(ChatRole role, String text) {}

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

        if (!out.isEmpty() && out.get(out.size() - 1).role() == ChatRole.USER) out.remove(out.size() - 1);
        return out;
    }
}
