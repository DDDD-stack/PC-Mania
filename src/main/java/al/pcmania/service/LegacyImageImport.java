package al.pcmania.service;

import al.pcmania.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Carries photos written to disk by earlier versions into the database, once.
 *
 * It runs on every start but only copies files whose key is not there yet, so after the first time
 * it costs one directory listing. Nothing is deleted from disk: the folder stays as a backup until
 * the operator is satisfied the move worked.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyImageImport implements ApplicationRunner {

    private final AppProperties props;
    private final FileStorage storage;

    @Override
    public void run(ApplicationArguments args) {
        Path root = Path.of(props.uploadDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) return;

        int imported = 0;
        for (ImageStorage.Size size : ImageStorage.Size.values()) {
            Path dir = root.resolve(size.name());
            if (!Files.isDirectory(dir)) continue;
            List<Path> onDisk;
            try (Stream<Path> list = Files.list(dir)) {
                onDisk = list.filter(Files::isRegularFile).toList();
            } catch (IOException e) {
                log.warn("Could not list {}: {}", dir, e.getMessage());
                continue;
            }
            for (Path file : onDisk) {
                String key = ImageStorage.key(size, file.getFileName().toString());
                if (storage.exists(key)) continue;
                try {
                    storage.put(key, "image/jpeg", Files.readAllBytes(file), null);
                    imported++;
                } catch (IOException e) {
                    log.warn("Could not import {}: {}", file, e.getMessage());
                }
            }
        }
        if (imported > 0) log.info("Imported {} photo file(s) from {} into the database", imported, root);
    }
}
