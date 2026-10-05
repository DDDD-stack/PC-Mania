package al.pcmania.service.chat;

import al.pcmania.config.ChatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Plumbing for the assistant: the thread pool replies stream on, and a start-up line saying what is on. */
@Configuration
@Slf4j
public class ChatConfig {

    /** Replies stream for seconds while tools run; each one gets a virtual thread so none is kept waiting. */
    @Bean(name = "chatExecutor", destroyMethod = "shutdown")
    ExecutorService chatExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ChatStartupLog chatStartupLog(ChatProperties props) {
        return new ChatStartupLog(props);
    }

    public static class ChatStartupLog {
        private final ChatProperties props;

        ChatStartupLog(ChatProperties props) {
            this.props = props;
        }

        @EventListener(ApplicationReadyEvent.class)
        void announce() {
            log.info("Customer assistant: provider {} (gemini key {}, model {}; anthropic key {}, model {}, cap {} USD); guided finder always on.",
                    props.provider(), props.gemini().keyConfigured() ? "set" : "missing", props.gemini().model(),
                    props.anthropic().keyConfigured() ? "set" : "missing", props.anthropic().model(), props.anthropic().monthlyCapUsd());
        }
    }
}
