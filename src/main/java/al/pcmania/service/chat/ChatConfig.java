package al.pcmania.service.chat;

import al.pcmania.config.ChatProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Anthropic client exists only when {@code ANTHROPIC_API_KEY} is set, and {@link AnthropicChatModel}
 * with it. Without the key there is no {@link ChatModel} bean at all, the widget is not rendered and
 * {@code /api/chat} answers 503: nothing about the assistant is reachable.
 */
@Configuration
@Slf4j
public class ChatConfig {

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${app.chat.api-key:}')")
    AnthropicClient anthropicClient(ChatProperties props) {
        log.info("Customer assistant on: model {}, monthly cap {} USD", props.model(), props.monthlyCapUsd());
        return AnthropicOkHttpClient.builder()
                .apiKey(props.apiKey().trim())
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                .build();
    }

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${app.chat.api-key:}')")
    ChatModel chatModel(AnthropicClient client, ChatProperties props, ObjectMapper json) {
        return new AnthropicChatModel(client, props, json);
    }

    /** Replies stream for seconds while tools run; each one gets a virtual thread so none is kept waiting. */
    @Bean(name = "chatExecutor", destroyMethod = "shutdown")
    ExecutorService chatExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
