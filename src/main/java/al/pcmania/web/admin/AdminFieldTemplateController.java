package al.pcmania.web.admin;

import al.pcmania.domain.Category;
import al.pcmania.domain.Enums.FieldType;
import al.pcmania.service.CategoryAdminService;
import al.pcmania.service.FieldTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/categories/{categoryId}/fields")
@RequiredArgsConstructor
public class AdminFieldTemplateController {

    private final FieldTemplateService templates;
    private final CategoryAdminService categories;

    @GetMapping
    String list(@PathVariable Long categoryId, Model model) {
        Category c = categories.get(categoryId);
        model.addAttribute("category", c);
        model.addAttribute("fields", templates.rows(categoryId));
        model.addAttribute("types", FieldType.values());
        return "admin/categories/fields";
    }

    @PostMapping
    String add(@PathVariable Long categoryId, FieldTemplateService.FieldForm form, RedirectAttributes flash) {
        try {
            templates.add(categoryId, form);
            flash.addFlashAttribute("success", "Fusha \"" + form.labelSq() + "\" u shtua.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categories/" + categoryId + "/fields";
    }

    @PostMapping("/{id}")
    String update(@PathVariable Long categoryId, @PathVariable Long id, FieldTemplateService.FieldForm form, RedirectAttributes flash) {
        try {
            templates.update(id, form);
            flash.addFlashAttribute("success", "Fusha u ruajt.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categories/" + categoryId + "/fields";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long categoryId, @PathVariable Long id, RedirectAttributes flash) {
        templates.delete(id);
        flash.addFlashAttribute("success", "Fusha u hoq. Vlerat e ruajtura te produktet mbeten si specifikime të lira.");
        return "redirect:/admin/categories/" + categoryId + "/fields";
    }
}
