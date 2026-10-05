package al.pcmania.config;

import al.pcmania.service.ApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Stateless bearer-token security for the mobile admin API under /api. Evaluated before the
 * session/form-login chain in {@link SecurityConfig}, which handles everything else.
 */
@Configuration
public class ApiSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http, ApiTokenService tokens, MobileAppKey appKey) throws Exception {
        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        // The customer assistant: anonymous, same-origin only (see corsSource), its own rate limits.
                        .requestMatchers("/api/chat", "/api/chat/**", "/api/lead", "/api/finder/**").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Tokens travel in the Authorization header, never in cookies, so CSRF does not apply.
                .csrf(c -> c.disable())
                .cors(c -> c.configurationSource(corsSource()))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .addFilterBefore(new BearerTokenFilter(tokens, appKey), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setCharacterEncoding("UTF-8");
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Sesioni ka skaduar. Hyni përsëri.\"}");
                }))
                .build();
    }

    /** Lets the app's web preview (a different origin) call the API. Safe: no cookies are involved. */
    private static UrlBasedCorsConfigurationSource corsSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("*"));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // The assistant is for this site's own pages: no origin is allowed, so another site cannot embed
        // it and spend the shop's API budget. Registered first, as the first matching pattern wins.
        source.registerCorsConfiguration("/api/chat/**", new CorsConfiguration());
        source.registerCorsConfiguration("/api/chat", new CorsConfiguration());
        source.registerCorsConfiguration("/api/lead", new CorsConfiguration());
        source.registerCorsConfiguration("/api/finder/**", new CorsConfiguration());
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    static class BearerTokenFilter extends OncePerRequestFilter {
        private final ApiTokenService tokens;
        private final MobileAppKey appKey;

        BearerTokenFilter(ApiTokenService tokens, MobileAppKey appKey) {
            this.tokens = tokens;
            this.appKey = appKey;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String header = req.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String raw = header.substring(7).trim();
                if (appKey.matches(raw)) {
                    authenticate("phone-app");
                } else {
                    tokens.authenticate(raw).ifPresent(user -> authenticate(user.getUsername()));
                }
            }
            chain.doFilter(req, res);
        }

        private static void authenticate(String name) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    name, null, AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        }
    }
}
