package al.pcmania.service;

import al.pcmania.config.AppProperties;
import al.pcmania.domain.BuildRequest;
import al.pcmania.domain.Enums.TradeMediaType;
import al.pcmania.domain.Order;
import al.pcmania.domain.OrderItem;
import al.pcmania.domain.TradeRequest;
import al.pcmania.domain.WishRequest;
import al.pcmania.repo.BuildRequestRepository;
import al.pcmania.repo.OrderRepository;
import al.pcmania.repo.TradeRequestRepository;
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

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    public record BuildRequested(Long buildRequestId) {}

    public record WishRequested(Long wishRequestId) {}

    public record TradeRequested(Long tradeRequestId) {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ObjectProvider<JavaMailSender> mailSender;
    private final OrderRepository orders;
    private final BuildRequestRepository builds;
    private final WishRequestRepository wishes;
    private final TradeRequestRepository trades;
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

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onTradeRequested(TradeRequested event) {
        TradeRequest t = trades.findWithProductById(event.tradeRequestId()).orElse(null);
        if (t == null) return;
        String proof = t.getMediaFilename() != null ? (t.getMediaType() == TradeMediaType.VIDEO ? "Video e ngarkuar" : "Foto e ngarkuar")
                : t.isWhatsappInstead() ? "Do ta dërgojë në WhatsApp" : "-";
        String subject = "Kërkesë për këmbim " + t.getRequestNumber() + " – " + t.getManufacturer() + " " + t.getModel();
        String body = """
                Kërkesë e re për këmbim (Nderro)

                Nr.: %s
                Klienti: %s
                Kontakti: %s (%s)

                Jep: %s – %s %s
                Shënime: %s
                Prova: %s

                Kërkon: %s (%s)

                Hape në admin: %s/admin/trades/%d
                """.formatted(t.getRequestNumber(), t.getCustomerName(), t.getContact(), t.getContactMethod().label,
                t.getItemType().label, t.getManufacturer(), t.getModel(), orDash(t.getExtraNotes()), proof,
                t.getProductTitleSnapshot(), t.getProduct() == null ? "-" : Fmt.lek(t.getProduct().getPriceLek()),
                props.base(), t.getId());
        send(props.notifyEmail(), subject, body);
    }

    public boolean sendTradeQuote(TradeRequest t) {
        int price = t.getProduct().getPriceLek();
        String shipping = t.getProduct().isTransportIncluded() ? "transporti falas" : "pa transportin";
        String body = """
                Përshëndetje %s,

                Faleminderit për kërkesën %s për të ndërruar %s %s me %s.

                Vlera që ju ofrojmë për pajisjen tuaj: %s
                Çmimi i produktit: %s
                Pas këmbimit paguani: %s (%s)
                %s
                Oferta vlen deri më %s.

                Për ta pranuar, na telefononi në %s ose na shkruani në WhatsApp, dhe përmendni numrin %s.

                PCMania
                %s

                (Ky email dërgohet automatikisht. Mos iu përgjigjni këtij emaili.)
                """.formatted(t.getCustomerName(), t.getRequestNumber(), t.getManufacturer(), t.getModel(),
                t.getProductTitleSnapshot(), Fmt.lek(t.getQuotedValueLek()), Fmt.lek(price),
                Fmt.lek(price - t.getQuotedValueLek()), shipping,
                StringUtils.hasText(t.getQuoteNotes()) ? "\nShënim: " + t.getQuoteNotes() + "\n" : "",
                DATE.format(t.getQuoteExpiresAt()), props.phoneDisplay(), t.getRequestNumber(), props.base());
        return send(t.getCustomerEmail(), "PCMania – oferta për këmbimin " + t.getRequestNumber(), body);
    }

    private boolean send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!StringUtils.hasText(mailHost) || sender == null || !StringUtils.hasText(to)) {
            log.info("Email not sent (mail not configured). To: {} | {}\n{}", to, subject, body);
            return false;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.mailFrom());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
            log.info("Email sent to {}: {}", to, subject);
            return true;
        } catch (RuntimeException e) {
            log.error("Failed to send email to {} ({}): {}\n{}", to, subject, e.getMessage(), body);
            return false;
        }
    }

    private static String orDash(String s) {
        return StringUtils.hasText(s) ? s : "-";
    }
}
