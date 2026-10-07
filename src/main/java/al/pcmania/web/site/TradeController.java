package al.pcmania.web.site;

import al.pcmania.domain.Enums.ContactMethod;
import al.pcmania.domain.Enums.TradeItemType;
import al.pcmania.domain.TradeRequest;
import al.pcmania.service.CatalogService;
import al.pcmania.service.NotFoundException;
import al.pcmania.service.RateLimiter;
import al.pcmania.service.SeoService;
import al.pcmania.service.TradeMedia;
import al.pcmania.service.TradeService;
import al.pcmania.web.view.ProductDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Controller
@RequiredArgsConstructor
@Slf4j
public class TradeController {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern REQUEST_NUMBER = Pattern.compile("^TR-\\d{4}-\\d{4,}$");

    private final CatalogService catalog;
    private final TradeService trades;
    private final SeoService seo;
    private final RateLimiter rateLimiter;

    @GetMapping("/nderro")
    String overview(Model model) {
        model.addAttribute("seo", seo.page("Nderro – sill kartën e vjetër, merr një të re",
                "Ndërro kartën grafike, procesorin ose RAM-in e vjetër me një produkt nga dyqani. Na dërgo një video testi dhe të japim një ofertë brenda 24 orëve.",
                "/nderro"));
        model.addAttribute("products", catalog.tradeable());
        model.addAttribute("itemTypes", TradeItemType.values());
        return "site/trade";
    }

    @GetMapping("/nderro/{slug}")
    String form(@PathVariable String slug, Model model, RedirectAttributes flash) {
        ProductDetail p = catalog.detail(slug).orElseThrow(NotFoundException::new);
        if (!p.isTradeable()) return notTradeable(p, flash);
        TradeForm form = new TradeForm();
        return render(p, form, model);
    }

    @PostMapping("/nderro/{slug}")
    Object submit(@PathVariable String slug, @Valid @ModelAttribute("form") TradeForm form, BindingResult errors,
                  @RequestParam(name = "media", required = false) MultipartFile media,
                  @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                  HttpServletRequest request, Model model, RedirectAttributes flash) {
        boolean script = "XMLHttpRequest".equals(requestedWith);
        ProductDetail p = catalog.detail(slug).orElseThrow(NotFoundException::new);
        if (!p.isTradeable()) {
            return script ? json(HttpStatus.CONFLICT, Map.of("form", "Ky produkt nuk pranon më këmbim."), null) : notTradeable(p, flash);
        }
        if (StringUtils.hasText(form.getWebsite())) {
            return script ? json(HttpStatus.OK, Map.of(), "/nderro") : "redirect:/nderro";
        }
        validateContact(form, errors);
        if (!errors.hasErrors() && !rateLimiter.tryAcquire("trade:" + request.getRemoteAddr(), 5, Duration.ofHours(1))) {
            errors.reject("rate", "Keni dërguar shumë kërkesa në pak kohë. Na shkruani në WhatsApp.");
        }
        if (!errors.hasErrors()) {
            try {
                TradeRequest t = trades.submit(slug, form, media);
                String next = "/nderro/derguar?nr=" + t.getRequestNumber();
                return script ? json(HttpStatus.OK, Map.of(), next) : "redirect:" + next;
            } catch (TradeService.MediaException e) {
                errors.rejectValue("whatsappInstead", "media", e.getMessage());
            } catch (IOException e) {
                log.warn("Trade-in upload failed: {}", e.getMessage());
                errors.reject("upload", "Ngarkimi dështoi. Provoni përsëri ose dërgojeni videon në WhatsApp.");
            }
        }
        if (script) return json(HttpStatus.UNPROCESSABLE_ENTITY, fieldErrors(errors), null);
        return render(p, form, model);
    }

    @GetMapping("/nderro/derguar")
    String sent(@RequestParam(name = "nr", required = false) String number, Model model) {
        if (number == null || !REQUEST_NUMBER.matcher(number).matches()) return "redirect:/nderro";
        model.addAttribute("seo", seo.page("Kërkesa për këmbim u dërgua", "Faleminderit.", "/nderro/derguar").withNoindex());
        model.addAttribute("number", number);
        model.addAttribute("whatsappProof", seo.whatsappLink("Përshëndetje! Po dërgoj videon e testit për kërkesën e këmbimit " + number + "."));
        return "site/trade-sent";
    }

    static void validateContact(TradeForm form, BindingResult errors) {
        if (form.getContactMethod() == ContactMethod.EMAIL) {
            if (!StringUtils.hasText(form.getCustomerEmail()) || !EMAIL.matcher(form.getCustomerEmail().trim()).matches()) {
                errors.rejectValue("customerEmail", "invalid", "Shkruani një email të vlefshëm");
            }
        } else if (form.getContactMethod() == ContactMethod.PHONE) {
            if (!StringUtils.hasText(form.getCustomerPhone()) || !form.getCustomerPhone().trim().matches(CheckoutForm.PHONE_REGEX)) {
                errors.rejectValue("customerPhone", "invalid", "Shkruani një numër telefoni të vlefshëm");
            }
        }
    }

    private String render(ProductDetail p, TradeForm form, Model model) {
        model.addAttribute("seo", seo.page("Nderro për " + p.title(), "Kërko një ofertë këmbimi për " + p.title() + ".",
                "/nderro/" + p.slug()).withNoindex());
        model.addAttribute("p", p);
        model.addAttribute("form", form);
        model.addAttribute("itemTypes", TradeItemType.values());
        model.addAttribute("contactMethods", ContactMethod.values());
        model.addAttribute("maxVideoMb", TradeMedia.MAX_VIDEO_BYTES / (1024 * 1024));
        model.addAttribute("maxImageMb", TradeMedia.MAX_IMAGE_BYTES / (1024 * 1024));
        return "site/trade-form";
    }

    private static String notTradeable(ProductDetail p, RedirectAttributes flash) {
        flash.addFlashAttribute("notice", "Ky produkt nuk pranon këmbim për momentin. Shikoni produktet e tjera te \"Nderro\".");
        return "redirect:/produkt/" + p.slug();
    }

    private static Map<String, String> fieldErrors(BindingResult errors) {
        Map<String, String> out = new LinkedHashMap<>();
        for (FieldError e : errors.getFieldErrors()) out.putIfAbsent(e.getField(), e.getDefaultMessage());
        if (errors.hasGlobalErrors()) out.put("form", errors.getGlobalError().getDefaultMessage());
        return out;
    }

    private static ResponseEntity<Map<String, Object>> json(HttpStatus status, Map<String, ?> errors, String redirect) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", status.is2xxSuccessful());
        if (redirect != null) body.put("redirect", redirect);
        if (!errors.isEmpty()) body.put("errors", errors);
        return ResponseEntity.status(status).body(body);
    }
}
