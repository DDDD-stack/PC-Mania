package al.pcmania.web.admin;

import al.pcmania.domain.Enums.TradeStatus;
import al.pcmania.domain.Enums.WishStatus;
import al.pcmania.repo.TradeRequestRepository;
import al.pcmania.repo.WishRequestRepository;
import al.pcmania.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;

@Controller
@RequiredArgsConstructor
public class AdminHomeController {

    private final DashboardService dashboard;
    private final WishRequestRepository wishes;
    private final TradeRequestRepository trades;

    @GetMapping("/admin")
    String dashboard(@RequestParam(defaultValue = "false") boolean allSales, Model model) {
        model.addAttribute("d", dashboard.build());
        model.addAttribute("allSales", allSales);
        model.addAttribute("newWishes", wishes.countByStatus(WishStatus.NEW));
        model.addAttribute("openTrades", trades.countByStatusIn(
                Arrays.stream(TradeStatus.values()).filter(TradeStatus::isOpen).toList()));
        // Converted trade-ins whose item has not been listed yet: stock that is on its way in.
        model.addAttribute("incomingTradeIns", trades.findByStatusAndStockProductIdIsNullOrderByClosedAtAsc(TradeStatus.CONVERTED));
        return "admin/dashboard";
    }

    @GetMapping("/admin/login")
    String login() {
        return "admin/login";
    }
}
