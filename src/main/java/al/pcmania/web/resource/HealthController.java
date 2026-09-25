package al.pcmania.web.resource;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Render's health check (render.yaml: healthCheckPath). It is polled every few seconds while the
 * service is up, so it answers without rendering a page or querying the database: pointing it at
 * "/" cost the home page's queries on every poll.
 *
 * It deliberately does not test the database. The application does not start at all unless Flyway
 * reached the database, which is what a deploy needs to know, and restarting the service would not
 * fix a database that is briefly unreachable afterwards.
 */
@RestController
public class HealthController {

    @GetMapping(value = "/healthz", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> health() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body("ok");
    }
}
