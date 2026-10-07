package al.pcmania.web.api;

import al.pcmania.config.AppProperties;
import al.pcmania.domain.BuildRequest;
import al.pcmania.domain.Enums.BuildStatus;
import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.OrderStatus;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Enums.UpcomingStatus;
import al.pcmania.domain.Order;
import al.pcmania.domain.OrderItem;
import al.pcmania.domain.ProductImage;
import al.pcmania.domain.UpcomingProduct;
import al.pcmania.repo.BuildRequestRepository;
import al.pcmania.repo.OrderItemRepository;
import al.pcmania.repo.OrderRepository;
import al.pcmania.service.*;
import al.pcmania.web.Links;
import al.pcmania.web.api.ApiDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AdminApiController {

    static final Map<String, List<OrderStatus>> ORDER_GROUPS = Map.of(
            "new", List.of(OrderStatus.NEW),
            "active", List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED),
            "done", List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED));

    static final Map<String, List<BuildStatus>> BUILD_GROUPS = Map.of(
            "new", List.of(BuildStatus.NEW),
            "active", List.of(BuildStatus.QUOTED, BuildStatus.ACCEPTED),
            "done", List.of(BuildStatus.DECLINED, BuildStatus.CLOSED));

    static final Map<String, List<ProductStatus>> PRODUCT_GROUPS = Map.of(
            "active", List.of(ProductStatus.ACTIVE),
            "reserved", List.of(ProductStatus.RESERVED),
            "hidden", List.of(ProductStatus.DRAFT, ProductStatus.HIDDEN),
            "sold", List.of(ProductStatus.SOLD));

    private final ApiTokenService tokens;
    private final LoginAttemptService attempts;
    private final DashboardService dashboard;
    private final OrderRepository orders;
    private final BuildRequestRepository builds;
    private final BuildRequestService buildService;
    private final OrderItemRepository orderItems;
    private final OrderService orderService;
    private final ProductAdminService productService;
    private final UpcomingService upcoming;
    private final AppProperties props;

    @PostMapping("/auth/login")
    ResponseEntity<?> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (attempts.isBlocked(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse("locked", "Shumë tentativa të dështuara. Provoni pas 15 minutash."));
        }
        return tokens.login(body.username(), body.password(), body.deviceName())
                .<ResponseEntity<?>>map(token -> {
                    attempts.succeeded(ip);
                    return ResponseEntity.ok(new LoginResponse(token, body.username().trim()));
                })
                .orElseGet(() -> {
                    attempts.failed(ip);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(new ErrorResponse("bad_credentials", "Emri ose fjalëkalimi është i gabuar."));
                });
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(@RequestHeader("Authorization") String header) {
        tokens.revoke(header.replaceFirst("^Bearer ", "").trim());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    Summary summary() {
        var d = dashboard.build();
        long activeProducts = d.aging().size();
        long slow = d.aging().stream().filter(a -> a.days() >= 30).count();
        return new Summary(d.newOrders(), d.openOrders(), d.newBuildRequests(),
                d.month().units(), d.month().revenue(), d.month().profit(),
                d.allTime().profit(), d.allTime().marginPct(), d.allTime().avgDaysToSell(),
                d.stockUnits(), d.stockCost(), d.reservedUnits(),
                activeProducts, slow, d.fastestBand() == null ? null : d.fastestBand().label(),
                orders.findTopByOrderByIdDesc().map(Order::getId).orElse(null));
    }

    @GetMapping("/orders")
    PageDto<OrderRow> orders(@RequestParam(defaultValue = "new") String group, @RequestParam(defaultValue = "0") int page) {
        List<OrderStatus> statuses = ORDER_GROUPS.getOrDefault(group, ORDER_GROUPS.get("new"));
        Page<Order> result = orders.findByStatusInOrderByCreatedAtDesc(statuses, PageRequest.of(Math.max(page, 0), 30));
        List<Long> ids = result.getContent().stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> items = ids.isEmpty() ? Map.of()
                : orderItems.findByOrderIds(ids).stream().collect(Collectors.groupingBy(i -> i.getOrder().getId()));
        return PageDto.of(result, o -> OrderRow.of(o, items.getOrDefault(o.getId(), List.of())));
    }

    @GetMapping("/orders/counts")
    Map<String, Long> orderCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        ORDER_GROUPS.forEach((group, statuses) -> counts.put(group, statuses.stream().mapToLong(orders::countByStatus).sum()));
        return counts;
    }

    @GetMapping("/orders/{id}")
    OrderDetail order(@PathVariable Long id) {
        Order o = orders.findWithItems(id).orElseThrow(NotFoundException::new);
        List<Option> transitions = Arrays.stream(OrderStatus.values()).filter(o.allowedTransitions()::contains)
                .map(s -> new Option(s.name(), s.label)).toList();
        List<OrderItemDto> items = o.getItems().stream().map(i -> {
            ProductImage img = i.getProduct() == null ? null : i.getProduct().getPrimaryImage();
            return new OrderItemDto(i.getProduct() == null ? null : i.getProduct().getId(), i.getTitleSnapshot(), i.getQuantity(),
                    i.getPriceLekSnapshot(), i.getCostLekSnapshot(),
                    img == null ? null : props.base() + ImageStorage.url(ImageStorage.Size.thumb, img.getFilename()));
        }).toList();
        int profit = o.getItems().stream().mapToInt(i -> (i.getPriceLekSnapshot() - i.getCostLekSnapshot()) * i.getQuantity()).sum();
        String whatsapp = Links.whatsappTo(o.getCustomerPhone(),
                "Përshëndetje " + o.getCustomerName() + ", ju shkruajmë nga PCMania për porosinë " + o.getOrderNumber() + ".");
        return new OrderDetail(o.getId(), o.getOrderNumber(), o.getStatus().name(), o.getStatus().label, transitions,
                o.getCustomerName(), o.getCustomerPhone(), o.getCustomerEmail(), o.getCity(), o.getAddress(),
                o.getCustomerNotes(), o.getAdminNotes(), o.getDeliveryMethod().label, o.getPaymentMethod().label,
                o.getSubtotalLek(), o.getShippingLek(), o.getTotalLek(), profit, items, whatsapp, o.getCreatedAt(), o.getDeliveredAt());
    }

    @PostMapping("/orders/{id}/status")
    OrderDetail changeOrderStatus(@PathVariable Long id, @RequestBody StatusChange body) {
        orderService.changeStatus(id, parse(OrderStatus.class, body.status()));
        return order(id);
    }

    @PutMapping("/orders/{id}/notes")
    OrderDetail orderNotes(@PathVariable Long id, @RequestBody NotesChange body) {
        orderService.updateNotes(id, body.notes());
        return order(id);
    }

    @GetMapping("/builds")
    PageDto<BuildRow> buildRequests(@RequestParam(defaultValue = "new") String group, @RequestParam(defaultValue = "0") int page) {
        List<BuildStatus> statuses = BUILD_GROUPS.getOrDefault(group, BUILD_GROUPS.get("new"));
        return PageDto.of(builds.findByStatusInOrderByCreatedAtDesc(statuses, PageRequest.of(Math.max(page, 0), 30)), BuildRow::of);
    }

    @GetMapping("/builds/counts")
    Map<String, Long> buildCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        BUILD_GROUPS.forEach((group, statuses) -> counts.put(group, statuses.stream().mapToLong(builds::countByStatus).sum()));
        return counts;
    }

    @GetMapping("/builds/{id}")
    BuildDetail buildRequest(@PathVariable Long id) {
        BuildRequest b = builds.findById(id).orElseThrow(NotFoundException::new);
        List<Option> statuses = Arrays.stream(BuildStatus.values()).map(s -> new Option(s.name(), s.label)).toList();
        String whatsapp = Links.whatsappTo(b.getCustomerPhone(),
                "Përshëndetje " + b.getCustomerName() + ", ju shkruajmë nga PCMania për kërkesën tuaj për një PC me porosi.");
        return new BuildDetail(b.getId(), b.getStatus().name(), b.getStatus().label, statuses, b.getCustomerName(),
                b.getCustomerPhone(), b.getBudgetLek(), b.getUseCase().label, b.getNotes(), b.getAdminNotes(),
                b.getQuotedTotalLek(), whatsapp, b.getCreatedAt());
    }

    @PatchMapping("/builds/{id}")
    BuildDetail updateBuildRequest(@PathVariable Long id, @RequestBody BuildPatch body) {
        BuildRequest b = builds.findById(id).orElseThrow(NotFoundException::new);
        BuildStatus status = body.status() == null ? b.getStatus() : parse(BuildStatus.class, body.status());
        Integer quoted = body.quotedTotalLek() == null ? b.getQuotedTotalLek() : body.quotedTotalLek();
        String notes = body.adminNotes() == null ? b.getAdminNotes() : body.adminNotes();
        buildService.update(id, status, quoted, notes);
        return buildRequest(id);
    }

    @GetMapping("/products")
    PageDto<ProductRow> products(@RequestParam(defaultValue = "active") String group, @RequestParam(required = false) String q,
                                 @RequestParam(defaultValue = "0") int page) {
        List<ProductStatus> statuses = PRODUCT_GROUPS.getOrDefault(group, PRODUCT_GROUPS.get("active"));
        Sort sort = group.equals("active") ? Sort.by("listedAt").ascending() : Sort.by("createdAt").descending();
        return PageDto.of(productService.search(q, statuses, null, PageRequest.of(Math.max(page, 0), 30, sort)),
                p -> ProductRow.of(p, props.base()));
    }

    @GetMapping("/products/counts")
    Map<String, Long> productCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        PRODUCT_GROUPS.forEach((group, statuses) ->
                counts.put(group, productService.search(null, statuses, null, PageRequest.of(0, 1)).getTotalElements()));
        return counts;
    }

    @GetMapping("/products/{id}")
    ProductRow product(@PathVariable Long id) {
        return ProductRow.of(productService.get(id), props.base());
    }

    @PatchMapping("/products/{id}")
    ProductRow updateProduct(@PathVariable Long id, @RequestBody ProductPatch body) {
        ProductStatus status = body.status() == null ? null : parse(ProductStatus.class, body.status());
        Condition condition = body.condition() == null ? null : parse(Condition.class, body.condition());
        productService.quickUpdate(id, status, body.quantity(), body.priceLek(), body.costLek(),
                body.title(), condition, body.shortDescription());
        return product(id);
    }

    @DeleteMapping("/products/{id}")
    ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        if (productService.delete(id)) return ResponseEntity.noContent().build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("has_orders",
                "Produkti ka porosi të lidhura dhe nuk mund të fshihet. Vendoseni \"I fshehur\"."));
    }

    @GetMapping("/upcoming")
    List<UpcomingRow> upcomingList() {
        List<UpcomingProduct> items = upcoming.all();
        Map<Long, Long> counts = upcoming.interestCounts(items);
        return items.stream().map(u -> UpcomingRow.of(u, props.base(), counts.getOrDefault(u.getId(), 0L))).toList();
    }

    @GetMapping("/upcoming/{id}")
    UpcomingRow upcomingOne(@PathVariable Long id) {
        return UpcomingRow.of(upcoming.get(id), props.base(), upcoming.interestCount(id));
    }

    @PostMapping("/upcoming")
    ResponseEntity<UpcomingRow> createUpcoming(@RequestBody UpcomingPatch body) {
        UpcomingProduct u = upcoming.save(null, body.title(), body.teaser(), body.categorySlug(), body.expectedPriceLek(),
                body.expectedLabel(),
                body.condition() == null ? null : parse(Condition.class, body.condition()),
                body.status() == null ? UpcomingStatus.VISIBLE : parse(UpcomingStatus.class, body.status()),
                body.sortOrder());
        return ResponseEntity.status(HttpStatus.CREATED).body(UpcomingRow.of(u, props.base(), 0));
    }

    @PatchMapping("/upcoming/{id}")
    UpcomingRow updateUpcoming(@PathVariable Long id, @RequestBody UpcomingPatch body) {
        UpcomingProduct existing = upcoming.get(id);
        upcoming.save(id,
                body.title() == null ? existing.getTitle() : body.title(),
                body.teaser() == null ? existing.getTeaser() : body.teaser(),
                body.categorySlug() == null ? existing.getCategorySlug() : body.categorySlug(),
                body.expectedPriceLek() == null ? existing.getExpectedPriceLek() : body.expectedPriceLek(),
                body.expectedLabel() == null ? existing.getExpectedLabel() : body.expectedLabel(),
                body.condition() == null ? existing.getCondition() : parse(Condition.class, body.condition()),
                body.status() == null ? existing.getStatus() : parse(UpcomingStatus.class, body.status()),
                body.sortOrder());
        return upcomingOne(id);
    }

    @DeleteMapping("/upcoming/{id}")
    ResponseEntity<Void> deleteUpcoming(@PathVariable Long id) {
        upcoming.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/upcoming/{id}/interest")
    List<InterestDto> upcomingInterest(@PathVariable Long id) {
        return upcoming.interestsFor(id).stream()
                .map(i -> new InterestDto(i.getId(), i.getCustomerName(), i.getCustomerPhone(), i.isNotified(),
                        Links.whatsappTo(i.getCustomerPhone(), "Përshëndetje " + i.getCustomerName()
                                + ", ju shkruajmë nga PCMania — artikulli që prisnit erdhi në stok."),
                        i.getCreatedAt()))
                .toList();
    }

    @PostMapping("/upcoming/interest/{interestId}/notified")
    ResponseEntity<Void> markInterestNotified(@PathVariable Long interestId) {
        upcoming.markNotified(interestId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/meta")
    Map<String, List<Option>> meta() {
        return Map.of(
                "productStatuses", Arrays.stream(ProductStatus.values()).map(s -> new Option(s.name(), s.label)).toList(),
                "conditions", Arrays.stream(Condition.values()).map(c -> new Option(c.name(), c.label)).toList(),
                "upcomingStatuses", Arrays.stream(UpcomingStatus.values()).map(s -> new Option(s.name(), s.label)).toList());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Status i pavlefshëm: " + value);
        }
    }
}
