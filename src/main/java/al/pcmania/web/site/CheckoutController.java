package al.pcmania.web.site;

import al.pcmania.domain.Enums.DeliveryMethod;
import al.pcmania.domain.Enums.PaymentMethod;
import al.pcmania.domain.Order;
import al.pcmania.service.CatalogService;
import al.pcmania.service.NotFoundException;
import al.pcmania.service.OrderService;
import al.pcmania.service.RateLimiter;
import al.pcmania.service.SeoService;
import al.pcmania.web.view.ProductDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.List;

/** Single-item "buy now" checkout. No account required. */
@Controller
@RequestMapping("/porosit")
@RequiredArgsConstructor
public class CheckoutController {

    static final List<String> CITIES = List.of("Tiranë", "Durrës", "Vlorë", "Elbasan", "Shkodër", "Fier", "Korçë", "Berat",
            "Lushnjë", "Kavajë", "Pogradec", "Gjirokastër", "Sarandë", "Lezhë", "Kukës", "Kamëz", "Laç", "Patos",
            "Peshkopi", "Kuçovë", "Krujë", "Burrel", "Librazhd", "Tepelenë", "Përmet");

    private final CatalogService catalog;
    private final OrderService orders;
    private final SeoService seo;
    private final RateLimiter rateLimiter;

    @GetMapping("/{slug}")
    String form(@PathVariable String slug, Model model, RedirectAttributes flash) {
        ProductDetail p = catalog.detail(slug).orElseThrow(NotFoundException::new);
        if (!p.isAvailable()) return unavailable(p, flash);
        return render(p, new CheckoutForm(), model);
    }

    @PostMapping("/{slug}")
    String submit(@PathVariable String slug, @Valid @ModelAttribute("form") CheckoutForm form, BindingResult errors,
                  HttpServletRequest request, Model model, RedirectAttributes flash) {
        ProductDetail p = catalog.detail(slug).orElseThrow(NotFoundException::new);
        if (!p.isAvailable()) return unavailable(p, flash);
        if (form.getQuantity() > p.quantity()) errors.rejectValue("quantity", "max", "Në stok ka vetëm " + p.quantity() + " copë");
        if (StringUtils.hasText(form.getWebsite())) return "redirect:/"; // honeypot tripped
        if (errors.hasErrors()) return render(p, form, model);
        if (!rateLimiter.tryAcquire("order:" + request.getRemoteAddr(), 5, Duration.ofHours(1))) {
            model.addAttribute("formError", "Keni dërguar shumë porosi në pak kohë. Ju lutem na kontaktoni në WhatsApp.");
            return render(p, form, model);
        }
        try {
            Order o = orders.place(slug, form);
            flash.addFlashAttribute("orderNumber", o.getOrderNumber());
            flash.addFlashAttribute("orderTotal", o.getTotalLek());
            flash.addFlashAttribute("orderPhone", o.getCustomerPhone());
            flash.addFlashAttribute("orderPayment", o.getPaymentMethod());
            flash.addFlashAttribute("orderTitle", p.title());
            return "redirect:/porosit/sukses";
        } catch (OrderService.OutOfStockException e) {
            return unavailable(p, flash);
        }
    }

    @GetMapping("/sukses")
    String success(Model model) {
        if (!model.containsAttribute("orderNumber")) return "redirect:/";
        model.addAttribute("seo", seo.page("Porosia u dërgua", "Faleminderit për porosinë.", "/porosit/sukses").withNoindex());
        return "site/checkout-success";
    }

    private String unavailable(ProductDetail p, RedirectAttributes flash) {
        flash.addFlashAttribute("notice", "Na vjen keq, ky produkt sapo u rezervua ose u shit. Na shkruani në WhatsApp për një alternativë.");
        return "redirect:/produkt/" + p.slug();
    }

    private String render(ProductDetail p, CheckoutForm form, Model model) {
        model.addAttribute("seo", seo.page("Porosit " + p.title(), "Porosit " + p.title() + " me pagesë në dorëzim.", "/porosit/" + p.slug()).withNoindex());
        model.addAttribute("p", p);
        model.addAttribute("form", form);
        model.addAttribute("cities", CITIES);
        model.addAttribute("deliveryMethods", DeliveryMethod.values());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("courierShipping", orders.shippingFor(DeliveryMethod.COURIER, p.transportIncluded()));
        return "site/checkout";
    }
}
