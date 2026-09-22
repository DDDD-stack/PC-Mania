package al.pcmania.web.site;

import al.pcmania.service.RateLimiter;
import al.pcmania.service.SeoService;
import al.pcmania.service.UpcomingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;

/** The "Së shpejti" page: stock on its way, where the only action is leaving a phone number. */
@Controller
@RequiredArgsConstructor
public class UpcomingController {

    private final UpcomingService service;
    private final SeoService seo;
    private final RateLimiter rateLimiter;

    @GetMapping("/se-shpejti")
    String list(Model model) {
        return render(new InterestForm(), model);
    }

    @PostMapping("/se-shpejti/{id}/njofto")
    String notifyMe(@PathVariable Long id, @Valid @ModelAttribute("form") InterestForm form, BindingResult errors,
                    HttpServletRequest request, Model model, RedirectAttributes flash) {
        if (StringUtils.hasText(form.getWebsite())) return "redirect:/se-shpejti";
        if (errors.hasErrors()) {
            model.addAttribute("openId", id);
            return render(form, model);
        }
        if (!rateLimiter.tryAcquire("interest:" + request.getRemoteAddr(), 8, Duration.ofHours(1))) {
            model.addAttribute("formError", "Keni dërguar shumë kërkesa në pak kohë. Na shkruani në WhatsApp.");
            model.addAttribute("openId", id);
            return render(form, model);
        }
        boolean added = service.addInterest(id, form.getCustomerName(), form.getCustomerPhone());
        flash.addFlashAttribute("notified", added
                ? "Të shkruajmë sapo të vijë. Faleminderit!"
                : "Ky numër ishte tashmë në listë — do t'ju kontaktojmë.");
        return "redirect:/se-shpejti";
    }

    private String render(InterestForm form, Model model) {
        var items = service.publicList();
        model.addAttribute("seo", seo.page("Së shpejti në PCMania",
                "Karta grafike dhe komponentë që po vijnë në stok. Lini numrin dhe ju njoftojmë të parët kur të mbërrijnë.",
                "/se-shpejti"));
        model.addAttribute("items", items);
        model.addAttribute("interest", service.interestCounts(items));
        model.addAttribute("form", form);
        return "site/upcoming";
    }
}
