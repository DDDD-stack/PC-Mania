package al.pcmania.service;

import al.pcmania.config.AppProperties;
import al.pcmania.domain.Enums.DeliveryMethod;
import al.pcmania.domain.Enums.OrderStatus;
import al.pcmania.domain.Enums.PaymentMethod;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Order;
import al.pcmania.domain.OrderItem;
import al.pcmania.domain.Product;
import al.pcmania.repo.OrderItemRepository;
import al.pcmania.repo.OrderRepository;
import al.pcmania.repo.OrderSequenceRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.web.site.CheckoutForm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;

@Service
@RequiredArgsConstructor
public class OrderService {

    /** Thrown when the product was sold or reserved between page load and submit. */
    public static class OutOfStockException extends RuntimeException {}

    /** Published after commit so the operator gets an email. */
    public record OrderPlaced(Long orderId) {}

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderSequenceRepository sequence;
    private final ProductRepository products;
    private final AppProperties props;
    private final ApplicationEventPublisher events;
    private final EntityManager em;

    public int shippingFor(DeliveryMethod method, boolean transportIncluded) {
        return method == DeliveryMethod.COURIER && !transportIncluded ? props.courierShippingLek() : 0;
    }

    /** Creates an order for a single product and reserves the stock. */
    @Transactional
    public Order place(String productSlug, CheckoutForm form) {
        // Row lock + refresh so concurrent orders can't oversell.
        Long productId = products.findIdBySlug(productSlug).orElseThrow(NotFoundException::new);
        Product p = lock(productId);
        int qty = form.getQuantity();
        if (p.getStatus() != ProductStatus.ACTIVE || p.getQuantity() < qty) throw new OutOfStockException();

        Order o = new Order();
        o.setOrderNumber(nextOrderNumber());
        o.setCustomerName(form.getCustomerName().trim());
        o.setCustomerPhone(form.getCustomerPhone().trim());
        o.setCustomerEmail(StringUtils.hasText(form.getCustomerEmail()) ? form.getCustomerEmail().trim() : null);
        o.setCity(form.getCity().trim());
        o.setAddress(StringUtils.hasText(form.getAddress()) ? form.getAddress().trim() : null);
        o.setCustomerNotes(StringUtils.hasText(form.getNotes()) ? form.getNotes().trim() : null);
        o.setDeliveryMethod(form.getDeliveryMethod());
        o.setPaymentMethod(form.getPaymentMethod());
        o.setStatus(OrderStatus.NEW);
        addItem(o, p, qty, p.getPriceLek());
        o.setShippingLek(shippingFor(form.getDeliveryMethod(), p.isTransportIncluded()));
        o.setTotalLek(o.getSubtotalLek() + o.getShippingLek());

        p.setQuantity(p.getQuantity() - qty);
        if (p.getQuantity() == 0) p.changeStatus(ProductStatus.RESERVED);

        orders.save(o);
        events.publishEvent(new OrderPlaced(o.getId()));
        return o;
    }

    /**
     * Records a sale made outside the website (e.g. agreed on Facebook) as a delivered order,
     * so the dashboard's margin and days-to-sell figures include it.
     */
    @Transactional
    public Order recordOfflineSale(Long productId, int qty, int priceLek, LocalDate date, String buyer) {
        Product p = lock(productId);
        if (qty < 1 || p.getQuantity() < qty) throw new OutOfStockException();
        LocalDateTime when = date == null || date.equals(LocalDate.now()) ? LocalDateTime.now() : date.atTime(LocalTime.NOON);

        Order o = new Order();
        o.setOrderNumber(nextOrderNumber());
        o.setCustomerName(StringUtils.hasText(buyer) ? buyer.trim() : "Shitje direkte");
        o.setCustomerPhone("-");
        o.setCity("Tiranë");
        o.setDeliveryMethod(DeliveryMethod.PICKUP_TIRANA);
        o.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        o.setStatus(OrderStatus.DELIVERED);
        o.setCreatedAt(when);
        o.setDeliveredAt(when);
        o.setAdminNotes("Shitje jashtë faqes (regjistruar nga admini).");
        addItem(o, p, qty, priceLek);
        o.setTotalLek(o.getSubtotalLek());

        p.setQuantity(p.getQuantity() - qty);
        if (p.getQuantity() == 0) {
            p.changeStatus(ProductStatus.SOLD);
            p.setSoldAt(when);
        }
        return orders.save(o);
    }

    @Transactional
    public void changeStatus(Long orderId, OrderStatus target) {
        Order o = orders.findWithItems(orderId).orElseThrow(NotFoundException::new);
        if (!o.allowedTransitions().contains(target)) {
            throw new IllegalStateException("Kalimi " + o.getStatus().label + " → " + target.label + " nuk lejohet.");
        }
        o.setStatus(target);
        for (OrderItem item : o.getItems()) {
            if (item.getProduct() == null) continue;
            Product p = lock(item.getProduct().getId());
            if (target == OrderStatus.CANCELLED) {
                p.setQuantity(p.getQuantity() + item.getQuantity());
                if (p.getStatus() == ProductStatus.RESERVED || p.getStatus() == ProductStatus.SOLD) p.changeStatus(ProductStatus.ACTIVE);
            } else if (target == OrderStatus.DELIVERED && p.getQuantity() == 0
                    && p.getStatus() == ProductStatus.RESERVED && !orderItems.existsOpenForProduct(p.getId(), o.getId())) {
                p.changeStatus(ProductStatus.SOLD);
            }
        }
        if (target == OrderStatus.DELIVERED) o.setDeliveredAt(LocalDateTime.now());
    }

    @Transactional
    public void updateNotes(Long orderId, String notes) {
        Order o = orders.findById(orderId).orElseThrow(NotFoundException::new);
        o.setAdminNotes(StringUtils.hasText(notes) ? notes.trim() : null);
    }

    /**
     * Locks the product row and reloads its state. A plain locking query is not enough: with open-session-in-view
     * the product may already be managed (loaded earlier in the request) and Hibernate would return stale state.
     */
    private Product lock(Long productId) {
        Product p = em.find(Product.class, productId);
        if (p == null) throw new NotFoundException();
        em.refresh(p, LockModeType.PESSIMISTIC_WRITE);
        return p;
    }

    private void addItem(Order o, Product p, int qty, int priceLek) {
        OrderItem item = new OrderItem();
        item.setOrder(o);
        item.setProduct(p);
        item.setTitleSnapshot(p.getTitle());
        item.setPriceLekSnapshot(priceLek);
        item.setCostLekSnapshot(p.getCostLek());
        item.setQuantity(qty);
        o.getItems().add(item);
        o.setSubtotalLek(o.getItems().stream().mapToInt(OrderItem::getLineTotalLek).sum());
    }

    private String nextOrderNumber() {
        int year = Year.now().getValue();
        return "PM-%d-%04d".formatted(year, sequence.next(year));
    }
}
