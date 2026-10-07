package al.pcmania.web.chat;

import al.pcmania.domain.ChatLead;
import al.pcmania.domain.Enums.LeadSource;
import al.pcmania.service.RateLimiter;
import al.pcmania.service.chat.ChatService;
import al.pcmania.service.chat.LeadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LeadApiController {

    private final LeadService leads;
    private final ChatService chat;
    private final ChatCookie cookie;
    private final RateLimiter rateLimiter;

    @PostMapping(value = "/api/lead", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> create(@RequestParam String name, @RequestParam String phone,
                                                      @RequestParam String wantedItem,
                                                      @RequestParam(required = false) Integer budgetLek,
                                                      @RequestParam(required = false) Integer psuWatts,
                                                      @RequestParam(required = false) String notes,
                                                      @RequestParam(required = false, defaultValue = "CHAT") String source,
                                                      @RequestParam(required = false) String website,
                                                      HttpServletRequest request) {

        if (StringUtils.hasText(website)) return ResponseEntity.ok(Map.of("ok", true));
        if (!rateLimiter.tryAcquire("lead:" + request.getRemoteAddr(), 5, Duration.ofHours(1))) {
            return ResponseEntity.status(429).body(Map.of("ok", false, "error", "Keni dërguar shumë kërkesa në pak kohë. Na shkruani në WhatsApp."));
        }
        LeadSource src;
        try {
            src = LeadSource.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            src = LeadSource.FORM;
        }
        try {
            ChatLead lead = leads.create(chat.existingSession(cookie.read(request)).orElse(null), src, name, phone, wantedItem,
                    budgetLek, psuWatts, notes);
            return ResponseEntity.ok(Map.of("ok", true, "id", lead.getId(),
                    "message", "Faleminderit, " + lead.getName() + "! Do t'ju telefonojmë sapo të kemi diçka."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        }
    }
}
