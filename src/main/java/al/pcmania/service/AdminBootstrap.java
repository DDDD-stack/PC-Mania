package al.pcmania.service;

import al.pcmania.config.AppProperties;
import al.pcmania.domain.AdminUser;
import al.pcmania.repo.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

/** Creates the single admin account on first start. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final AdminUserRepository repo;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    @Override
    public void run(ApplicationArguments args) {
        if (repo.count() > 0) return;
        String password = props.admin().password();
        boolean generated = password == null || password.isBlank();
        if (generated) {
            byte[] bytes = new byte[9];
            new SecureRandom().nextBytes(bytes);
            password = HexFormat.of().formatHex(bytes);
        }
        AdminUser user = new AdminUser();
        user.setUsername(props.admin().username());
        user.setPasswordHash(encoder.encode(password));
        repo.save(user);
        if (generated) {
            log.warn("Created admin user '{}' with generated password: {}", user.getUsername(), password);
        } else {
            log.info("Created admin user '{}'", user.getUsername());
        }
    }
}
