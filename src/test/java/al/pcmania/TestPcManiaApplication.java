package al.pcmania;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the whole site against a private local Postgres instead of Supabase:
 *
 * <pre>./mvnw spring-boot:test-run</pre>
 *
 * Use this for trying things out. Orders placed, photos uploaded and products edited here stay on this
 * PC and never reach the live shop. The data lives in {@code .local-db/} (not in git, and not wiped by
 * {@code mvnw clean}), so it survives restarts; delete that folder to start again from the seed data.
 * The dev profile is on, so the first start creates the admin {@code admin} / {@code admin123}.
 */
public class TestPcManiaApplication {

    private static final int PORT = 54329;

    public static void main(String[] args) throws IOException {
        Path data = Path.of(".local-db").toAbsolutePath();
        Files.createDirectories(data);
        EmbeddedPostgres pg = LocalPostgres.start(data, PORT);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (IOException ignored) {
            }
        }));

        LocalPostgres.properties(pg).forEach(System::setProperty);
        System.setProperty("spring.profiles.active", "dev");
        System.out.println("""

                ==============================================================
                  Local database (not Supabase): %s
                  Nothing done here reaches the live shop.
                ==============================================================
                """.formatted(data));
        SpringApplication.from(PcManiaApplication::main).run(args);
    }
}
