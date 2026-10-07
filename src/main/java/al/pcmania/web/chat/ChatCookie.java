package al.pcmania.web.chat;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ChatCookie {

    public static final String NAME = "pm_chat";
    static final Duration MAX_AGE = Duration.ofDays(30);

    private final boolean secure;

    public ChatCookie(@Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        this.secure = secure;
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    public ResponseCookie build(String token) {
        return ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api")
                .maxAge(MAX_AGE)
                .build();
    }
}
