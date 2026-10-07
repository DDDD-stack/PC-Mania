package al.pcmania.web.admin;

import al.pcmania.domain.Enums.DeliveryMethod;
import al.pcmania.domain.Enums.PaymentMethod;
import al.pcmania.domain.Enums.TradeStatus;
import al.pcmania.domain.Order;
import al.pcmania.domain.TradeRequest;
import al.pcmania.repo.StoredFileRepository;
import al.pcmania.repo.TradeRequestRepository;
import al.pcmania.service.FileStorage;
import al.pcmania.service.NotFoundException;
import al.pcmania.service.OrderService;
import al.pcmania.service.TradeService;
import al.pcmania.web.Links;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/trades")
@RequiredArgsConstructor
public class AdminTradeController {

    private final TradeRequestRepository repo;
    private final TradeService trades;
    private final FileStorage files;

    @GetMapping
    String list(@RequestParam(required = false) TradeStatus status, @RequestParam(defaultValue = "0") int page, Model model) {
        var pageable = PageRequest.of(Math.max(page, 0), 30);
        Map<TradeStatus, Long> counts = new EnumMap<>(TradeStatus.class);
        for (TradeStatus s : TradeStatus.values()) counts.put(s, repo.countByStatus(s));
        model.addAttribute("page", status == null ? repo.findAllByOrderByCreatedAtDesc(pageable) : repo.findByStatusOrderByCreatedAtDesc(status, pageable));
        model.addAttribute("status", status);
        model.addAttribute("counts", counts);
        model.addAttribute("statuses", TradeStatus.values());
        return "admin/trades/list";
    }

    @GetMapping("/{id}")
    String detail(@PathVariable Long id, Model model) {
        TradeRequest t = trades.get(id);
        model.addAttribute("t", t);
        model.addAttribute("defaultDays", TradeService.DEFAULT_QUOTE_DAYS);
        model.addAttribute("deliveryMethods", DeliveryMethod.values());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("media", t.getMediaFilename() == null ? null : files.meta(t.getMediaFilename()).orElse(null));
        if (t.getCustomerPhone() != null) {
            model.addAttribute("customerWhatsapp", Links.whatsappTo(t.getCustomerPhone(),
                    "Përshëndetje " + t.getCustomerName() + ", ju shkruajmë nga PCMania për kërkesën e këmbimit " + t.getRequestNumber() + "."));
        }
        return "admin/trades/detail";
    }

    @GetMapping("/{id}/media")
    ResponseEntity<Resource> media(@PathVariable Long id) {
        TradeRequest t = trades.get(id);
        if (t.getMediaFilename() == null) throw new NotFoundException();
        StoredFileRepository.Content c = files.content(t.getMediaFilename()).orElseThrow(NotFoundException::new);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.getContentType()))
                .cacheControl(CacheControl.noStore())
                .body(new ByteArrayResource(c.getData()));
    }

    @PostMapping("/{id}/reviewing")
    String reviewing(@PathVariable Long id, RedirectAttributes flash) {
        return act(id, flash, () -> trades.markReviewing(id), "U shënua \"Në shqyrtim\".");
    }

    @PostMapping("/{id}/quote")
    String quote(@PathVariable Long id, @RequestParam(required = false) Integer quotedValueLek, @RequestParam(required = false) String quoteNotes,
                 @RequestParam(required = false) Integer days, RedirectAttributes flash) {
        try {
            boolean emailed = trades.quote(id, quotedValueLek == null ? 0 : quotedValueLek, quoteNotes, days);
            TradeRequest t = trades.get(id);
            flash.addFlashAttribute("success", emailed
                    ? "Oferta u ruajt dhe iu dërgua klientit me email."
                    : "Oferta u ruajt. " + (t.getCustomerEmail() != null
                        ? "Emaili NUK u dërgua (posta nuk është konfiguruar): kontaktojeni klientin vetë."
                        : "Telefonojini klientit për t'ia thënë."));
        } catch (IllegalStateException | IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/trades/" + id;
    }

    @PostMapping("/{id}/decline")
    String decline(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes flash) {
        return act(id, flash, () -> trades.decline(id, reason), "Kërkesa u refuzua.");
    }

    @PostMapping("/{id}/accept")
    String accept(@PathVariable Long id, RedirectAttributes flash) {
        return act(id, flash, () -> trades.accept(id), "Oferta u shënua si e pranuar. Tani bëni porosinë.");
    }

    @PostMapping("/{id}/convert")
    String convert(@PathVariable Long id, @RequestParam(required = false) String phone, @RequestParam(required = false) String city,
                   @RequestParam(required = false) String address, @RequestParam(required = false) DeliveryMethod deliveryMethod,
                   @RequestParam(required = false) PaymentMethod paymentMethod, RedirectAttributes flash) {
        try {
            Order o = trades.convert(id, new TradeService.OrderDetails(phone, city, address, deliveryMethod, paymentMethod));
            flash.addFlashAttribute("success", "U krijua porosia " + o.getOrderNumber() + " me vlerën e këmbimit të zbritur.");
            return "redirect:/admin/orders/" + o.getId();
        } catch (OrderService.OutOfStockException e) {
            flash.addFlashAttribute("error", "Produkti nuk është më në stok. Zgjidhni bashkë me klientin një tjetër.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/trades/" + id;
    }

    @PostMapping("/{id}/stock")
    String takeIntoStock(@PathVariable Long id, RedirectAttributes flash) {
        try {
            Long productId = trades.takeIntoStock(id);
            flash.addFlashAttribute("success", "Artikulli u shtua si Draft me koston e këmbimit. Shtoni fotot, çmimin dhe aktivizojeni.");
            return "redirect:/admin/products/" + productId;
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/trades/" + id;
        }
    }

    @PostMapping("/{id}/delete-media")
    String deleteMedia(@PathVariable Long id, RedirectAttributes flash) {
        return act(id, flash, () -> trades.deleteMedia(id), "Videoja/fotoja u fshi.");
    }

    private static String act(Long id, RedirectAttributes flash, Runnable action, String done) {
        try {
            action.run();
            flash.addFlashAttribute("success", done);
        } catch (IllegalStateException | IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/trades/" + id;
    }
}
