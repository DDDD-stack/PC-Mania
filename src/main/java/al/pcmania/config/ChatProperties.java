package al.pcmania.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * The customer assistant. Off unless {@code ANTHROPIC_API_KEY} is set; the key never leaves the server.
 *
 * @param apiKey                Anthropic API key, from the environment only
 * @param model                 the Claude model the assistant answers with
 * @param maxMessagesPerSession customer messages one conversation may hold before it is sent to WhatsApp
 * @param maxToolRounds         tool-call rounds allowed for one reply
 * @param maxTokens             the longest reply, in tokens
 * @param maxPerHourPerIp       messages one client address may send in an hour
 * @param monthlyCapUsd         spend in a calendar month past which the widget is hidden
 * @param inputUsdPerMtok       price list for the running cost, dollars per million tokens
 */
@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(
        String apiKey,
        @DefaultValue("claude-haiku-4-5-20251001") String model,
        @DefaultValue("25") int maxMessagesPerSession,
        @DefaultValue("4") int maxToolRounds,
        @DefaultValue("1024") int maxTokens,
        @DefaultValue("30") int maxPerHourPerIp,
        @DefaultValue("25") double monthlyCapUsd,
        @DefaultValue("1.0") double inputUsdPerMtok,
        @DefaultValue("5.0") double outputUsdPerMtok,
        @DefaultValue("0.10") double cacheReadUsdPerMtok,
        @DefaultValue("1.25") double cacheWriteUsdPerMtok) {

    public boolean keyConfigured() {
        return StringUtils.hasText(apiKey);
    }
}
