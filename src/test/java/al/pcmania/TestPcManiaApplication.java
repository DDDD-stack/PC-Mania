package al.pcmania;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
