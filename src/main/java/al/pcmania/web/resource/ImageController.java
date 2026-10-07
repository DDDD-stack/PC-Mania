package al.pcmania.web.resource;

import al.pcmania.repo.StoredFileRepository;
import al.pcmania.service.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class ImageController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final ImageStorage images;

    @GetMapping("/img/p/{size}/{filename}")
    public ResponseEntity<byte[]> image(@PathVariable String size, @PathVariable String filename,
                                        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        ImageStorage.Size variant;
        try {
            variant = ImageStorage.Size.valueOf(size);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }

        String etag = "\"" + filename + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(CACHE).build();
        }

        Optional<StoredFileRepository.Content> found = images.content(variant, filename);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        StoredFileRepository.Content c = found.get();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.getContentType()))
                .cacheControl(CACHE)
                .eTag(etag)
                .body(c.getData());
    }
}
