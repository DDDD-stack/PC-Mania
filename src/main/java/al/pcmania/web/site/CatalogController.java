package al.pcmania.web.site;

import al.pcmania.domain.Category;
import al.pcmania.domain.Enums.Condition;
import al.pcmania.service.CatalogService;
import al.pcmania.service.ComboService;
import al.pcmania.service.NotFoundException;
import al.pcmania.service.SeoService;
import al.pcmania.service.UpcomingService;
import al.pcmania.web.view.CatalogFilter;
import al.pcmania.web.view.ProductDetail;
import al.pcmania.web.view.Seo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import al.pcmania.web.view.ProductCard;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.regex.Pattern;

@Controller
@RequiredArgsConstructor
public class CatalogController {

    private static final Pattern BOTS = Pattern.compile("(?i)bot|crawl|spider|facebookexternalhit|preview|slurp");

    private final CatalogService catalog;
    private final UpcomingService upcoming;
    private final SeoService seo;
    private final ComboService combos;

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("seo", seo.home());
        model.addAttribute("combos", combos.visible(4));
        model.addAttribute("newest", catalog.newest(8));
        model.addAttribute("tiles", catalog.categoryTiles());
        model.addAttribute("upcoming", upcoming.publicList().stream().limit(4).toList());
        return "site/home";
    }

    @GetMapping("/kategori/{slug}")
    String category(@PathVariable String slug,
                    @RequestParam(required = false) Integer min,
                    @RequestParam(required = false) Integer max,
                    @RequestParam(name = "gjendja", required = false) List<String> conditions,
                    @RequestParam(name = "marka", required = false) List<String> brands,
                    @RequestParam(name = "spec", required = false) List<String> specs,
                    @RequestParam(name = "nderrim", required = false) Boolean trade,
                    @RequestParam(name = "gpu", required = false) List<String> gpus,
                    @RequestParam(name = "ram", required = false) List<Integer> rams,
                    @RequestParam(name = "cmimi", required = false) String band,
                    @RequestParam(name = "rendit", required = false) String sort,
                    @RequestParam(name = "faqe", required = false) Integer page,
                    Model model) {
        Category category = catalog.category(slug).orElseThrow(NotFoundException::new);
        CatalogFilter filter = CatalogFilter.of(min, max, conditions, brands, specs, trade, gpus, rams, band, sort, page);
        String path = "/kategori/" + slug;

        Page<ProductCard> results = catalog.list(slug, filter);
        Seo pageSeo = seo.category(category, filter.page(), results.getSize(), results.getContent());

        if (filter.hasFilters() || filter.sort() != CatalogFilter.Sort.TE_REJAT) {
            pageSeo = pageSeo.withNoindex().withCanonical(seo.abs(path));
        }

        model.addAttribute("seo", pageSeo);
        model.addAttribute("category", category);
        model.addAttribute("filter", filter);
        model.addAttribute("path", path);
        model.addAttribute("page", results);
        model.addAttribute("intro", catalog.categoryIntro(category));
        model.addAttribute("facets", catalog.facets(slug));
        model.addAttribute("conditions", Condition.values());
        model.addAttribute("sorts", CatalogFilter.Sort.values());
        model.addAttribute("bands", CatalogFilter.PriceBand.values());
        model.addAttribute("prebuilt", category.isPrebuilt());
        return "site/category";
    }

    @GetMapping("/kerko")
    String search(@RequestParam(name = "q", required = false) String q,
                  @RequestParam(name = "faqe", required = false) Integer page, Model model) {
        String query = q == null ? "" : q.trim();
        Page<ProductCard> results = catalog.search(query, page == null ? 1 : page);
        model.addAttribute("seo", seo.page(query.isBlank() ? "Kërko produkt" : "Kërko: " + query,
                "Kërko karta grafike dhe pjesë kompjuteri që PCMania i ka në stok.", "/kerko").withNoindex());
        model.addAttribute("searchQuery", query);
        model.addAttribute("page", results);
        model.addAttribute("tooShort", !query.isBlank() && query.length() < 2);
        return "site/search";
    }

    @GetMapping("/produkt/{slug}")
    ModelAndView product(@PathVariable String slug, @RequestHeader(value = "User-Agent", required = false) String userAgent, Model model) {
        ProductDetail p = catalog.detail(slug).orElse(null);
        if (p == null) {
            String current = catalog.renamedTo(slug).orElseThrow(NotFoundException::new);
            RedirectView moved = new RedirectView("/produkt/" + current);
            moved.setStatusCode(HttpStatus.MOVED_PERMANENTLY);
            moved.setExposeModelAttributes(false);
            return new ModelAndView(moved);
        }
        Category category = catalog.category(p.categorySlug()).orElse(null);
        if (userAgent != null && !BOTS.matcher(userAgent).find()) catalog.countView(p.id());

        model.addAttribute("seo", seo.product(p, category));
        model.addAttribute("p", p);
        model.addAttribute("category", category);
        model.addAttribute("related", catalog.related(p, 4));
        model.addAttribute("whatsappLink", seo.whatsappLink(p));
        model.addAttribute("shareUrl", seo.abs("/produkt/" + p.slug()));
        return new ModelAndView("site/product", model.asMap());
    }
}
