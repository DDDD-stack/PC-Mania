package al.pcmania.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logs, once per start, the client address the first proxied request resolved to.
 *
 * Order and build-request limits and the admin login lockout are keyed on {@code getRemoteAddr()}.
 * Behind Render that is only the visitor's address if Tomcat trusts the load balancer in front of it
 * ({@code server.forward-headers-strategy: native}; Tomcat trusts private addresses by default).
 * If it does not, every visitor appears to come from the same proxy address and shares one limit, so
 * a handful of failed logins would lock the admin out for everyone. Nothing in Render's documentation
 * pins down the load balancer's address, so this makes it visible: the line in the log should show a
 * public address that is not Render's.
 */
@Component
@Slf4j
public class ClientAddressCheck extends OncePerRequestFilter {

    private final AtomicBoolean logged = new AtomicBoolean();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Tomcat removes X-Forwarded-For once it has used it, so its absence alone says nothing: a request
        // counts as proxied when any of the headers the edge adds is there. The health check has none.
        boolean proxied = request.getHeader("X-Forwarded-For") != null || request.getHeader("X-Forwarded-Proto") != null
                || request.getHeader("CF-Ray") != null;
        if (proxied && !"/healthz".equals(request.getRequestURI()) && logged.compareAndSet(false, true)) {
            String left = request.getHeader("X-Forwarded-For");
            log.info("Client address check (first proxied request since start): resolved to {}; X-Forwarded-For "
                            + "left unprocessed: {}. The first should be the visitor's own address. If it belongs to "
                            + "the proxy instead, set server.tomcat.remoteip.internal-proxies to include it.",
                    request.getRemoteAddr(), left == null ? "(none - Tomcat used it)" : left);
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return logged.get();
    }
}
