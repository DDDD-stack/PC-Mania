package al.pcmania.web.resource;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping(value = "/healthz", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> health() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body("ok");
    }
}
