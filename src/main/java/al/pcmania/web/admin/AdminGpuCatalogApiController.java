package al.pcmania.web.admin;

import al.pcmania.domain.GpuCatalog;
import al.pcmania.service.GpuCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * JSON behind the product form's title combobox. Lives under /admin, so the admin session guards it
 * like every other admin page (the bearer-token chain only covers /api).
 */
@RestController
@RequestMapping("/admin/api/gpu-catalog")
@RequiredArgsConstructor
public class AdminGpuCatalogApiController {

    public record Hit(Long id, String name, String vendor, Integer vramGb, Integer tier) {
        static Hit of(GpuCatalog g) {
            return new Hit(g.getId(), g.getName(), g.getVendor() == null ? null : g.getVendor().name(), g.getVramGb(), g.getTier());
        }
    }

    /** Everything the autofill needs for one row, plus the blurb it proposes as the short description. */
    public record Detail(Long id, String name, String vendor, Integer vramGb, String memoryType, Integer tdpWatts,
                         Integer psuMinWatts, String pcieConnectors, Integer lengthMm, Integer tier, String shortDescription) {
        static Detail of(GpuCatalog g) {
            return new Detail(g.getId(), g.getName(), g.getVendor() == null ? null : g.getVendor().name(), g.getVramGb(),
                    g.getMemoryType(), g.getTdpWatts(), g.getPsuMinWatts(), g.getPcieConnectors(), g.getLengthMm(), g.getTier(),
                    GpuCatalogService.shortDescriptionSq(g));
        }
    }

    private final GpuCatalogService service;

    @GetMapping("/search")
    List<Hit> search(@RequestParam(name = "q", defaultValue = "") String q) {
        return service.search(q, 8).stream().map(m -> Hit.of(m.gpu())).toList();
    }

    @GetMapping("/{id}")
    Detail detail(@PathVariable Long id) {
        return Detail.of(service.get(id));
    }
}
