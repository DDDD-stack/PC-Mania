package al.pcmania.web.admin;

import al.pcmania.domain.SpecCatalogEntry;
import al.pcmania.service.FieldTemplateService;
import al.pcmania.service.SpecCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/admin/spec-catalog")
@RequiredArgsConstructor
public class AdminSpecCatalogController {

    private final SpecCatalogService service;
    private final FieldTemplateService templates;

    public record CatalogKind(String key, String label, FieldTemplateService.Template template) {}

    @ModelAttribute("kinds")
    List<CatalogKind> kinds() {
        Map<String, CatalogKind> out = new LinkedHashMap<>();
        for (FieldTemplateService.Template t : templates.all().values()) {
            if (t.prebuilt()) continue;
            out.putIfAbsent(t.catalogKey(), new CatalogKind(t.catalogKey(), t.categoryName(), t));
        }
        return new ArrayList<>(out.values());
    }

    @GetMapping
    String list(@RequestParam(required = false) String category, Model model) {
        List<SpecCatalogEntry> rows = service.all();
        if (StringUtils.hasText(category)) rows = rows.stream().filter(e -> e.getCategorySlug().equals(category)).toList();
        model.addAttribute("rows", rows);
        model.addAttribute("category", category);
        model.addAttribute("counts", service.counts());
        return "admin/spec-catalog/list";
    }

    @GetMapping("/new")
    String create(@RequestParam(required = false) String category, Model model) {
        SpecCatalogEntry e = new SpecCatalogEntry();
        e.setCategorySlug(StringUtils.hasText(category) ? category : SpecCatalogService.GPU);
        return form(e, model);
    }

    @GetMapping("/{id}")
    String edit(@PathVariable Long id, Model model) {
        return form(service.get(id), model);
    }

    @PostMapping({"", "/{id}"})
    String save(@PathVariable(required = false) Long id, @RequestParam String categorySlug, @RequestParam String name,
                @RequestParam(required = false) String aliases, @RequestParam(defaultValue = "false") boolean verified,
                @RequestParam Map<String, String> params, Model model, RedirectAttributes flash) {
        Map<String, String> values = new LinkedHashMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("v.")) values.put(k.substring(2), v);
        });
        try {
            SpecCatalogEntry saved = service.save(id, new SpecCatalogService.EntryForm(categorySlug, name, aliases, verified, values));
            flash.addFlashAttribute("success", id == null ? "Rreshti u shtua në katalog." : "Ndryshimet u ruajtën.");
            return "redirect:/admin/spec-catalog/" + saved.getId();
        } catch (IllegalArgumentException e) {
            SpecCatalogEntry draft = id == null ? new SpecCatalogEntry() : service.get(id);
            draft.setCategorySlug(categorySlug);
            draft.setName(name);
            draft.setAliases(aliases);
            draft.setVerified(verified);
            draft.setSpecJson(new LinkedHashMap<>(values));
            model.addAttribute("error", e.getMessage());
            return form(draft, model);
        }
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes flash) {
        try {
            service.delete(id);
            flash.addFlashAttribute("success", "Rreshti u fshi nga katalogu.");
            return "redirect:/admin/spec-catalog";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/spec-catalog/" + id;
        }
    }

    private String form(SpecCatalogEntry e, Model model) {
        model.addAttribute("e", e);
        model.addAttribute("values", SpecCatalogService.values(e));
        model.addAttribute("fields", kinds().stream().filter(k -> k.key().equals(e.getCategorySlug())).findFirst()
                .map(k -> k.template().fields()).orElse(List.of()));
        return "admin/spec-catalog/form";
    }
}
