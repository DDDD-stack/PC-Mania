package al.pcmania.web.admin;

import al.pcmania.domain.Combo;
import al.pcmania.domain.ComboItem;
import al.pcmania.service.ComboService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin/combos")
@RequiredArgsConstructor
public class AdminComboController {

    private final ComboService service;

    @GetMapping
    String list(Model model) {
        model.addAttribute("rows", service.adminRows());
        return "admin/combos/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        return form(null, new ComboService.ComboForm(null, null, null, null, true, 0, null, null,
                List.of(new ComboService.Line(null, 1), new ComboService.Line(null, 1))), model);
    }

    @GetMapping("/{id}")
    String edit(@PathVariable Long id, Model model) {
        Combo c = service.get(id);
        List<ComboService.Line> lines = new ArrayList<>();
        for (ComboItem i : c.getItems()) lines.add(new ComboService.Line(i.getProduct().getId(), i.getQuantity()));
        model.addAttribute("reasons", service.reasons(c));
        model.addAttribute("visible", service.isVisible(id));
        return form(c, new ComboService.ComboForm(c.getTitleSq(), c.getSubtitleSq(), c.getSlug(), c.getComboPriceLek(),
                c.isActive(), c.getSortOrder(), c.getStartsAt(), c.getEndsAt(), lines), model);
    }

    @PostMapping({"", "/{id}"})
    String save(@PathVariable(required = false) Long id, @RequestParam String titleSq,
                @RequestParam(required = false) String subtitleSq, @RequestParam(required = false) String slug,
                @RequestParam(required = false) Integer comboPriceLek, @RequestParam(defaultValue = "false") boolean active,
                @RequestParam(required = false) Integer sortOrder,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt,
                @RequestParam(name = "productId", required = false) List<Long> productIds,
                @RequestParam(name = "quantity", required = false) List<Integer> quantities,
                Model model, RedirectAttributes flash) {
        List<ComboService.Line> lines = new ArrayList<>();
        if (productIds != null) {
            for (int i = 0; i < productIds.size(); i++) {
                Integer q = quantities != null && i < quantities.size() ? quantities.get(i) : null;
                lines.add(new ComboService.Line(productIds.get(i), q == null ? 1 : q));
            }
        }
        ComboService.ComboForm form = new ComboService.ComboForm(titleSq, subtitleSq, slug, comboPriceLek, active, sortOrder,
                startsAt, endsAt, lines);
        try {
            Combo saved = service.save(id, form);
            flash.addFlashAttribute("success", id == null ? "Paketa u krijua." : "Paketa u ruajt.");
            return "redirect:/admin/combos/" + saved.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return form(id == null ? null : service.get(id), form, model);
        }
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes flash) {
        service.delete(id);
        flash.addFlashAttribute("success", "Paketa u fshi. Porositë e vjetra mbeten siç ishin.");
        return "redirect:/admin/combos";
    }

    private String form(Combo combo, ComboService.ComboForm form, Model model) {
        model.addAttribute("combo", combo);
        model.addAttribute("form", form);
        model.addAttribute("products", service.pickableProducts(combo));
        return "admin/combos/form";
    }
}
