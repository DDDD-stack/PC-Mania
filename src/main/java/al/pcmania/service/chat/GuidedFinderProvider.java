package al.pcmania.service.chat;

import al.pcmania.domain.ChatMessage;
import al.pcmania.domain.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GuidedFinderProvider implements ChatProvider {

    public static final String NAME = "guided";
    static final String INTRO = "Të ndihmoj me kërkimin e shpejtë: katër pyetje dhe të tregoj çfarë kemi në stok.";
    static final String BUSY = "Asistenti është i zënë për momentin. Në vend të tij, ja kërkimi i shpejtë: katër pyetje dhe të tregoj çfarë kemi në stok.";

    private final GuidedFinder finder;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void stream(ChatSession s, String userMessage, List<ChatMessage> history, ToolRegistry tools, ChatStream out) {
        stream(out, false);
    }

    public void stream(ChatStream out, boolean fallback) {
        out.notice(fallback ? BUSY : INTRO);
        out.finder(finder.step(Map.of()));
    }
}
