package al.pcmania.web.api;

import al.pcmania.domain.BuildRequest;
import al.pcmania.domain.Order;
import al.pcmania.domain.OrderItem;
import al.pcmania.domain.Product;
import al.pcmania.domain.ProductImage;
import al.pcmania.domain.UpcomingProduct;
import al.pcmania.service.ImageStorage;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

/** JSON shapes for the mobile admin API. Unlike the public site, these include cost and margin. */
public final class ApiDtos {

    private ApiDtos() {}

    public record LoginRequest(String username, String password, String deviceName) {}

    public record LoginResponse(String token, String username) {}

    public record ErrorResponse(String error, String message) {}

    public record Option(String value, String label) {}

    public record PageDto<T>(List<T> items, int page, int totalPages, long totalElements) {
        static <E, T> PageDto<T> of(Page<E> page, Function<E, T> map) {
            return new PageDto<>(page.getContent().stream().map(map).toList(), page.getNumber(), page.getTotalPages(), page.getTotalElements());
        }
    }

    public record Summary(
            long newOrders, long inProgressOrders, long newBuildRequests,
            int soldUnitsThisMonth, long revenueThisMonth, long profitThisMonth,
            long profitAllTime, Double marginPct, Double avgDaysToSell,
            int stockUnits, long stockCost, int reservedUnits,
            long activeProducts, long slowProducts, String fastestBand,
            Long latestOrderId) {}

    public record OrderRow(Long id, String orderNumber, String status, String statusLabel, String customerName,
                           String customerPhone, String city, String delivery, int totalLek, int itemCount,
                           String itemsSummary, LocalDateTime createdAt) {
        static OrderRow of(Order o, List<OrderItem> items) {
            String summary = items.isEmpty() ? "" : items.getFirst().getTitleSnapshot()
                    + (items.size() > 1 ? " +" + (items.size() - 1) : "");
            return new OrderRow(o.getId(), o.getOrderNumber(), o.getStatus().name(), o.getStatus().label, o.getCustomerName(),
                    o.getCustomerPhone(), o.getCity(), o.getDeliveryMethod().label, o.getTotalLek(),
                    items.stream().mapToInt(OrderItem::getQuantity).sum(), summary, o.getCreatedAt());
        }
    }

    public record OrderItemDto(Long productId, String title, int quantity, int priceLek, int costLek, String thumbUrl) {}

    public record OrderDetail(Long id, String orderNumber, String status, String statusLabel, List<Option> transitions,
                              String customerName, String customerPhone, String customerEmail, String city, String address,
                              String customerNotes, String adminNotes, String delivery, String payment,
                              int subtotalLek, int shippingLek, int totalLek, int profitLek, List<OrderItemDto> items,
                              String whatsappUrl, LocalDateTime createdAt, LocalDateTime deliveredAt) {}

    public record ProductRow(Long id, String title, String status, String statusLabel, String condition,
                             String conditionValue, String shortDescription, String category,
                             int priceLek, int costLek, int quantity, String thumbUrl, Long daysListed, int viewCount,
                             String publicUrl) {
        static ProductRow of(Product p, String base) {
            ProductImage img = p.getPrimaryImage();
            return new ProductRow(p.getId(), p.getTitle(), p.getStatus().name(), p.getStatus().label, p.getCondition().label,
                    p.getCondition().name(), p.getShortDescription(),
                    p.getCategorySlug(), p.getPriceLek(), p.getCostLek(), p.getQuantity(),
                    img == null ? null : base + ImageStorage.url(ImageStorage.Size.thumb, img.getFilename()),
                    p.getListedAt() == null ? null : ChronoUnit.DAYS.between(p.getListedAt(), LocalDateTime.now()),
                    p.getViewCount(), base + "/produkt/" + p.getSlug());
        }
    }

    public record BuildRow(Long id, String status, String statusLabel, String customerName, String customerPhone,
                           int budgetLek, String useCase, Integer quotedTotalLek, String notesPreview,
                           LocalDateTime createdAt) {
        static BuildRow of(BuildRequest b) {
            String notes = b.getNotes() == null ? "" : b.getNotes().strip();
            if (notes.length() > 120) notes = notes.substring(0, 119).strip() + "…";
            return new BuildRow(b.getId(), b.getStatus().name(), b.getStatus().label, b.getCustomerName(),
                    b.getCustomerPhone(), b.getBudgetLek(), b.getUseCase().label, b.getQuotedTotalLek(),
                    notes, b.getCreatedAt());
        }
    }

    public record BuildDetail(Long id, String status, String statusLabel, List<Option> statuses, String customerName,
                              String customerPhone, int budgetLek, String useCase, String notes, String adminNotes,
                              Integer quotedTotalLek, String whatsappUrl, LocalDateTime createdAt) {}

    /** Null fields are left unchanged, except adminNotes where an empty string clears the field. */
    public record BuildPatch(String status, Integer quotedTotalLek, String adminNotes) {}

    public record ProductPatch(String status, Integer quantity, Integer priceLek, Integer costLek,
                               String title, String condition, String shortDescription) {}

    public record UpcomingRow(Long id, String title, String slug, String status, String statusLabel, String teaser,
                              String categorySlug, Integer expectedPriceLek, String expectedLabel, String condition,
                              String imageUrl, int sortOrder, long interestCount, Long productId,
                              LocalDateTime createdAt) {
        static UpcomingRow of(UpcomingProduct u, String base, long interestCount) {
            return new UpcomingRow(u.getId(), u.getTitle(), u.getSlug(), u.getStatus().name(), u.getStatus().label,
                    u.getTeaser(), u.getCategorySlug(), u.getExpectedPriceLek(), u.getExpectedLabel(),
                    u.getCondition() == null ? null : u.getCondition().name(),
                    u.getImageFilename() == null ? null : base + ImageStorage.url(ImageStorage.Size.thumb, u.getImageFilename()),
                    u.getSortOrder(), interestCount, u.getProduct() == null ? null : u.getProduct().getId(),
                    u.getCreatedAt());
        }
    }

    public record InterestDto(Long id, String customerName, String customerPhone, boolean notified,
                              String whatsappUrl, LocalDateTime createdAt) {}

    /** Null fields are left unchanged. `condition` and `status` are enum names. */
    public record UpcomingPatch(String title, String teaser, String categorySlug, Integer expectedPriceLek,
                                String expectedLabel, String condition, String status, Integer sortOrder) {}

    public record StatusChange(String status) {}

    public record NotesChange(String notes) {}
}
