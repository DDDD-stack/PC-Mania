package al.pcmania.web.admin;

import al.pcmania.domain.Enums.DiscountType;
import al.pcmania.domain.Enums.PromoTheme;
import al.pcmania.domain.Product;
import al.pcmania.domain.Promotion;
import al.pcmania.repo.CategoryRepository;
import al.pcmania.service.ComboService;
import al.pcmania.service.PromotionService;
import al.pcmania.service.PromotionService.Line;
import al.pcmania.service.PromotionService.PromotionForm;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/admin/promotions")
@RequiredArgsConstructor
public class AdminPromotionController {

    private final PromotionService service;
    private final ComboService combos;
    private final CategoryRepository categories;

    @GetMapping
    String hub(Model model) {
        List<PromotionService.Row> rows = service.rows();
        List<ComboService.AdminRow> comboRows = combos.adminRows();
        model.addAttribute("rows", rows);
        model.addAttribute("stats", service.stats(rows));
        model.addAttribute("combos", comboRows);
        model.addAttribute("visibleCombos", comboRows.stream().filter(ComboService.AdminRow::visible).count());
        return "admin/promotions/list";
    }

    @GetMapping("/new")
    String create(@RequestParam(name = "product", required = false) Long productId, Model model) {
        LocalDateTime start = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(7).withHour(23).withMinute(59);
        List<Line> lines = productId == null ? List.of() : List.of(new Line(productId, null));
        return form(null, new PromotionForm("", null, DiscountType.PERCENT, 10, start, end, PromoTheme.FIRE, true, true, lines), model);
    }

    @GetMapping("/{id}")
    String edit(@PathVariable Long id, Model model) {
        Promotion p = service.get(id);
        return form(p, service.formOf(p), model);
    }

    @PostMapping({"", "/{id}"})
    String save(@PathVariable(required = false) Long id, @RequestParam(required = false) String name,
                @RequestParam(required = false) String headline,
                @RequestParam(defaultValue = "PERCENT") DiscountType discountType,
                @RequestParam(required = false) Integer discountValue,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt,
                @RequestParam(defaultValue = "FIRE") PromoTheme theme,
                @RequestParam(defaultValue = "false") boolean showBanner,
                @RequestParam(defaultValue = "false") boolean active,
                @RequestParam(name = "productId", required = false) List<Long> productIds,
                @RequestParam Map<String, String> params,
                Model model, RedirectAttributes flash) {
        List<Line> lines = new ArrayList<>();
        for (Long productId : productIds == null ? List.<Long>of() : new LinkedHashSet<>(productIds)) {
            lines.add(new Line(productId, parseLek(params.get("fixed_" + productId))));
        }
        PromotionForm form = new PromotionForm(name, headline, discountType, discountValue, startsAt, endsAt, theme,
                showBanner, active, lines);
        try {
            PromotionService.Saved saved = service.save(id, form);
            flash.addFlashAttribute("success", id == null ? "Oferta u krijua." : "Oferta u ruajt.");
            if (!saved.warnings().isEmpty()) flash.addFlashAttribute("warning", "Kujdes: " + String.join(" ", saved.warnings()));
            return "redirect:/admin/promotions/" + saved.promotion().getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return form(id == null ? null : service.get(id), form, model);
        }
    }

    @PostMapping("/{id}/toggle")
    String toggle(@PathVariable Long id, RedirectAttributes flash) {
        boolean nowActive = !service.get(id).isActive();
        service.setActive(id, nowActive);
        flash.addFlashAttribute("success", nowActive ? "Oferta u rifillua." : "Oferta u ndal. Çmimet u kthyen menjëherë.");
        return "redirect:/admin/promotions";
    }

    @PostMapping("/{id}/end")
    String end(@PathVariable Long id, RedirectAttributes flash) {
        service.endNow(id);
        flash.addFlashAttribute("success", "Oferta përfundoi. Çmimet u kthyen menjëherë.");
        return "redirect:/admin/promotions";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes flash) {
        service.delete(id);
        flash.addFlashAttribute("success", "Oferta u fshi. Porositë e vjetra mbeten me çmimin që u paguan.");
        return "redirect:/admin/promotions";
    }

    private String form(Promotion promotion, PromotionForm form, Model model) {
        List<Product> products = service.pickableProducts(promotion);
        Set<String> used = new HashSet<>();
        products.forEach(p -> used.add(p.getCategorySlug()));
        Map<String, String> categoryNames = new LinkedHashMap<>();
        categories.findAllByOrderBySortOrderAsc().stream().filter(c -> used.contains(c.getSlug()))
                .forEach(c -> categoryNames.put(c.getSlug(), c.getNameSq()));
        model.addAttribute("promotion", promotion);
        model.addAttribute("form", form);
        model.addAttribute("products", products);
        model.addAttribute("categoryNames", categoryNames);
        model.addAttribute("themes", PromoTheme.values());
        model.addAttribute("row", promotion == null ? null : service.row(promotion, LocalDateTime.now()));
        return "admin/promotions/form";
    }

    static Integer parseLek(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
