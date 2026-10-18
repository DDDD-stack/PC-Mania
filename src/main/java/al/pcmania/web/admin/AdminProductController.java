package al.pcmania.web.admin;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;
import al.pcmania.domain.SpecCatalogEntry;
import al.pcmania.repo.BrandRepository;
import al.pcmania.repo.CategoryRepository;
import al.pcmania.repo.ProductSpecRepository;
import al.pcmania.service.FieldTemplateService;
import al.pcmania.service.GpuCatalogService;
import al.pcmania.service.ProductAdminService;
import al.pcmania.service.SpecCatalogService;
import al.pcmania.web.Fmt;
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
import java.util.*;

@Controller
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    static final String GPU_CATEGORY = "karta-grafike";
    static final List<ProductStatus> QUICK_STATUSES = List.of(ProductStatus.ACTIVE, ProductStatus.RESERVED, ProductStatus.SOLD);

    private final ProductAdminService service;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final ProductSpecRepository specs;
    private final GpuCatalogService gpuCatalog;
    private final FieldTemplateService templates;
    private final SpecCatalogService specCatalog;

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("categories", categories.findAllByOrderBySortOrderAsc());
        model.addAttribute("statuses", ProductStatus.values());
        model.addAttribute("quickStatuses", QUICK_STATUSES);
    }

    @GetMapping
    String list(@RequestParam(required = false) String q, @RequestParam(required = false) ProductStatus status,
                @RequestParam(required = false) String category, @RequestParam(defaultValue = "false") boolean all,
                @RequestParam(defaultValue = "0") int page, Model model) {
        var pageable = PageRequest.of(Math.max(page, 0), 30, Sort.by(Sort.Direction.DESC, "createdAt"));
        Collection<ProductStatus> statuses = status != null ? List.of(status)
                : all ? null : EnumSet.complementOf(EnumSet.of(ProductStatus.SOLD));
        model.addAttribute("page", service.search(q, statuses, category, pageable));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("category", category);
        model.addAttribute("all", all);
        return "admin/products/list";
    }

    @PostMapping(value = "/{id}/quick-status", produces = "application/json")
    ResponseEntity<Map<String, Object>> quickStatusJson(@PathVariable Long id, @RequestParam("to") ProductStatus to) {
        Product p = service.quickUpdate(id, to, null, null, null, null, null, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", p.getId());
        body.put("status", p.getStatus().name());
        body.put("label", p.getStatus().label);
        body.put("soldAt", p.getSoldAt() == null ? null : new Fmt().dateTime(p.getSoldAt()));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/quick-status")
    String quickStatus(@PathVariable Long id, @RequestParam("to") ProductStatus to,
                       @RequestHeader(value = "Referer", required = false) String referer, RedirectAttributes flash) {
        Product p = service.quickUpdate(id, to, null, null, null, null, null, null);
        flash.addFlashAttribute("success", "\"" + p.getTitle() + "\" → " + p.getStatus().label + ".");
        return "redirect:" + localPath(referer, "/admin/products");
    }

    @GetMapping("/new")
    String create(Model model) {
        return form(null, new ProductForm(), model);
    }

    @GetMapping("/{id}")
    String edit(@PathVariable Long id, Model model) {
        Product p = service.get(id);
        ProductForm f = ProductForm.from(p);
        service.prebuilt(id).ifPresent(spec -> f.setPrebuilt(ProductForm.PrebuiltForm.from(spec)));
        return form(p, f, model);
    }

    @PostMapping({"", "/{id}"})
    String save(@PathVariable(required = false) Long id, @Valid @ModelAttribute("form") ProductForm form,
                BindingResult errors, @RequestParam(name = "newImages", required = false) List<MultipartFile> newImages,
                Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) return form(id == null ? null : service.get(id), form, model);
        Product p;
        try {
            p = service.save(id, form);
        } catch (ProductAdminService.InvalidProduct e) {
            model.addAttribute("specErrors", e.errors);
            return form(id == null ? null : service.get(id), form, model);
        }

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

    @PostMapping("/{id}/catalog-entry")
    String saveAsCatalogEntry(@PathVariable Long id, RedirectAttributes flash) {
        Product p = service.get(id);
        try {
            SpecCatalogEntry e = specCatalog.fromProduct(p, templates.forCategory(p.getCategorySlug()).catalogKey());
            flash.addFlashAttribute("success", "U ruajt në katalog si \"" + e.getName() + "\". Kontrollojeni dhe shënojeni të verifikuar.");
            return "redirect:/admin/spec-catalog/" + e.getId();
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/products/" + id;
        }
    }

    @PostMapping("/{id}/duplicate")
    String duplicate(@PathVariable Long id, RedirectAttributes flash) {
        Product copy = service.duplicate(id);
        flash.addFlashAttribute("success", "Kopja u krijua si Draft. Kontrolloni të dhënat dhe aktivizojeni.");
        return "redirect:/admin/products/" + copy.getId();
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes flash) {
        Optional<String> blocker = service.deleteBlocker(id);
        if (blocker.isEmpty() && service.delete(id)) {
            flash.addFlashAttribute("success", "Produkti u fshi.");
            return "redirect:/admin/products";
        }
        flash.addFlashAttribute("error", blocker.orElse("Produkti nuk mund të fshihet."));
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

    public record SpecRow(String key, String value) {}

    private String form(Product product, ProductForm form, Model model) {
        model.addAttribute("product", product);
        model.addAttribute("form", form);
        model.addAttribute("brands", brands.findAllByOrderByNameAsc());
        model.addAttribute("conditions", Condition.values());
        String cat = form.getCategorySlug() != null ? form.getCategorySlug() : GPU_CATEGORY;
        FieldTemplateService.Template template = templates.forCategory(cat);
        Map<String, String> templateValues = new LinkedHashMap<>();
        List<SpecRow> extraSpecs = new ArrayList<>();
        for (int i = 0; i < form.getSpecKeys().size(); i++) {
            String key = form.getSpecKeys().get(i);
            String value = i < form.getSpecValues().size() ? form.getSpecValues().get(i) : "";
            if (key == null || key.isBlank()) continue;
            if (template.field(key).isPresent()) templateValues.put(key, value);
            else extraSpecs.add(new SpecRow(key, value));
        }
        model.addAttribute("template", template);
        model.addAttribute("templateValues", templateValues);
        model.addAttribute("extraSpecs", extraSpecs);
        model.addAttribute("templatesJson", templates.json());
        model.addAttribute("specKeySuggestions", specs.keysInCategory(cat));
        model.addAttribute("gpuModel", gpuCatalog.find(form.getGpuModelId()).orElse(null));
        if (form.getPrebuilt() != null && form.getPrebuilt().getGpuCatalogId() != null && form.getPrebuilt().getGpuCatalogName() == null) {
            specCatalog.find(form.getPrebuilt().getGpuCatalogId()).ifPresent(e -> form.getPrebuilt().setGpuCatalogName(e.getName()));
        }
        return "admin/products/form";
    }
}
