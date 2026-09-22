package al.pcmania.web.site;

import al.pcmania.config.AppProperties;
import al.pcmania.service.CatalogService;
import al.pcmania.domain.Enums.UpcomingStatus;
import al.pcmania.service.SeoService;
import al.pcmania.service.UpcomingService;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Model attributes shared by every public page (navigation, contact details). */
@ControllerAdvice(basePackageClasses = SiteAdvice.class)
@RequiredArgsConstructor
public class SiteAdvice {

    private final CatalogService catalog;
    private final SeoService seo;
    private final AppProperties props;
    private final UpcomingService upcoming;

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("navCategories", catalog.categories());
        model.addAttribute("site", props);
        model.addAttribute("whatsappGeneral", seo.whatsappLink("Përshëndetje PCMania! Kam një pyetje."));
        // The "Së shpejti" link only appears once something is actually on the way.
        model.addAttribute("hasUpcoming", upcoming.countByStatus(UpcomingStatus.VISIBLE) > 0);
    }
}
