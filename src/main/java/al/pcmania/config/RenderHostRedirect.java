package al.pcmania.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RenderHostRedirect extends OncePerRequestFilter {

    private static final List<String> KEPT = List.of("/api/", "/admin", "/login", "/logout", "/healthz", "/img/",
            "/css/", "/js/", "/images/", "/vendor/", "/favicon");

    private final String base;
    private final String baseHost;

    public RenderHostRedirect(AppProperties app) {
        this.base = app.base();
        String host;
        try {
            host = URI.create(base).getHost();
        } catch (IllegalArgumentException e) {
            host = null;
        }
        this.baseHost = host == null ? null : host.toLowerCase(Locale.ROOT);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String query = request.getQueryString();
        response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        response.setHeader(HttpHeaders.LOCATION, base + request.getRequestURI() + (query == null ? "" : "?" + query));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String host = request.getServerName() == null ? "" : request.getServerName().toLowerCase(Locale.ROOT);
        if (baseHost == null || !host.endsWith(".onrender.com") || host.equals(baseHost)) return true;
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) return true;
        String path = request.getRequestURI();
        return KEPT.stream().anyMatch(path::startsWith);
    }
}
