package al.pcmania.service.chat;

import al.pcmania.domain.ChatMessage;
import al.pcmania.domain.ChatSession;

import java.util.List;

public interface ChatProvider {

    String name();

    boolean isAvailable();

    void stream(ChatSession s, String userMessage, List<ChatMessage> history, ToolRegistry tools, ChatStream out)
            throws ProviderUnavailableException;
}
