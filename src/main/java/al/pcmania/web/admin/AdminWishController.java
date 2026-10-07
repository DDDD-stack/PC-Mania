package al.pcmania.web.admin;

import al.pcmania.domain.Enums.WishStatus;
import al.pcmania.domain.WishRequest;
import al.pcmania.repo.WishRequestRepository;
import al.pcmania.service.NotFoundException;
import al.pcmania.service.WishService;
import al.pcmania.web.Links;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/wishes")
@RequiredArgsConstructor
public class AdminWishController {

    private final WishRequestRepository repo;
    private final WishService service;

    @GetMapping
    String list(@RequestParam(required = false) WishStatus status, @RequestParam(defaultValue = "0") int page, Model model) {
        var pageable = PageRequest.of(Math.max(page, 0), 30);
        Map<WishStatus, Long> counts = new EnumMap<>(WishStatus.class);
        for (WishStatus s : WishStatus.values()) counts.put(s, repo.countByStatus(s));
        model.addAttribute("page", status == null ? repo.findAllByOrderByCreatedAtDesc(pageable) : repo.findByStatusOrderByCreatedAtDesc(status, pageable));
        model.addAttribute("status", status);
        model.addAttribute("counts", counts);
        model.addAttribute("statuses", WishStatus.values());
        return "admin/wishes/list";
    }

    @GetMapping("/{id}")
    String detail(@PathVariable Long id, Model model) {
        WishRequest w = repo.findById(id).orElseThrow(NotFoundException::new);
        model.addAttribute("w", w);
        model.addAttribute("statuses", WishStatus.values());
        model.addAttribute("customerWhatsapp", Links.whatsappTo(w.getCustomerPhone(),
                "Përshëndetje " + w.getCustomerName() + ", ju shkruajmë nga PCMania për kërkesën tuaj: " + w.getItem() + "."));
        return "admin/wishes/detail";
    }

    @PostMapping("/{id}")
    String update(@PathVariable Long id, @RequestParam WishStatus status, @RequestParam(required = false) String adminNotes,
                  RedirectAttributes flash) {
        service.update(id, status, adminNotes);
        flash.addFlashAttribute("success", "Kërkesa u përditësua.");
        return "redirect:/admin/wishes/" + id;
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes flash) {
        service.delete(id);
        flash.addFlashAttribute("success", "Kërkesa u fshi.");
        return "redirect:/admin/wishes";
    }
}
