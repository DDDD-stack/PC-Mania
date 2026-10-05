package al.pcmania.web.admin;

import al.pcmania.domain.Enums.DriverStatus;
import al.pcmania.domain.Enums.GpuVendor;
import al.pcmania.domain.Enums.MiningRisk;
import al.pcmania.domain.Enums.UpscalerVersion;
import al.pcmania.domain.GpuCatalog;
import al.pcmania.repo.ProductRepository;
import al.pcmania.service.GpuCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.stream.Collectors;

/** Admin > Katalogu GPU: the reference rows the autofill and the assistant work from. Plain; used rarely. */
@Controller
@RequestMapping("/admin/gpu-catalog")
@RequiredArgsConstructor
public class AdminGpuCatalogController {

    private final GpuCatalogService service;
    private final ProductRepository products;

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("vendors", GpuVendor.values());
        model.addAttribute("upscalers", UpscalerVersion.values());
        model.addAttribute("driverStatuses", DriverStatus.values());
        model.addAttribute("miningRisks", MiningRisk.values());
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("rows", service.all());
        Map<Long, Long> productCounts = products.countByGpuModel().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        model.addAttribute("productCounts", productCounts);
        return "admin/gpu-catalog/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        model.addAttribute("g", new GpuCatalog());
        return "admin/gpu-catalog/form";
    }

    @GetMapping("/{id}")
    String edit(@PathVariable Long id, Model model) {
        model.addAttribute("g", service.get(id));
        return "admin/gpu-catalog/form";
    }

    @PostMapping({"", "/{id}"})
    String save(@PathVariable(required = false) Long id, @ModelAttribute("g") GpuCatalog form, Model model, RedirectAttributes flash) {
        try {
            GpuCatalog saved = service.save(id, form);
            flash.addFlashAttribute("success", id == null ? "Rreshti u shtua." : "Ndryshimet u ruajtën.");
            return "redirect:/admin/gpu-catalog/" + saved.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            form.setId(id);
            return "admin/gpu-catalog/form";
        }
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes flash) {
        service.delete(id);
        flash.addFlashAttribute("success", "Rreshti u fshi. Produktet që tregonin tek ai mbetën pa model.");
        return "redirect:/admin/gpu-catalog";
    }
}
