package al.pcmania.service.notify;

import al.pcmania.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EmailNotifier implements AdminNotifier {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties props;
    private final String mailHost;

    public EmailNotifier(ObjectProvider<JavaMailSender> mailSender, AppProperties props, @Value("${spring.mail.host:}") String mailHost) {
        this.mailSender = mailSender;
        this.props = props;
        this.mailHost = mailHost;
    }

    @Override
    public String name() {
        return "Email";
    }

    @Override
    public boolean isEnabled() {
        return StringUtils.hasText(mailHost) && StringUtils.hasText(props.notifyEmail()) && mailSender.getIfAvailable() != null;
    }

    @Override
    public void notifyAdmin(String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(props.mailFrom());
        msg.setTo(props.notifyEmail());
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.getObject().send(msg);
    }
}
