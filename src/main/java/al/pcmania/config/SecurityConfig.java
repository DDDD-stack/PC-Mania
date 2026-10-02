package al.pcmania.config;

import al.pcmania.repo.AdminUserRepository;
import al.pcmania.service.LoginAttemptService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, LoginAttemptService attempts) throws Exception {
        return http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/admin/login").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .formLogin(f -> f
                        .loginPage("/admin/login")
                        .failureHandler((req, res, ex) -> {
                            attempts.failed(req.getRemoteAddr());
                            res.sendRedirect("/admin/login?error");
                        })
                        .successHandler((req, res, auth) -> {
                            attempts.succeeded(req.getRemoteAddr());
                            res.sendRedirect("/admin");
                        }))
                .addFilterBefore(attempts.filter(), UsernamePasswordAuthenticationFilter.class)
                .logout(l -> l.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login?logout"))
                // Public forms are anonymous and often left open for hours in the Facebook in-app browser;
                // CSRF there protects nothing and would only produce expired-token errors.
                .csrf(c -> c.ignoringRequestMatchers("/porosit/**", "/pc-me-porosi", "/kerko-produkt", "/nderro/**"))
                .headers(h -> h
                        .frameOptions(f -> f.sameOrigin())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(AdminUserRepository repo) {
        return username -> repo.findByUsername(username)
                .map(u -> User.withUsername(u.getUsername()).password(u.getPasswordHash())
                        .disabled(!u.isEnabled()).roles("ADMIN").build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
