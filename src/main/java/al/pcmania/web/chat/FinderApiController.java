package al.pcmania.web.chat;

import al.pcmania.service.chat.ChatService;
import al.pcmania.service.chat.ChatService.Card;
import al.pcmania.service.chat.ChatTools.StockItem;
import al.pcmania.service.chat.GuidedFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/finder/step}: the guided finder's state machine. The widget sends the answers it has
 * ({@code use}, {@code res}, {@code budget}, {@code psu}) and gets the next question, or the results as
 * product cards once all four are answered.
 */
@RestController
@RequiredArgsConstructor
public class FinderApiController {

    public record Response(GuidedFinder.Step step, List<Card> cards) {}

    private final GuidedFinder finder;
    private final ChatService chat;

    @GetMapping(value = "/api/finder/step", produces = MediaType.APPLICATION_JSON_VALUE)
    public Response step(@RequestParam Map<String, String> answers) {
        GuidedFinder.Step step = finder.step(answers);
        List<Card> cards = step.done() ? chat.cards(step.results().stream().map(StockItem::slug).toList()) : List.of();
        return new Response(step, cards);
    }
}
