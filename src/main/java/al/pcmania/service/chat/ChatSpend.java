package al.pcmania.service.chat;

import al.pcmania.config.ChatProperties;
import al.pcmania.domain.ChatUsage;
import al.pcmania.repo.ChatUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSpend {

    private final ChatUsageRepository repo;
    private final ChatProperties props;

    private volatile Boolean overCapCached;
    private volatile long overCapCheckedAt;

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

    public double capUsd() {
        return props.anthropic().monthlyCapUsd();
    }

    public boolean overCap() {
        if (capUsd() <= 0) return false;
        long now = System.currentTimeMillis();
        Boolean cached = overCapCached;
        if (cached == null || now - overCapCheckedAt > 60_000) {
            cached = thisMonth().costUsd() >= capUsd();
            overCapCached = cached;
            overCapCheckedAt = now;
        }
        return cached;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long input, long output, long cacheRead, long cacheWrite) {
        String month = currentMonth();
        ChatUsage u = repo.findForUpdateByMonth(month).orElseGet(() -> {
            ChatUsage n = new ChatUsage();
            n.setMonth(month);
            return n;
        });
        u.setInputTokens(u.getInputTokens() + input);
        u.setOutputTokens(u.getOutputTokens() + output);
        u.setCacheReadTokens(u.getCacheReadTokens() + cacheRead);
        u.setCacheWriteTokens(u.getCacheWriteTokens() + cacheWrite);
        u.setCostMicroUsd(u.getCostMicroUsd() + cost(input, output, cacheRead, cacheWrite));
        u.setReplies(u.getReplies() + 1);
        u.setUpdatedAt(LocalDateTime.now());
        repo.save(u);
        overCapCached = capUsd() > 0 && u.costUsd() >= capUsd();
        overCapCheckedAt = System.currentTimeMillis();
        if (overCapCached) {
            log.warn("Anthropic: monthly cap of {} USD reached ({} USD); the provider is off until next month.",
                    capUsd(), String.format("%.2f", u.costUsd()));
        }
    }

    long cost(long input, long output, long cacheRead, long cacheWrite) {
        ChatProperties.Anthropic a = props.anthropic();
        return Math.round(input * a.inputUsdPerMtok() + output * a.outputUsdPerMtok()
                + cacheRead * a.cacheReadUsdPerMtok() + cacheWrite * a.cacheWriteUsdPerMtok());
    }
}
