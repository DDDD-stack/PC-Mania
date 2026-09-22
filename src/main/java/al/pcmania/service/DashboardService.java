package al.pcmania.service;

import al.pcmania.domain.Enums.OrderStatus;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Order;
import al.pcmania.domain.OrderItem;
import al.pcmania.domain.Product;
import al.pcmania.repo.BuildRequestRepository;
import al.pcmania.repo.OrderRepository;
import al.pcmania.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Margin, turnover and aging figures. A "sale" is an order item in a DELIVERED order; its cost and price come from
 * the order-item snapshots, and days-to-sell runs from the product's listedAt to the order's deliveredAt.
 * Volumes for a single-operator shop are small, so everything is computed in memory.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    /** Price bands by unit sale price, in Lek. Upper bound is exclusive; null means open-ended. */
    public static final List<Band> BANDS = List.of(
            new Band("0 – 100k", 0, 100_000),
            new Band("100k – 150k", 100_000, 150_000),
            new Band("150k – 200k", 150_000, 200_000),
            new Band("200k – 250k", 200_000, 250_000),
            new Band("250k+", 250_000, null));

    public record Band(String label, int min, Integer max) {
        boolean contains(int price) {
            return price >= min && (max == null || price < max);
        }
    }

    public record SoldItem(Long orderId, String orderNumber, Long productId, String title, int quantity,
                           int unitCost, int unitPrice, LocalDateTime listedAt, LocalDateTime soldAt, Long daysToSell) {
        public int getRevenue() { return unitPrice * quantity; }
        public int getProfit() { return (unitPrice - unitCost) * quantity; }
        public double getMarginPct() { return unitPrice == 0 ? 0 : 100.0 * (unitPrice - unitCost) / unitPrice; }
    }

    public record BandStats(Band band, int unitsSold, long revenue, long profit, Double avgMarginPct, Double avgDaysToSell,
                            int unitsInStock, long stockCost) {}

    public record Totals(int units, long revenue, long profit, Double marginPct, Double avgDaysToSell) {}

    public record AgingRow(Product product, long days) {}

    public record Dashboard(
            int stockUnits, long stockCost, int reservedUnits, long reservedCost,
            Totals month, Totals allTime,
            List<BandStats> bands, Band fastestBand, double maxBandDays, long maxBandProfit,
            List<SoldItem> sold, List<AgingRow> aging,
            long newOrders, long openOrders, long newBuildRequests) {}

    private final ProductRepository products;
    private final OrderRepository orders;
    private final BuildRequestRepository builds;

    public Dashboard build() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();

        // Unsold stock: available units on any non-sold product, valued at current cost.
        List<Product> unsold = products.findByStatusNot(ProductStatus.SOLD);
        int stockUnits = unsold.stream().mapToInt(Product::getQuantity).sum();
        long stockCost = unsold.stream().mapToLong(p -> (long) p.getQuantity() * p.getCostLek()).sum();

        // Units already taken out of stock by orders not yet delivered (still physically ours).
        List<OrderItem> openItems = orders.findWithItemsByStatusIn(List.of(OrderStatus.NEW, OrderStatus.CONFIRMED, OrderStatus.SHIPPED))
                .stream().flatMap(o -> o.getItems().stream()).toList();
        int reservedUnits = openItems.stream().mapToInt(OrderItem::getQuantity).sum();
        long reservedCost = openItems.stream().mapToLong(i -> (long) i.getQuantity() * i.getCostLekSnapshot()).sum();

        List<SoldItem> sold = new ArrayList<>();
        for (Order o : orders.findWithItemsByStatusIn(List.of(OrderStatus.DELIVERED))) {
            LocalDateTime soldAt = o.getDeliveredAt() != null ? o.getDeliveredAt() : o.getCreatedAt();
            for (OrderItem i : o.getItems()) {
                Product p = i.getProduct();
                LocalDateTime listedAt = p == null ? null : p.getListedAt();
                Long days = listedAt == null ? null : Math.max(0, ChronoUnit.DAYS.between(listedAt, soldAt));
                sold.add(new SoldItem(o.getId(), o.getOrderNumber(), p == null ? null : p.getId(), i.getTitleSnapshot(),
                        i.getQuantity(), i.getCostLekSnapshot(), i.getPriceLekSnapshot(), listedAt, soldAt, days));
            }
        }
        sold.sort(Comparator.comparing(SoldItem::soldAt).reversed());

        List<BandStats> bands = new ArrayList<>();
        for (Band band : BANDS) {
            List<SoldItem> inBand = sold.stream().filter(s -> band.contains(s.unitPrice())).toList();
            Totals t = totals(inBand);
            List<Product> stockInBand = unsold.stream()
                    .filter(p -> p.getStatus() == ProductStatus.ACTIVE && band.contains(p.getPriceLek())).toList();
            bands.add(new BandStats(band, t.units(), t.revenue(), t.profit(), t.marginPct(), t.avgDaysToSell(),
                    stockInBand.stream().mapToInt(Product::getQuantity).sum(),
                    stockInBand.stream().mapToLong(p -> (long) p.getQuantity() * p.getCostLek()).sum()));
        }
        Band fastest = bands.stream().filter(b -> b.avgDaysToSell() != null)
                .min(Comparator.comparingDouble(BandStats::avgDaysToSell)).map(BandStats::band).orElse(null);
        double maxDays = bands.stream().filter(b -> b.avgDaysToSell() != null).mapToDouble(BandStats::avgDaysToSell).max().orElse(0);
        long maxProfit = bands.stream().mapToLong(b -> Math.max(0, b.profit())).max().orElse(0);

        List<AgingRow> aging = products.findByStatusOrderByListedAtAsc(ProductStatus.ACTIVE).stream()
                .map(p -> new AgingRow(p, p.getListedAt() == null ? 0 : ChronoUnit.DAYS.between(p.getListedAt(), now)))
                .toList();

        return new Dashboard(stockUnits, stockCost, reservedUnits, reservedCost,
                totals(sold.stream().filter(s -> !s.soldAt().isBefore(monthStart)).toList()), totals(sold),
                bands, fastest, maxDays, maxProfit, sold, aging,
                orders.countByStatus(OrderStatus.NEW),
                orders.countByStatus(OrderStatus.CONFIRMED) + orders.countByStatus(OrderStatus.SHIPPED),
                builds.countByStatus(al.pcmania.domain.Enums.BuildStatus.NEW));
    }

    /** Margin is revenue-weighted; days-to-sell is averaged per unit. */
    static Totals totals(List<SoldItem> items) {
        int units = items.stream().mapToInt(SoldItem::quantity).sum();
        long revenue = items.stream().mapToLong(SoldItem::getRevenue).sum();
        long profit = items.stream().mapToLong(SoldItem::getProfit).sum();
        Double margin = revenue == 0 ? null : 100.0 * profit / revenue;
        long dayUnits = items.stream().filter(s -> s.daysToSell() != null).mapToLong(SoldItem::quantity).sum();
        Double avgDays = dayUnits == 0 ? null
                : (double) items.stream().filter(s -> s.daysToSell() != null).mapToLong(s -> s.daysToSell() * s.quantity()).sum() / dayUnits;
        return new Totals(units, revenue, profit, margin, avgDays);
    }
}
