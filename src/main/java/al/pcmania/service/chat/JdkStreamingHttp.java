package al.pcmania.service.chat;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** {@link StreamingHttp} on the JDK's HttpClient: no extra dependency, honours the JVM's proxy settings. */
@Component
public class JdkStreamingHttp implements StreamingHttp {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Override
    public Response postStream(String url, Map<String, String> headers, String body, Consumer<String> onLine) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(b::header);
        try {
            HttpResponse<Stream<String>> res = client.send(b.build(), HttpResponse.BodyHandlers.ofLines());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                try (Stream<String> lines = res.body()) {
                    lines.forEach(onLine);
                }
                return new Response(res.statusCode(), null);
            }
            StringBuilder err = new StringBuilder();
            try (Stream<String> lines = res.body()) {
                lines.forEach(l -> err.append(l).append('\n'));
            }
            return new Response(res.statusCode(), err.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + url, e);
        }
    }
}
