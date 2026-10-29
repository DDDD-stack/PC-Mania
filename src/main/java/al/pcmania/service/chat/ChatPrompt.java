package al.pcmania.service.chat;

import al.pcmania.config.AppProperties;
import al.pcmania.web.Fmt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

@Component
public class ChatPrompt {

    static final String RESOURCE = "prompts/assistant-sq.txt";

    private final String base;
    private final Supplier<String> policies;
    private volatile String system;

    @Autowired
    public ChatPrompt(AppProperties props, ShopPolicyText policies) {
        this(props, policies::text);
    }

    ChatPrompt(AppProperties props) {
        this(props, () -> "");
    }

    ChatPrompt(AppProperties props, Supplier<String> policies) {
        this.policies = policies;
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            String rules = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            base = rules + "\n\nSHOP FACTS\n- Shipping by courier costs " + Fmt.lek(props.courierShippingLek())
                    + " anywhere in Albania; pickup in Tiranë is free; products marked \"transport falas\" ship free.\n"
                    + "- The shop's phone and WhatsApp: " + props.phoneDisplay() + ".\n";
        } catch (IOException e) {
            throw new UncheckedIOException("Missing " + RESOURCE, e);
        }
    }

    public String system() {
        String s = system;
        if (s == null) {
            String text = policies.get();
            s = text == null || text.isBlank() ? base
                    : base + "\nSHOP POLICIES (the site's own pages, word for word; the only source for policy answers)\n" + text + "\n";
            system = s;
        }
        return s;
    }
}
