package al.pcmania.web.admin;

import al.pcmania.domain.SpecCatalogEntry;
import al.pcmania.service.GpuCatalogService;
import al.pcmania.service.SpecCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api/spec-catalog")
@RequiredArgsConstructor
public class AdminSpecCatalogApiController {

    public record Hit(Long id, String name, boolean verified, String summary) {
        static Hit of(SpecCatalogEntry e) {
            String summary = String.join(" · ", SpecCatalogService.values(e).values().stream().limit(3).toList());
            return new Hit(e.getId(), e.getName(), e.isVerified(), summary);
        }
    }

    public record Detail(Long id, String name, String categorySlug, boolean verified, Long gpuCatalogId,
                         Map<String, String> values, String shortDescription) {}

    private final SpecCatalogService service;
    private final GpuCatalogService gpus;

    @GetMapping("/search")
    List<Hit> search(@RequestParam(name = "category", defaultValue = "") String category,
                     @RequestParam(name = "q", defaultValue = "") String q) {
        return service.search(category, q, 8).stream().map(m -> Hit.of(m.entry())).toList();
    }

    @GetMapping("/{id}")
    Detail detail(@PathVariable Long id) {
        SpecCatalogEntry e = service.get(id);
        String shortDescription = e.getGpuCatalogId() == null ? null
                : gpus.find(e.getGpuCatalogId()).map(GpuCatalogService::shortDescriptionSq).orElse(null);
        return new Detail(e.getId(), e.getName(), e.getCategorySlug(), e.isVerified(), e.getGpuCatalogId(),
                SpecCatalogService.values(e), shortDescription);
    }
}
