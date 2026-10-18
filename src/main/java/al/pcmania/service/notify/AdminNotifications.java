package al.pcmania.service.notify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotifications {

    private final List<AdminNotifier> notifiers;

    public int notifyAll(String subject, String body) {
        int sent = 0;
        for (AdminNotifier n : notifiers) {
            if (!n.isEnabled()) continue;
            try {
                n.notifyAdmin(subject, body);
                sent++;
                log.info("{} notification sent: {}", n.name(), subject);
            } catch (RuntimeException e) {
                log.error("{} notification failed ({}): {}", n.name(), subject, e.getMessage());
            }
        }
        if (sent == 0) log.info("Admin notification (no notifier delivered it): {}\n{}", subject, body);
        return sent;
    }

    public List<String> enabled() {
        return notifiers.stream().filter(AdminNotifier::isEnabled).map(AdminNotifier::name).toList();
    }
}
