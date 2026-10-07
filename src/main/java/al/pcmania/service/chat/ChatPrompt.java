package al.pcmania.service.chat;

import al.pcmania.config.AppProperties;
import al.pcmania.web.Fmt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Component
public class ChatPrompt {

    static final String RESOURCE = "prompts/assistant-sq.txt";

    private final String system;

    public ChatPrompt(AppProperties props) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            String rules = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            system = rules + "\n\nSHOP FACTS\n- Shipping by courier costs " + Fmt.lek(props.courierShippingLek())
                    + " anywhere in Albania; pickup in Tiranë is free; products marked \"transport falas\" ship free.\n"
                    + "- The shop's phone and WhatsApp: " + props.phoneDisplay() + ".\n";
        } catch (IOException e) {
            throw new UncheckedIOException("Missing " + RESOURCE, e);
        }
    }

    public String system() {
        return system;
    }
}
