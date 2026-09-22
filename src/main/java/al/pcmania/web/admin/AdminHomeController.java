package al.pcmania.web.admin;

import al.pcmania.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AdminHomeController {

    private final DashboardService dashboard;

    @GetMapping("/admin")
    String dashboard(@RequestParam(defaultValue = "false") boolean allSales, Model model) {
        model.addAttribute("d", dashboard.build());
        model.addAttribute("allSales", allSales);
        return "admin/dashboard";
    }

    @GetMapping("/admin/login")
    String login() {
        return "admin/login";
    }
}
