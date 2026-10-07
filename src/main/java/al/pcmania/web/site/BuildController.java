package al.pcmania.web.site;

import al.pcmania.domain.Enums.UseCase;
import al.pcmania.service.BuildRequestService;
import al.pcmania.service.RateLimiter;
import al.pcmania.service.SeoService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;

@Controller
@RequiredArgsConstructor
public class BuildController {

    private final BuildRequestService service;
    private final SeoService seo;
    private final RateLimiter rateLimiter;

    @GetMapping("/pc-me-porosi")
    String form(Model model) {
        return render(new BuildRequestForm(), model);
    }

    @PostMapping("/pc-me-porosi")
    String submit(@Valid @ModelAttribute("form") BuildRequestForm form, BindingResult errors,
                  HttpServletRequest request, Model model, RedirectAttributes flash) {
        if (StringUtils.hasText(form.getWebsite())) return "redirect:/pc-me-porosi";
        if (errors.hasErrors()) return render(form, model);
        if (!rateLimiter.tryAcquire("build:" + request.getRemoteAddr(), 5, Duration.ofHours(1))) {
            model.addAttribute("formError", "Keni dërguar shumë kërkesa në pak kohë. Na shkruani në WhatsApp.");
            return render(form, model);
        }
        service.create(form);
        flash.addFlashAttribute("submitted", form.getCustomerPhone());
        return "redirect:/pc-me-porosi";
    }

    private String render(BuildRequestForm form, Model model) {
        model.addAttribute("seo", seo.page("PC me porosi – ndërtojmë kompjuterin tënd",
                "Na jep buxhetin dhe përdorimin: zgjedhim pjesët, të dërgojmë ofertë falas, e montojmë, e testojmë dhe ta sjellim gati. Tiranë dhe gjithë Shqipëria.",
                "/pc-me-porosi"));
        model.addAttribute("form", form);
        model.addAttribute("useCases", UseCase.values());
        return "site/build";
    }
}
