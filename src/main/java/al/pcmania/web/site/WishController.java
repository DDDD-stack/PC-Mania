package al.pcmania.web.site;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.service.RateLimiter;
import al.pcmania.service.SeoService;
import al.pcmania.service.WishService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;

@Controller
@RequiredArgsConstructor
public class WishController {

    private final WishService service;
    private final SeoService seo;
    private final RateLimiter rateLimiter;

    @GetMapping("/kerko-produkt")
    String form(@RequestParam(name = "p", required = false) String prefill, Model model) {
        WishForm form = new WishForm();
        if (StringUtils.hasText(prefill)) form.setItem(prefill.trim().substring(0, Math.min(prefill.trim().length(), 200)));
        return render(form, model);
    }

    @PostMapping("/kerko-produkt")
    String submit(@Valid @ModelAttribute("form") WishForm form, BindingResult errors,
                  HttpServletRequest request, Model model, RedirectAttributes flash) {
        if (StringUtils.hasText(form.getWebsite())) return "redirect:/kerko-produkt";
        if (errors.hasErrors()) return render(form, model);
        if (!rateLimiter.tryAcquire("wish:" + request.getRemoteAddr(), 5, Duration.ofHours(1))) {
            model.addAttribute("formError", "Keni dërguar shumë kërkesa në pak kohë. Na shkruani në WhatsApp.");
            return render(form, model);
        }
        service.create(form);
        flash.addFlashAttribute("submitted", form.getCustomerPhone());
        flash.addFlashAttribute("submittedItem", form.getItem());
        return "redirect:/kerko-produkt";
    }

    private String render(WishForm form, Model model) {
        model.addAttribute("seo", seo.page("Kërko një produkt – e sjellim ne",
                "Nuk e gjen kartën grafike apo pjesën që të duhet? Na thuaj çfarë kërkon dhe buxhetin: e gjejmë, të telefonojmë me çmimin dhe vendos ti.",
                "/kerko-produkt"));
        model.addAttribute("form", form);
        model.addAttribute("conditions", Condition.values());
        return "site/wish";
    }
}
