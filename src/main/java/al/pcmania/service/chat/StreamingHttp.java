package al.pcmania.service.chat;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The one thing a model provider needs from the network: POST a JSON body and read the response line
 * by line as it streams (server-sent events are line-based). An interface so tests can script the
 * lines a provider receives without a socket.
 */
public interface StreamingHttp {

    /** The HTTP status and, for a non-2xx status, the whole body (an error document). */
    record Response(int status, String errorBody) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /**
     * Sends the request; on a 2xx answer every body line goes to {@code onLine} as it arrives and the
     * returned error body is null. On any other status the body is collected and returned instead.
     *
     * @throws IOException when the server could not be reached or the connection dropped
     */
    Response postStream(String url, Map<String, String> headers, String body, Consumer<String> onLine) throws IOException;
}
