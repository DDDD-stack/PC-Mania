package al.pcmania.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

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
            @DefaultValue("gemini-flash-latest") String model,

            @DefaultValue("12") int requestsPerMinute,

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
