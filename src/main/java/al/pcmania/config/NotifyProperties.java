package al.pcmania.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notify")
public record NotifyProperties(String telegramBotToken, String telegramChatId) {}
