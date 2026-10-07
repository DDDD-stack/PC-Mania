package al.pcmania.web.admin;

import al.pcmania.domain.Enums.OrderStatus;
import al.pcmania.domain.Order;
import al.pcmania.repo.OrderRepository;
import al.pcmania.service.NotFoundException;
import al.pcmania.service.OrderService;
import al.pcmania.web.Links;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderRepository orders;
    private final OrderService service;

    @GetMapping("/orders")
    String list(@RequestParam(required = false) OrderStatus status, @RequestParam(defaultValue = "0") int page, Model model) {
        var pageable = PageRequest.of(Math.max(page, 0), 30);
        Page<Order> result = status == null ? orders.findAllByOrderByCreatedAtDesc(pageable) : orders.findByStatusOrderByCreatedAtDesc(status, pageable);
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) counts.put(s, orders.countByStatus(s));
        model.addAttribute("page", result);
        model.addAttribute("status", status);
        model.addAttribute("counts", counts);
        model.addAttribute("statuses", OrderStatus.values());
        return "admin/orders/list";
    }

    @GetMapping("/orders/{id}")
    String detail(@PathVariable Long id, Model model) {
        Order o = orders.findWithItems(id).orElseThrow(NotFoundException::new);
        model.addAttribute("order", o);
        model.addAttribute("profit", o.getItems().stream().mapToInt(i -> (i.getPriceLekSnapshot() - i.getCostLekSnapshot()) * i.getQuantity()).sum());
        model.addAttribute("customerWhatsapp", customerWhatsapp(o));
        model.addAttribute("transitions", java.util.Arrays.stream(OrderStatus.values()).filter(o.allowedTransitions()::contains).toList());
        return "admin/orders/detail";
    }

    @PostMapping("/orders/{id}/status")
    String changeStatus(@PathVariable Long id, @RequestParam OrderStatus status, RedirectAttributes flash) {
        try {
            service.changeStatus(id, status);
            flash.addFlashAttribute("success", "Statusi u ndryshua në \"" + status.label + "\".");
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    @PostMapping("/orders/{id}/notes")
    String notes(@PathVariable Long id, @RequestParam(required = false) String adminNotes, RedirectAttributes flash) {
        service.updateNotes(id, adminNotes);
        flash.addFlashAttribute("success", "Shënimet u ruajtën.");
        return "redirect:/admin/orders/" + id;
    }

    @PostMapping("/products/{productId}/offline-sale")
    String offlineSale(@PathVariable Long productId, @RequestParam int quantity, @RequestParam int priceLek,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(required = false) String buyer, RedirectAttributes flash) {
        try {
            Order o = service.recordOfflineSale(productId, quantity, priceLek, date, buyer);
            flash.addFlashAttribute("success", "Shitja u regjistrua si porosia " + o.getOrderNumber() + ".");
        } catch (OrderService.OutOfStockException e) {
            flash.addFlashAttribute("error", "Sasia në stok nuk mjafton për këtë shitje.");
        }
        return "redirect:/admin/products/" + productId;
    }

    static String customerWhatsapp(Order o) {
        return Links.whatsappTo(o.getCustomerPhone(),
                "Përshëndetje " + o.getCustomerName() + ", ju shkruajmë nga PCMania për porosinë " + o.getOrderNumber() + ".");
    }
}
