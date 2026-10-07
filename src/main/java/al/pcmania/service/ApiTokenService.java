package al.pcmania.service;

import al.pcmania.domain.AdminUser;
import al.pcmania.domain.ApiToken;
import al.pcmania.repo.AdminUserRepository;
import al.pcmania.repo.ApiTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiTokenService {

    private static final Duration IDLE_EXPIRY = Duration.ofDays(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiTokenRepository tokens;
    private final AdminUserRepository admins;
    private final PasswordEncoder encoder;

    @Transactional
    public Optional<String> login(String username, String password, String deviceName) {
        Optional<AdminUser> user = admins.findByUsername(username == null ? "" : username.trim())
                .filter(AdminUser::isEnabled)
                .filter(u -> password != null && encoder.matches(password, u.getPasswordHash()));
        if (user.isEmpty()) return Optional.empty();

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ApiToken t = new ApiToken();
        t.setAdminUser(user.get());
        t.setTokenHash(hash(raw));
        t.setDeviceName(deviceName == null ? null : deviceName.substring(0, Math.min(deviceName.length(), 100)));
        t.setCreatedAt(LocalDateTime.now());
        t.setLastUsedAt(LocalDateTime.now());
        tokens.save(t);
        return Optional.of(raw);
    }

    @Transactional
    public Optional<AdminUser> authenticate(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        return tokens.findByTokenHash(hash(raw)).flatMap(t -> {
            if (t.getLastUsedAt().plus(IDLE_EXPIRY).isBefore(LocalDateTime.now()) || !t.getAdminUser().isEnabled()) {
                tokens.delete(t);
                return Optional.empty();
            }

            if (t.getLastUsedAt().isBefore(LocalDateTime.now().minusMinutes(5))) t.setLastUsedAt(LocalDateTime.now());
            return Optional.of(t.getAdminUser());
        });
    }

    @Transactional
    public void revoke(String raw) {
        tokens.findByTokenHash(hash(raw)).ifPresent(tokens::delete);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
