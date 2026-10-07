package al.pcmania.service;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);

    private record Attempts(int count, Instant last) {}

    private final Map<String, Attempts> byIp = new ConcurrentHashMap<>();

    public void failed(String ip) {
        byIp.merge(ip, new Attempts(1, Instant.now()),
                (a, b) -> new Attempts(isExpired(a) ? 1 : a.count + 1, Instant.now()));
    }

    public void succeeded(String ip) {
        byIp.remove(ip);
    }

    public boolean isBlocked(String ip) {
        Attempts a = byIp.get(ip);
        return a != null && a.count >= MAX_FAILURES && !isExpired(a);
    }

    private boolean isExpired(Attempts a) {
        return a.last.plus(LOCK).isBefore(Instant.now());
    }

    public Filter filter() {
        return (req, res, chain) -> {
            HttpServletRequest r = (HttpServletRequest) req;
            if ("POST".equals(r.getMethod()) && "/admin/login".equals(r.getRequestURI()) && isBlocked(r.getRemoteAddr())) {
                ((HttpServletResponse) res).sendRedirect("/admin/login?locked");
                return;
            }
            chain.doFilter(req, res);
        };
    }
}
