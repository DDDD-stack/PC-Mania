package al.pcmania.service;

import al.pcmania.domain.Enums.ContactMethod;
import al.pcmania.domain.Enums.DeliveryMethod;
import al.pcmania.domain.Enums.PaymentMethod;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Enums.TradeMediaType;
import al.pcmania.domain.Enums.TradeStatus;
import al.pcmania.domain.Order;
import al.pcmania.domain.Product;
import al.pcmania.domain.TradeRequest;
import al.pcmania.repo.OrderSequenceRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.repo.TradeRequestRepository;
import al.pcmania.web.site.TradeForm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.UUID;

/**
 * "Nderro": trade-in quote requests. Submitting one never creates an order or reserves the product;
 * the operator values the item by hand from the proof, and only an accepted quote becomes an order.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeService {

    /** How long a quote stands unless the operator says otherwise. */
    public static final int DEFAULT_QUOTE_DAYS = 7;
    /** Proof media is kept this long after a request closes, then deleted to free the database. */
    static final Duration KEEP_MEDIA_AFTER_CLOSE = Duration.ofDays(30);
    static final String MEDIA_PREFIX = "trade/";

    /** A problem with the uploaded proof, shown next to the upload field. */
    public static class MediaException extends IllegalArgumentException {
        public MediaException(String message) { super(message); }
    }

    /** Details an order needs that a trade-in request does not collect. */
    public record OrderDetails(String phone, String city, String address, DeliveryMethod delivery, PaymentMethod payment) {}

    private final TradeRequestRepository repo;
    private final ProductRepository products;
    private final OrderSequenceRepository sequence;
    private final FileStorage files;
    private final OrderService orders;
    private final ProductAdminService productAdmin;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;

    /** Whether a product can be asked about: offered for trade and still for sale. */
    public static boolean tradeable(Product p) {
        return p.isTradeEligible() && p.getStatus() == ProductStatus.ACTIVE && p.getQuantity() > 0;
    }

    // ---- Customer ----

    @Transactional
    public TradeRequest submit(String productSlug, TradeForm form, MultipartFile media) throws IOException {
        Product p = products.findBySlug(productSlug).filter(TradeService::tradeable).orElseThrow(NotFoundException::new);
        boolean hasMedia = media != null && !media.isEmpty();
        if (!hasMedia && !form.isWhatsappInstead()) {
            throw new MediaException("Ngarkoni videon ose foton e testit, ose zgjidhni ta dërgoni në WhatsApp.");
        }

        TradeRequest t = new TradeRequest();
        int year = Year.now().getValue();
        t.setRequestNumber("TR-%d-%04d".formatted(year, sequence.nextTrade(year)));
        t.setProduct(p);
        t.setProductTitleSnapshot(p.getTitle());
        t.setCustomerName(form.getCustomerName().trim());
        t.setContactMethod(form.getContactMethod());
        // Exactly one contact is kept: the one the customer chose.
        if (form.getContactMethod() == ContactMethod.EMAIL) t.setCustomerEmail(form.getCustomerEmail().trim());
        else t.setCustomerPhone(form.getCustomerPhone().trim());
        t.setItemType(form.getItemType());
        t.setManufacturer(form.getManufacturer().trim());
        t.setModel(form.getModel().trim());
        t.setExtraNotes(StringUtils.hasText(form.getExtraNotes()) ? form.getExtraNotes().trim() : null);
        t.setWhatsappInstead(form.isWhatsappInstead());
        t.setStatus(TradeStatus.NEW);
        if (hasMedia) storeMedia(t, media);
        repo.save(t);
        events.publishEvent(new NotificationService.TradeRequested(t.getId()));
        return t;
    }

    private void storeMedia(TradeRequest t, MultipartFile media) throws IOException {
        TradeMedia kind;
        try (InputStream in = media.getInputStream()) {
            kind = TradeMedia.detect(in);
        }
        if (kind == null) throw new MediaException("Skedari duhet të jetë video MP4/MOV ose foto JPG/PNG.");
        if (media.getSize() > kind.maxBytes()) {
            throw new MediaException(kind.type() == TradeMediaType.VIDEO
                    ? "Videoja është më e madhe se 40 MB. Shkurtojeni, ulni cilësinë, ose dërgojeni në WhatsApp."
                    : "Fotoja është më e madhe se 10 MB.");
        }
        if (files.totalSizeUnder(MEDIA_PREFIX) + media.getSize() > TradeMedia.MAX_TOTAL_BYTES) {
            log.warn("Trade-in media storage is full; refused {} bytes for {}", media.getSize(), t.getRequestNumber());
            throw new MediaException("Hapësira për video është plot për momentin. Zgjidhni \"Do ta dërgoj videon në WhatsApp\".");
        }
        String key = MEDIA_PREFIX + UUID.randomUUID().toString().replace("-", "") + "." + kind.extension();
        String label = media.getOriginalFilename() == null ? null
                : media.getOriginalFilename().substring(0, Math.min(media.getOriginalFilename().length(), 200));
        try (InputStream in = media.getInputStream()) {
            files.putStream(key, kind.contentType(), in, media.getSize(), label);
        }
        t.setMediaFilename(key);
        t.setMediaType(kind.type());
    }

    // ---- Operator ----

    public TradeRequest get(Long id) {
        return repo.findWithProductById(id).orElseThrow(NotFoundException::new);
    }

    @Transactional
    public void markReviewing(Long id) {
        TradeRequest t = get(id);
        require(t.getStatus() == TradeStatus.NEW, "Kërkesa nuk është më e re.");
        t.setStatus(TradeStatus.REVIEWING);
    }

    /**
     * Records the agreed value and starts the expiry clock. Email customers are sent the quote; phone
     * customers, or anyone the email could not reach, are flagged "call" in the admin list instead.
     * Returns whether an email went out.
     */
    @Transactional
    public boolean quote(Long id, int valueLek, String notes, Integer days) {
        TradeRequest t = get(id);
        require(t.getStatus() == TradeStatus.NEW || t.getStatus() == TradeStatus.REVIEWING || t.getStatus() == TradeStatus.QUOTED,
                "Kjo kërkesë nuk mund të marrë më ofertë.");
        require(t.getProduct() != null, "Produkti i kërkuar nuk ekziston më.");
        require(valueLek > 0, "Shkruani vlerën e ofertës.");
        require(valueLek < t.getProduct().getPriceLek(), "Vlera e këmbimit duhet të jetë më e vogël se çmimi i produktit.");
        int validDays = days == null || days < 1 ? DEFAULT_QUOTE_DAYS : Math.min(days, 60);
        t.setQuotedValueLek(valueLek);
        t.setQuoteNotes(StringUtils.hasText(notes) ? notes.trim() : null);
        t.setQuotedAt(LocalDateTime.now());
        t.setQuoteExpiresAt(LocalDateTime.now().plusDays(validDays));
        t.setQuoteEmailedAt(null);
        t.setStatus(TradeStatus.QUOTED);
        if (t.getContactMethod() == ContactMethod.EMAIL && notifications.sendTradeQuote(t)) {
            t.setQuoteEmailedAt(LocalDateTime.now());
            return true;
        }
        return false;
    }

    @Transactional
    public void decline(Long id, String reason) {
        TradeRequest t = get(id);
        require(t.getStatus().isOpen(), "Kërkesa është mbyllur tashmë.");
        require(StringUtils.hasText(reason), "Shkruani arsyen e refuzimit.");
        t.setDeclineReason(reason.trim());
        t.close(TradeStatus.DECLINED);
    }

    @Transactional
    public void accept(Long id) {
        TradeRequest t = get(id);
        require(t.getStatus() == TradeStatus.QUOTED, "Vetëm një ofertë e dhënë mund të pranohet.");
        require(t.getQuoteExpiresAt() == null || t.getQuoteExpiresAt().isAfter(LocalDateTime.now()),
                "Oferta ka skaduar. Jepni një ofertë të re.");
        t.setStatus(TradeStatus.ACCEPTED);
    }

    /** Turns an accepted trade-in into an order for the product it was made against. */
    @Transactional
    public Order convert(Long id, OrderDetails details) {
        TradeRequest t = get(id);
        require(t.getStatus() == TradeStatus.ACCEPTED, "Vetëm një ofertë e pranuar mund të bëhet porosi.");
        require(t.getProduct() != null, "Produkti i kërkuar nuk ekziston më.");
        Order o = orders.placeFromTrade(t, details);
        t.setOrderId(o.getId());
        t.close(TradeStatus.CONVERTED);
        return o;
    }

    /**
     * Takes the traded-in item into stock as a Draft product, its cost the trade credit given, so it can
     * be photographed, priced and listed like anything else. Returns the new product's id.
     */
    @Transactional
    public Long takeIntoStock(Long id) {
        TradeRequest t = get(id);
        require(t.getStatus() == TradeStatus.CONVERTED, "Vetëm këmbimet e kryera merren në stok.");
        require(t.getStockProductId() == null, "Ky artikull është marrë tashmë në stok.");
        String fallbackCategory = t.getProduct() == null ? null : t.getProduct().getCategorySlug();
        Product p = productAdmin.createDraftFromTradeIn(t, fallbackCategory);
        t.setStockProductId(p.getId());
        return p.getId();
    }

    @Transactional
    public void deleteMedia(Long id) {
        TradeRequest t = get(id);
        if (t.getMediaFilename() == null) return;
        files.delete(t.getMediaFilename());
        t.setMediaFilename(null);
    }

    // ---- Housekeeping ----

    /** Hourly: unanswered quotes lapse, and proof media goes 30 days after a request closes. */
    @Scheduled(cron = "0 20 * * * *", zone = "Europe/Tirane")
    @Transactional
    public void housekeeping() {
        LocalDateTime now = LocalDateTime.now();
        for (TradeRequest t : repo.findByStatusAndQuoteExpiresAtBefore(TradeStatus.QUOTED, now)) {
            t.close(TradeStatus.EXPIRED);
            log.info("Trade-in quote {} expired", t.getRequestNumber());
        }
        for (TradeRequest t : repo.findMediaToDelete(now.minus(KEEP_MEDIA_AFTER_CLOSE))) {
            files.delete(t.getMediaFilename());
            t.setMediaFilename(null);
            log.info("Deleted trade-in proof for {}", t.getRequestNumber());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
