package al.pcmania.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * The customer assistant. The provider is chosen by {@code CHAT_PROVIDER}; whichever is chosen, the
 * guided finder stands in when it is unavailable or rate-limited. Keys never leave the server.
 *
 * @param provider              gemini, anthropic or guided
 * @param maxMessagesPerSession customer messages one conversation may hold before it is sent to WhatsApp
 * @param maxToolRounds         tool-call rounds allowed for one reply
 * @param maxTokens             the longest reply, in tokens
 * @param maxPerHourPerIp       messages one client address may send in an hour
 * @param gemini                Gemini: key, model, requests per minute the limiter allows
 * @param anthropic             Anthropic: key, model, monthly spend cap and price list
 */
@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(
        @DefaultValue("gemini") String provider,
        @DefaultValue("25") int maxMessagesPerSession,
        @DefaultValue("4") int maxToolRounds,
        @DefaultValue("4096") int maxTokens,
        @DefaultValue("30") int maxPerHourPerIp,
        @DefaultValue Gemini gemini,
        @DefaultValue Anthropic anthropic) {

    public record Gemini(
            String apiKey,
            @DefaultValue("gemini-3.8-flash") String model,
            /** Free tier allows 15 on Flash; 12 leaves headroom for retries and clock drift. */
            @DefaultValue("12") int requestsPerMinute,
            /** The free tier's daily cap, shown in the admin against today's count. */
            @DefaultValue("1500") int requestsPerDay) {
        public boolean keyConfigured() {
            return StringUtils.hasText(apiKey);
        }
    }

    public record Anthropic(
            String apiKey,
            @DefaultValue("claude-haiku-4-5-20251001") String model,
            @DefaultValue("25") double monthlyCapUsd,
            @DefaultValue("1.0") double inputUsdPerMtok,
            @DefaultValue("5.0") double outputUsdPerMtok,
            @DefaultValue("0.10") double cacheReadUsdPerMtok,
            @DefaultValue("1.25") double cacheWriteUsdPerMtok) {
        public boolean keyConfigured() {
            return StringUtils.hasText(apiKey);
        }
    }
}
