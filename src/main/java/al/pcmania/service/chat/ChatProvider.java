package al.pcmania.service.chat;

import al.pcmania.domain.ChatMessage;
import al.pcmania.domain.ChatSession;

import java.util.List;

/**
 * Something that answers a customer's message: a language model behind the shared tools and system
 * prompt, or the deterministic guided finder. Providers differ only in how they talk to their model;
 * the tools, the prompt, the guardrails and the storage are shared and live outside them.
 */
public interface ChatProvider {

    /** gemini, anthropic or guided: recorded on every turn and counted in provider_usage. */
    String name();

    /** Whether it can answer right now: key configured, under its caps. The chain falls through when not. */
    boolean isAvailable();

    /**
     * Answers {@code userMessage} into {@code out}: text as it arrives, a status line while a tool runs,
     * product cards, a UI action such as the contact form. The customer's message is already stripped
     * of anything that looks like a phone number or an email address.
     *
     * @param history the conversation so far, oldest first, without {@code userMessage}
     * @throws ProviderUnavailableException when the provider could not answer at all (rate limit, outage,
     *                                      bad key): nothing has been written to {@code out} yet, and the
     *                                      next provider in the chain takes over
     */
    void stream(ChatSession s, String userMessage, List<ChatMessage> history, ToolRegistry tools, ChatStream out)
            throws ProviderUnavailableException;
}
