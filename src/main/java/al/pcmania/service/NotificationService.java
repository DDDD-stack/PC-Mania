package al.pcmania.service;

import al.pcmania.config.AppProperties;
import al.pcmania.domain.BuildRequest;
import al.pcmania.domain.Order;
import al.pcmania.domain.OrderItem;
import al.pcmania.domain.WishRequest;
import al.pcmania.repo.BuildRequestRepository;
import al.pcmania.repo.OrderRepository;
import al.pcmania.repo.WishRequestRepository;
import al.pcmania.web.Fmt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * Emails the operator about new orders and build requests. Runs after commit and asynchronously,
 * so a mail server problem never breaks checkout. Without MAIL_HOST configured it only logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    /** Published after a build request is saved. */
    public record BuildRequested(Long buildRequestId) {}

    /** Published after a customer asks the shop to bring something in. */
    public record WishRequested(Long wishRequestId) {}

    private final ObjectProvider<JavaMailSender> mailSender;
    private final OrderRepository orders;
    private final BuildRequestRepository builds;
    private final WishRequestRepository wishes;
    private final AppProperties props;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onOrderPlaced(OrderService.OrderPlaced event) {
        Order o = orders.findWithItems(event.orderId()).orElse(null);
        if (o == null) return;
        StringBuilder items = new StringBuilder();
        for (OrderItem i : o.getItems()) {
            items.append("  • ").append(i.getQuantity()).append(" × ").append(i.getTitleSnapshot())
                    .append(" – ").append(Fmt.lek(i.getPriceLekSnapshot())).append('\n');
        }
        String subject = "Porosi e re " + o.getOrderNumber() + " – " + Fmt.lek(o.getTotalLek());
        String body = """
                Porosi e re në PCMania

                Nr. porosisë: %s
                Klienti: %s
                Telefoni: %s
                Email: %s
                Qyteti: %s
                Adresa: %s
                Dorëzimi: %s
                Pagesa: %s

                Produktet:
                %s
                Nëntotali: %s
                Transporti: %s
                TOTALI: %s

                Shënime nga klienti:
                %s

                Hape në admin: %s/admin/orders/%d
                """.formatted(o.getOrderNumber(), o.getCustomerName(), o.getCustomerPhone(), orDash(o.getCustomerEmail()),
                o.getCity(), orDash(o.getAddress()), o.getDeliveryMethod().label, o.getPaymentMethod().label,
                items, Fmt.lek(o.getSubtotalLek()), Fmt.lek(o.getShippingLek()), Fmt.lek(o.getTotalLek()),
                orDash(o.getCustomerNotes()), props.base(), o.getId());
        send(props.notifyEmail(), subject, body);

        if (StringUtils.hasText(o.getCustomerEmail())) {
            send(o.getCustomerEmail(), "PCMania – porosia juaj " + o.getOrderNumber(), """
                    Faleminderit për porosinë, %s!

                    Numri i porosisë: %s
                    %s
                    Totali: %s (%s)

                    Do t'ju telefonojmë së shpejti në %s për të konfirmuar porosinë dhe dorëzimin.

                    PCMania
                    %s
                    """.formatted(o.getCustomerName(), o.getOrderNumber(), items, Fmt.lek(o.getTotalLek()),
                    o.getPaymentMethod().label, o.getCustomerPhone(), props.phoneDisplay()));
        }
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onBuildRequested(BuildRequested event) {
        BuildRequest b = builds.findById(event.buildRequestId()).orElse(null);
        if (b == null) return;
        String subject = "Kërkesë e re për PC – " + Fmt.lek(b.getBudgetLek()) + " – " + b.getUseCase().label;
        String body = """
                Kërkesë e re për ndërtim PC

                Klienti: %s
                Telefoni: %s
                Buxheti: %s
                Përdorimi: %s

                Shënime:
                %s

                Hape në admin: %s/admin/builds/%d
                """.formatted(b.getCustomerName(), b.getCustomerPhone(), Fmt.lek(b.getBudgetLek()), b.getUseCase().label,
                orDash(b.getNotes()), props.base(), b.getId());
        send(props.notifyEmail(), subject, body);
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onWishRequested(WishRequested event) {
        WishRequest w = wishes.findById(event.wishRequestId()).orElse(null);
        if (w == null) return;
        String subject = "Kërkesë për produkt – " + w.getItem();
        String body = """
                Një klient kërkon një produkt

                Kërkon: %s
                Buxheti: %s
                Gjendja: %s
                Klienti: %s
                Telefoni: %s

                Shënime:
                %s

                Hape në admin: %s/admin/wishes/%d
                """.formatted(w.getItem(), w.getMaxPriceLek() == null ? "-" : Fmt.lek(w.getMaxPriceLek()),
                w.getCondition() == null ? "Nuk ka rëndësi" : w.getCondition().label, w.getCustomerName(),
                w.getCustomerPhone(), orDash(w.getNotes()), props.base(), w.getId());
        send(props.notifyEmail(), subject, body);
    }

    private void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!StringUtils.hasText(mailHost) || sender == null || !StringUtils.hasText(to)) {
            log.info("Email not sent (mail not configured). To: {} | {}\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.mailFrom());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
            log.info("Email sent to {}: {}", to, subject);
        } catch (RuntimeException e) {
            log.error("Failed to send email to {} ({}): {}\n{}", to, subject, e.getMessage(), body);
        }
    }

    private static String orDash(String s) {
        return StringUtils.hasText(s) ? s : "-";
    }
}
