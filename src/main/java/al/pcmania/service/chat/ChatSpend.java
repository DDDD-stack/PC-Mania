package al.pcmania.service.chat;

import al.pcmania.config.ChatProperties;
import al.pcmania.domain.ChatUsage;
import al.pcmania.repo.ChatUsageRepository;
import al.pcmania.service.chat.ChatModel.Usage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * What the assistant has cost this month, priced from the token counts the API reports and kept in
 * {@code chat_usage}. The widget is hidden once the month's total passes the configured cap.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSpend {

    private final ChatUsageRepository repo;
    private final ChatProperties props;

    public static String currentMonth() {
        return YearMonth.now().toString();
    }

    @Transactional(readOnly = true)
    public ChatUsage thisMonth() {
        return repo.findById(currentMonth()).orElseGet(() -> {
            ChatUsage u = new ChatUsage();
            u.setMonth(currentMonth());
            return u;
        });
    }

    /** Every public page asks this, so the answer is kept for a minute between reads of the table. */
    private volatile Boolean overCapCached;
    private volatile long overCapCheckedAt;

    public boolean overCap() {
        if (props.monthlyCapUsd() <= 0) return false;
        long now = System.currentTimeMillis();
        Boolean cached = overCapCached;
        if (cached == null || now - overCapCheckedAt > 60_000) {
            cached = thisMonth().costUsd() >= props.monthlyCapUsd();
            overCapCached = cached;
            overCapCheckedAt = now;
        }
        return cached;
    }

    /** Adds one reply's tokens. Its own transaction: a failure here must not lose the reply itself. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Usage usage) {
        String month = currentMonth();
        ChatUsage u = repo.findForUpdateByMonth(month).orElseGet(() -> {
            ChatUsage n = new ChatUsage();
            n.setMonth(month);
            return n;
        });
        u.setInputTokens(u.getInputTokens() + usage.inputTokens());
        u.setOutputTokens(u.getOutputTokens() + usage.outputTokens());
        u.setCacheReadTokens(u.getCacheReadTokens() + usage.cacheReadTokens());
        u.setCacheWriteTokens(u.getCacheWriteTokens() + usage.cacheWriteTokens());
        u.setCostMicroUsd(u.getCostMicroUsd() + cost(usage));
        u.setReplies(u.getReplies() + 1);
        u.setUpdatedAt(LocalDateTime.now());
        repo.save(u);
        overCapCached = props.monthlyCapUsd() > 0 && u.costUsd() >= props.monthlyCapUsd();
        overCapCheckedAt = System.currentTimeMillis();
        if (overCapCached) {
            log.warn("Customer assistant: monthly cap of {} USD reached ({} USD); the widget is hidden until next month.",
                    props.monthlyCapUsd(), String.format("%.2f", u.costUsd()));
        }
    }

    /** Dollars per million tokens is the same number as micro-dollars per token. */
    long cost(Usage u) {
        double micro = u.inputTokens() * props.inputUsdPerMtok() + u.outputTokens() * props.outputUsdPerMtok()
                + u.cacheReadTokens() * props.cacheReadUsdPerMtok() + u.cacheWriteTokens() * props.cacheWriteUsdPerMtok();
        return Math.round(micro);
    }
}
