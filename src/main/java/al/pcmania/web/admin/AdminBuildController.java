package al.pcmania.web.admin;

import al.pcmania.domain.BuildRequest;
import al.pcmania.domain.Enums.BuildStatus;
import al.pcmania.repo.BuildRequestRepository;
import al.pcmania.service.BuildRequestService;
import al.pcmania.service.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/builds")
@RequiredArgsConstructor
public class AdminBuildController {

    private final BuildRequestRepository repo;
    private final BuildRequestService service;

    @GetMapping
    String list(@RequestParam(required = false) BuildStatus status, @RequestParam(defaultValue = "0") int page, Model model) {
        var pageable = PageRequest.of(Math.max(page, 0), 30);
        Map<BuildStatus, Long> counts = new EnumMap<>(BuildStatus.class);
        for (BuildStatus s : BuildStatus.values()) counts.put(s, repo.countByStatus(s));
        model.addAttribute("page", status == null ? repo.findAllByOrderByCreatedAtDesc(pageable) : repo.findByStatusOrderByCreatedAtDesc(status, pageable));
        model.addAttribute("status", status);
        model.addAttribute("counts", counts);
        model.addAttribute("statuses", BuildStatus.values());
        return "admin/builds/list";
    }

    @GetMapping("/{id}")
    String detail(@PathVariable Long id, Model model) {
        BuildRequest b = repo.findById(id).orElseThrow(NotFoundException::new);
        model.addAttribute("b", b);
        model.addAttribute("statuses", BuildStatus.values());
        String digits = b.getCustomerPhone().replaceAll("\\D", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        else if (digits.startsWith("0")) digits = "355" + digits.substring(1);
        model.addAttribute("customerWhatsapp", "https://wa.me/" + digits);
        return "admin/builds/detail";
    }

    @PostMapping("/{id}")
    String update(@PathVariable Long id, @RequestParam BuildStatus status, @RequestParam(required = false) Integer quotedTotalLek,
                  @RequestParam(required = false) String adminNotes, RedirectAttributes flash) {
        try {
            service.update(id, status, quotedTotalLek, adminNotes);
            flash.addFlashAttribute("success", "Kërkesa u përditësua.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/builds/" + id;
    }
}
