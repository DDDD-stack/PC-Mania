package al.pcmania;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

final class LocalPostgres {

    private LocalPostgres() {}

    static EmbeddedPostgres startTemporary() {
        try {
            Path target = Files.createDirectories(Path.of("target"));
            return start(Files.createTempDirectory(target, "test-pg-"), 0, true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static EmbeddedPostgres start(Path dataDirectory, int port) {
        return start(dataDirectory, port, false);
    }

    private static EmbeddedPostgres start(Path dataDirectory, int port, boolean deleteOnClose) {
        try {
            EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder()
                    .setDataDirectory(dataDirectory)
                    .setCleanDataDirectory(deleteOnClose)

                    .setLocaleConfig("locale", "C")
                    .setPGStartupWait(Duration.ofSeconds(30));
            if (port > 0) builder.setPort(port);
            return builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the local Postgres", e);
        }
    }

    static Map<String, String> properties(EmbeddedPostgres pg) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres") + "&currentSchema=pcmania");
        p.put("spring.datasource.username", "postgres");
        p.put("spring.datasource.password", "postgres");
        p.put("spring.flyway.schemas", "pcmania");
        return p;
    }
}
