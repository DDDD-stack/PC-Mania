package al.pcmania.web.site;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * An upload over the server's limit fails before any controller runs. The trade-in form checks sizes in
 * the browser first, so this only answers someone who got past that: with the form's own JSON shape for
 * the scripted upload, or a plain message otherwise.
 */
@ControllerAdvice
public class UploadLimitAdvice {

    private static final String MESSAGE = "Skedari është shumë i madh. Videoja duhet të jetë deri në 40 MB – "
            + "ose zgjidhni ta dërgoni në WhatsApp.";

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> tooLarge(HttpServletRequest request) {
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("ok", false, "errors", Map.of("form", MESSAGE)));
        }
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .header("Content-Type", "text/plain; charset=UTF-8")
                .body(MESSAGE);
    }
}
