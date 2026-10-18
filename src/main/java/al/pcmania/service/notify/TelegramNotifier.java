package al.pcmania.service.notify;

import al.pcmania.config.NotifyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class TelegramNotifier implements AdminNotifier {

    static final int MAX_TEXT = 4000;

    @FunctionalInterface
    public interface FormPoster {
        int post(URI uri, String form) throws IOException, InterruptedException;
    }

    private final String token;
    private final String chatId;
    private final FormPoster http;

    @Autowired
    public TelegramNotifier(NotifyProperties props) {
        this(props.telegramBotToken(), props.telegramChatId(), jdkPoster());
    }

    TelegramNotifier(String token, String chatId, FormPoster http) {
        this.token = token == null ? null : token.trim();
        this.chatId = chatId == null ? null : chatId.trim();
        this.http = http;
    }

    @Override
    public String name() {
        return "Telegram";
    }

    @Override
    public boolean isEnabled() {
        return StringUtils.hasText(token) && StringUtils.hasText(chatId);
    }

    @Override
    public void notifyAdmin(String subject, String body) {
        String text = subject + "\n\n" + body;
        if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT - 1) + "…";
        String form = "chat_id=" + enc(chatId) + "&text=" + enc(text) + "&disable_web_page_preview=true";
        int status;
        try {
            status = http.post(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"), form);
        } catch (IOException e) {
            throw new IllegalStateException("Telegram could not be reached: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending to Telegram");
        }
        if (status != 200) throw new IllegalStateException("Telegram answered HTTP " + status);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static FormPoster jdkPoster() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return (uri, form) -> client.send(HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(), HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }
}
