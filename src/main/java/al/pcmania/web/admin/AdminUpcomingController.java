package al.pcmania.web.admin;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.UpcomingStatus;
import al.pcmania.domain.UpcomingProduct;
import al.pcmania.repo.CategoryRepository;
import al.pcmania.service.UpcomingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/upcoming")
@RequiredArgsConstructor
public class AdminUpcomingController {

    private final UpcomingService service;
    private final CategoryRepository categories;

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("categories", categories.findAllByOrderBySortOrderAsc());
        model.addAttribute("conditions", Condition.values());
        model.addAttribute("statuses", UpcomingStatus.values());
    }

    @GetMapping
    String list(Model model) {
        var items = service.all();
        model.addAttribute("items", items);
        model.addAttribute("interest", service.interestCounts(items));
        return "admin/upcoming/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        model.addAttribute("u", new UpcomingProduct());
        return "admin/upcoming/form";
    }

    @GetMapping("/{id}")
    String edit(@PathVariable Long id, Model model) {
        model.addAttribute("u", service.get(id));
        model.addAttribute("interests", service.interestsFor(id));
        return "admin/upcoming/form";
    }

    @PostMapping({"", "/{id}"})
    String save(@PathVariable(required = false) Long id,
                @RequestParam String title,
                @RequestParam(required = false) String teaser,
                @RequestParam(required = false) String categorySlug,
                @RequestParam(required = false) Integer expectedPriceLek,
                @RequestParam(required = false) String expectedLabel,
                @RequestParam(required = false) Condition condition,
                @RequestParam UpcomingStatus status,
                @RequestParam(required = false) Integer sortOrder,
                @RequestParam(required = false) MultipartFile image,
                RedirectAttributes flash) {
        try {
            UpcomingProduct u = service.save(id, title, teaser, categorySlug, expectedPriceLek, expectedLabel,
                    condition, status, sortOrder);
            if (image != null && !image.isEmpty()) service.setImage(u.getId(), image);
            flash.addFlashAttribute("success", id == null ? "U shtua te \"Së shpejti\"." : "Ndryshimet u ruajtën.");
            return "redirect:/admin/upcoming/" + u.getId();
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/upcoming" + (id == null ? "/new" : "/" + id);
        }
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes flash) {
        service.delete(id);
        flash.addFlashAttribute("success", "U fshi.");
        return "redirect:/admin/upcoming";
    }

    @PostMapping("/interest/{interestId}/notified")
    String markNotified(@PathVariable Long interestId, @RequestParam Long upcomingId, RedirectAttributes flash) {
        service.markNotified(interestId);
        flash.addFlashAttribute("success", "U shënua si i njoftuar.");
        return "redirect:/admin/upcoming/" + upcomingId;
    }
}
