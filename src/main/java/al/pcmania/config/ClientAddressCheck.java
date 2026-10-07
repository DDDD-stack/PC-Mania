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

@Component
@Slf4j
public class ClientAddressCheck extends OncePerRequestFilter {

    private final AtomicBoolean logged = new AtomicBoolean();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

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
