package al.pcmania;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A real Postgres (the same major version as Supabase) running from binaries Maven downloads, so the
 * application can be exercised end to end without touching the live database. Nothing has to be
 * installed: the first start unpacks the binaries into the temp directory and later ones reuse them.
 */
final class LocalPostgres {

    private LocalPostgres() {}

    /** A throwaway server on a free port, its data under target/ and deleted again when it is closed. */
    static EmbeddedPostgres startTemporary() {
        try {
            Path target = Files.createDirectories(Path.of("target"));
            return start(Files.createTempDirectory(target, "test-pg-"), 0, true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Starts a server that keeps its data between runs; the caller holds the reference while it should run. */
    static EmbeddedPostgres start(Path dataDirectory, int port) {
        return start(dataDirectory, port, false);
    }

    /*
     * The data directory is always given explicitly: the library's default is under the system temp
     * folder, where initdb can be refused permission to create its subdirectories on Windows.
     */
    private static EmbeddedPostgres start(Path dataDirectory, int port, boolean deleteOnClose) {
        try {
            EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder()
                    .setDataDirectory(dataDirectory)
                    .setCleanDataDirectory(deleteOnClose)
                    // Matches Supabase, where text sorts and compares the same way.
                    .setLocaleConfig("locale", "C")
                    .setPGStartupWait(Duration.ofSeconds(30));
            if (port > 0) builder.setPort(port);
            return builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the local Postgres", e);
        }
    }

    /**
     * Spring properties pointing the application at the server. They outrank DB_URL and friends,
     * so a shell that is set up for Supabase still ends up on the local database.
     */
    static Map<String, String> properties(EmbeddedPostgres pg) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres") + "&currentSchema=pcmania");
        p.put("spring.datasource.username", "postgres");
        p.put("spring.datasource.password", "postgres");
        p.put("spring.flyway.schemas", "pcmania");
        return p;
    }
}
