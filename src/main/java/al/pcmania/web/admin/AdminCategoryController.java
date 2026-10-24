package al.pcmania.web.admin;

import al.pcmania.domain.Category;
import al.pcmania.service.CategoryAdminService;
import al.pcmania.service.MetaText;
import al.pcmania.service.SeoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryAdminService service;
    private final SeoService seo;

    @GetMapping
    String list(Model model) {
        model.addAttribute("categories", service.all());
        model.addAttribute("counts", service.counts());
        return "admin/categories/list";
    }

    @PostMapping
    String create(@RequestParam String nameSq, @RequestParam(required = false) String slug,
                  @RequestParam(required = false) String iconClass, @RequestParam(required = false) Integer sortOrder,
                  RedirectAttributes flash) {
        if (nameSq == null || nameSq.isBlank()) {
            flash.addFlashAttribute("error", "Shkruani emrin e kategorisë.");
            return "redirect:/admin/categories";
        }
        Category c = service.create(nameSq, slug, iconClass, sortOrder);
        flash.addFlashAttribute("success", "Kategoria \"" + c.getNameSq() + "\" u shtua (/kategori/" + c.getSlug() + ").");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}")
    String update(@PathVariable Long id, @RequestParam String nameSq, @RequestParam(required = false) String iconClass,
                  @RequestParam(required = false) Integer sortOrder, RedirectAttributes flash) {
        service.update(id, nameSq, iconClass, sortOrder);
        flash.addFlashAttribute("success", "Kategoria u përditësua.");
        return "redirect:/admin/categories";
    }

    @GetMapping("/{id}/seo")
    String seoForm(@PathVariable Long id, Model model) {
        Category c = service.get(id);
        model.addAttribute("category", c);
        model.addAttribute("generatedTitle", seo.categoryTitle(withoutOverrides(c)));
        model.addAttribute("generatedDescription", seo.categoryDescription(withoutOverrides(c)));
        model.addAttribute("titleMax", MetaText.TITLE_MAX);
        model.addAttribute("descriptionMax", MetaText.DESCRIPTION_MAX);
        return "admin/categories/seo";
    }

    @PostMapping("/{id}/seo")
    String seoSave(@PathVariable Long id, @RequestParam(required = false) String metaTitle,
                   @RequestParam(required = false) String metaDescription, @RequestParam(required = false) String introHtml,
                   RedirectAttributes flash) {
        if (metaTitle != null && metaTitle.trim().length() > 120) {
            flash.addFlashAttribute("error", "Titulli është shumë i gjatë (maks. 120 shkronja).");
            return "redirect:/admin/categories/" + id + "/seo";
        }
        if (metaDescription != null && metaDescription.trim().length() > 300) {
            flash.addFlashAttribute("error", "Përshkrimi është shumë i gjatë (maks. 300 shkronja).");
            return "redirect:/admin/categories/" + id + "/seo";
        }
        Category c = service.updateSeo(id, metaTitle, metaDescription, introHtml);
        flash.addFlashAttribute("success", "Teksti për Google i \"" + c.getNameSq() + "\" u ruajt.");
        return "redirect:/admin/categories/" + id + "/seo";
    }

    private static Category withoutOverrides(Category c) {
        Category copy = new Category();
        copy.setNameSq(c.getNameSq());
        copy.setSlug(c.getSlug());
        return copy;
    }

    @PostMapping("/{id}/visible")
    String visible(@PathVariable Long id, @RequestParam boolean value, RedirectAttributes flash) {
        Category c = service.setVisible(id, value);
        flash.addFlashAttribute("success", value
                ? "\"" + c.getNameSq() + "\" shfaqet tani në faqe."
                : "\"" + c.getNameSq() + "\" u fsheh nga faqja.");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/out-of-stock")
    String outOfStock(@PathVariable Long id, @RequestParam boolean value, RedirectAttributes flash) {
        Category c = service.setOutOfStock(id, value);
        flash.addFlashAttribute("success", value
                ? "\"" + c.getNameSq() + "\" u shënua si pa stok."
                : "Shenja \"pa stok\" u hoq nga \"" + c.getNameSq() + "\".");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes flash) {
        if (service.delete(id)) flash.addFlashAttribute("success", "Kategoria u fshi.");
        else flash.addFlashAttribute("error", "Kategoria ka produkte dhe nuk mund të fshihet. Fshihni ose zhvendosni produktet, ose thjesht fshiheni kategorinë.");
        return "redirect:/admin/categories";
    }
}
