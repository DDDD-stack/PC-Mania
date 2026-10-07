package al.pcmania.service.chat;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

public interface StreamingHttp {

    record Response(int status, String errorBody) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    Response postStream(String url, Map<String, String> headers, String body, Consumer<String> onLine) throws IOException;
}
