package al.pcmania.service.chat;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ScriptedHttp implements StreamingHttp {

    public record Sent(String url, Map<String, String> headers, String body) {}

    private record Scripted(int status, List<String> lines, String errorBody, IOException failure) {}

    public final List<Sent> sent = new ArrayList<>();
    private final Deque<Scripted> script = new ArrayDeque<>();

    public ScriptedHttp stream(String... lines) {
        script.add(new Scripted(200, List.of(lines), null, null));
        return this;
    }

    public ScriptedHttp status(int status, String errorBody) {
        script.add(new Scripted(status, List.of(), errorBody, null));
        return this;
    }

    public ScriptedHttp fail(String message) {
        script.add(new Scripted(0, List.of(), null, new IOException(message)));
        return this;
    }

    @Override
    public Response postStream(String url, Map<String, String> headers, String body, Consumer<String> onLine) throws IOException {
        sent.add(new Sent(url, headers, body));
        Scripted next = script.poll();
        if (next == null) throw new IOException("script exhausted");
        if (next.failure() != null) throw next.failure();
        if (next.status() >= 200 && next.status() < 300) {
            next.lines().forEach(onLine);
            return new Response(next.status(), null);
        }
        return new Response(next.status(), next.errorBody());
    }
}
