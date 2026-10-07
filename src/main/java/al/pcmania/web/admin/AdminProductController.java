package al.pcmania.web.admin;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;
import al.pcmania.repo.BrandRepository;
import al.pcmania.repo.CategoryRepository;
import al.pcmania.repo.ProductSpecRepository;
import al.pcmania.service.GpuCatalogService;
import al.pcmania.service.ProductAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.util.List;

@Controller
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    static final String GPU_CATEGORY = "karta-grafike";

    private final ProductAdminService service;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final ProductSpecRepository specs;
    private final GpuCatalogService gpuCatalog;

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("categories", categories.findAllByOrderBySortOrderAsc());
        model.addAttribute("statuses", ProductStatus.values());
    }

    @GetMapping
    String list(@RequestParam(required = false) String q, @RequestParam(required = false) ProductStatus status,
                @RequestParam(required = false) String category, @RequestParam(defaultValue = "0") int page, Model model) {
        var pageable = PageRequest.of(Math.max(page, 0), 30, Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("page", service.search(q, status, category, pageable));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("category", category);
        return "admin/products/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        return form(null, new ProductForm(), model);
    }

    @GetMapping("/{id}")
    String edit(@PathVariable Long id, Model model) {
        Product p = service.get(id);
        return form(p, ProductForm.from(p), model);
    }

    @PostMapping({"", "/{id}"})
    String save(@PathVariable(required = false) Long id, @Valid @ModelAttribute("form") ProductForm form,
                BindingResult errors, @RequestParam(name = "newImages", required = false) List<MultipartFile> newImages,
                Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) return form(id == null ? null : service.get(id), form, model);
        Product p = service.save(id, form);

        List<MultipartFile> uploads = newImages == null ? List.of() : newImages.stream().filter(f -> !f.isEmpty()).toList();
        String imageNote = "";
        if (!uploads.isEmpty()) {
            try {
                service.addImages(p.getId(), uploads);
                imageNote = " " + uploads.size() + (uploads.size() == 1 ? " foto u ngarkua." : " foto u ngarkuan.");
            } catch (IllegalArgumentException e) {
                flash.addFlashAttribute("error", "Produkti u ruajt, por fotot jo: " + e.getMessage());
            }
        }
        flash.addFlashAttribute("success", (id == null ? "Produkti u krijua." : "Ndryshimet u ruajtën.") + imageNote);

        if (p.getGpuModel() == null && GPU_CATEGORY.equals(p.getCategorySlug())) {
            flash.addFlashAttribute("warning", "Pa model nga katalogu, asistenti nuk do ta rekomandojë këtë produkt.");
        }
        return "redirect:/admin/products/" + p.getId();
    }

    @PostMapping("/{id}/duplicate")
    String duplicate(@PathVariable Long id, RedirectAttributes flash) {
        Product copy = service.duplicate(id);
        flash.addFlashAttribute("success", "Kopja u krijua si Draft. Kontrolloni të dhënat dhe aktivizojeni.");
        return "redirect:/admin/products/" + copy.getId();
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes flash) {
        if (service.delete(id)) {
            flash.addFlashAttribute("success", "Produkti u fshi.");
            return "redirect:/admin/products";
        }
        flash.addFlashAttribute("error", "Produkti ka porosi të lidhura dhe nuk mund të fshihet. Vendoseni statusin \"I fshehur\".");
        return "redirect:/admin/products/" + id;
    }

    @PostMapping("/bulk-status")
    String bulkStatus(@RequestParam(name = "ids", required = false) List<Long> ids, @RequestParam ProductStatus status,
                      @RequestHeader(value = "Referer", required = false) String referer, RedirectAttributes flash) {
        int n = ids == null ? 0 : service.bulkStatus(ids, status);
        flash.addFlashAttribute("success", n + " produkte u ndryshuan në \"" + status.label + "\".");
        return "redirect:" + localPath(referer, "/admin/products");
    }

    static String localPath(String referer, String fallback) {
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath();
            if (path != null && path.startsWith(fallback)) return path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    @PostMapping("/{id}/images")
    String uploadImages(@PathVariable Long id, @RequestParam("files") List<MultipartFile> files, Model model) {
        try {
            service.addImages(id, files);
        } catch (IllegalArgumentException e) {
            model.addAttribute("imageError", e.getMessage());
        }
        return imagesFragment(id, model);
    }

    @PostMapping("/{id}/images/order")
    ResponseEntity<Void> reorder(@PathVariable Long id, @RequestParam("ids") List<Long> ids) {
        service.reorderImages(id, ids);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/images/{imageId}/delete")
    String deleteImage(@PathVariable Long id, @PathVariable Long imageId, Model model) {
        service.deleteImage(id, imageId);
        return imagesFragment(id, model);
    }

    private String imagesFragment(Long id, Model model) {
        model.addAttribute("product", service.get(id));
        return "admin/products/form :: images";
    }

    private String form(Product product, ProductForm form, Model model) {
        model.addAttribute("product", product);
        model.addAttribute("form", form);
        model.addAttribute("brands", brands.findAllByOrderByNameAsc());
        model.addAttribute("conditions", Condition.values());
        String cat = form.getCategorySlug() != null ? form.getCategorySlug() : GPU_CATEGORY;
        model.addAttribute("specKeySuggestions", specs.keysInCategory(cat));
        model.addAttribute("gpuModel", gpuCatalog.find(form.getGpuModelId()).orElse(null));
        return "admin/products/form";
    }
}
