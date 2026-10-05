package al.pcmania.web.site;

import al.pcmania.config.AppProperties;
import al.pcmania.service.CatalogService;
import al.pcmania.domain.Enums.UpcomingStatus;
import al.pcmania.service.SeoService;
import al.pcmania.service.UpcomingService;
import al.pcmania.service.chat.ChatAssistant;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Model attributes shared by every public page (navigation, contact details).
 *
 * It runs before every handler in this package, including ones that render no page, so endpoints that
 * are not HTML pages (photos, sitemap) live in {@code web.resource} instead. Otherwise every photo on a
 * page would cost these lookups again, even the repeat views that are answered from the ETag alone.
 */
@ControllerAdvice(basePackageClasses = SiteAdvice.class)
@RequiredArgsConstructor
public class SiteAdvice {

    private final CatalogService catalog;
    private final SeoService seo;
    private final AppProperties props;
    private final UpcomingService upcoming;
    private final ChatAssistant assistant;

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("navCategories", catalog.categories());
        model.addAttribute("site", props);
        model.addAttribute("whatsappGeneral", seo.whatsappLink("Përshëndetje PCMania! Kam një pyetje."));
        // The "Së shpejti" link only appears once something is actually on the way.
        model.addAttribute("hasUpcoming", upcoming.countByStatus(UpcomingStatus.VISIBLE) > 0);
        // The assistant's bubble: the chat when it can answer, the WhatsApp link when it is over its monthly cap.
        boolean configured = assistant.configured();
        model.addAttribute("chatConfigured", configured);
        model.addAttribute("chatAvailable", configured && assistant.available());
    }
}
