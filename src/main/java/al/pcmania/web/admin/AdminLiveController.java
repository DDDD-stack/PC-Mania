package al.pcmania.web.admin;

import al.pcmania.domain.Enums.BuildStatus;
import al.pcmania.domain.Enums.OrderStatus;
import al.pcmania.domain.Enums.TradeStatus;
import al.pcmania.domain.Enums.WishStatus;
import al.pcmania.domain.Order;
import al.pcmania.repo.BuildRequestRepository;
import al.pcmania.repo.OrderRepository;
import al.pcmania.repo.TradeRequestRepository;
import al.pcmania.repo.WishRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Polled by the admin pages every 30 s to badge the navbar and announce new orders. Session-authenticated. */
@RestController
@RequiredArgsConstructor
public class AdminLiveController {

    private final OrderRepository orders;
    private final BuildRequestRepository builds;
    private final WishRequestRepository wishes;
    private final TradeRequestRepository trades;

    @GetMapping("/admin/live")
    Map<String, Object> live() {
        return Map.of(
                "newOrders", orders.countByStatus(OrderStatus.NEW),
                "newBuilds", builds.countByStatus(BuildStatus.NEW),
                "newWishes", wishes.countByStatus(WishStatus.NEW),
                "newTrades", trades.countByStatus(TradeStatus.NEW),
                "latestOrderId", orders.findTopByOrderByIdDesc().map(Order::getId).orElse(0L));
    }
}
