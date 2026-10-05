package al.pcmania.web.admin;

import al.pcmania.config.ChatProperties;
import al.pcmania.domain.ChatLead;
import al.pcmania.domain.ChatMessage;
import al.pcmania.domain.ChatSession;
import al.pcmania.domain.Enums.ChatRole;
import al.pcmania.domain.Enums.LeadStatus;
import al.pcmania.domain.Product;
import al.pcmania.repo.ChatLeadRepository;
import al.pcmania.repo.ChatMessageRepository;
import al.pcmania.repo.ChatSessionRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.service.NotFoundException;
import al.pcmania.service.chat.ChatService;
import al.pcmania.service.chat.ChatDemand;
import al.pcmania.service.chat.ChatSpend;
import al.pcmania.service.chat.GuidedFinderProvider;
import al.pcmania.service.chat.ProviderUsageService;
import al.pcmania.domain.ProviderUsage;
import al.pcmania.web.Links;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/** Admin > Asistenti: what customers ask for and do not get, the leads to call, and the transcripts. */
@Controller
@RequestMapping("/admin/chat")
@RequiredArgsConstructor
public class AdminChatController {

    /** One conversation in the list: when, how long, what it opened with, what the assistant showed, who answered. */
    public record SessionRow(ChatSession session, String opening, List<Product> products, boolean leadCaptured, String provider) {}

    /** One provider's day in the usage panel. */
    public record UsageRow(String provider, int requests, int errors, int rateLimitHits) {}

    /** One turn in a transcript, with the products its tool calls surfaced. */
    public record TranscriptRow(ChatMessage message, List<Product> products) {}

    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final ChatLeadRepository leads;
    private final ProductRepository products;
    private final ChatDemand demand;
    private final ChatSpend spend;
    private final ChatService assistant;
    private final ProviderUsageService providerUsage;
    private final ChatProperties props;

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("leadStatuses", LeadStatus.values());
        model.addAttribute("newLeads", leads.countByStatus(LeadStatus.NEW));
    }

    @GetMapping
    @Transactional(readOnly = true)
    String index(@RequestParam(defaultValue = "0") int page, Model model) {
        List<ChatDemand.Row> report = demand.report();
        model.addAttribute("notInStock", report.stream().filter(r -> !r.inStock()).toList());
        model.addAttribute("inStock", report.stream().filter(ChatDemand.Row::inStock).toList());
        var usage = spend.thisMonth();
        model.addAttribute("usage", usage);
        model.addAttribute("capUsd", spend.capUsd());
        model.addAttribute("capPercent", spend.capUsd() > 0 ? Math.min(100, Math.round(usage.costUsd() * 100 / spend.capUsd())) : 0);
        model.addAttribute("overCap", spend.overCap());
        model.addAttribute("provider", assistant.primary().name());
        model.addAttribute("providerAvailable", assistant.primary().isAvailable());
        model.addAttribute("geminiConfigured", props.gemini().keyConfigured());
        model.addAttribute("anthropicConfigured", props.anthropic().keyConfigured());
        model.addAttribute("geminiDailyCap", props.gemini().requestsPerDay());
        Map<String, ProviderUsage> today = providerUsage.today().stream().collect(Collectors.toMap(ProviderUsage::getProvider, u -> u, (a, b) -> a));
        model.addAttribute("todayRows", List.of("gemini", "anthropic", GuidedFinderProvider.NAME, ProviderUsageService.FALLBACK).stream()
                .map(name -> { ProviderUsage u = today.get(name); return new UsageRow(name, u == null ? 0 : u.getRequestCount(),
                        u == null ? 0 : u.getErrorCount(), u == null ? 0 : u.getRateLimitHits()); }).toList());
        model.addAttribute("geminiToday", today.containsKey("gemini") ? today.get("gemini").getRequestCount() : 0);
        model.addAttribute("fallbacksToday", today.containsKey(ProviderUsageService.FALLBACK) ? today.get(ProviderUsageService.FALLBACK).getRequestCount() : 0);
        model.addAttribute("recentUsage", providerUsage.lastDays(7));

        Page<ChatSession> sessionPage = sessions.findAllByOrderByLastMessageAtDesc(PageRequest.of(Math.max(page, 0), 25));
        List<Long> ids = sessionPage.getContent().stream().map(ChatSession::getId).toList();
        Map<Long, String> openings = ids.isEmpty() ? Map.of() : messages.firstByRole(ids, ChatRole.USER).stream()
                .collect(Collectors.toMap(m -> m.getSession().getId(), ChatMessage::getContent, (a, b) -> a));
        Map<Long, LinkedHashSet<String>> slugs = new HashMap<>();
        if (!ids.isEmpty()) {
            for (ChatMessage m : messages.findBySessionIds(ids)) {
                for (ChatService.Card c : assistant.cardsOf(m)) slugs.computeIfAbsent(m.getSession().getId(), k -> new LinkedHashSet<>()).add(c.slug());
            }
        }
        Map<String, Product> bySlug = productsBySlug(slugs.values().stream().flatMap(Collection::stream).toList());
        List<SessionRow> rows = sessionPage.getContent().stream().map(s -> new SessionRow(s, openings.get(s.getId()),
                slugs.getOrDefault(s.getId(), new LinkedHashSet<>()).stream().map(bySlug::get).filter(Objects::nonNull).toList(),
                s.isLeadCaptured(), s.getProviderUsed())).toList();
        model.addAttribute("page", sessionPage);
        model.addAttribute("rows", rows);
        return "admin/chat/index";
    }

    @GetMapping("/sessions/{id}")
    @Transactional(readOnly = true)
    String session(@PathVariable Long id, Model model) {
        ChatSession s = sessions.findById(id).orElseThrow(NotFoundException::new);
        List<ChatMessage> all = messages.findBySessionOrderByIdAsc(s);
        List<String> slugs = all.stream().flatMap(m -> assistant.cardsOf(m).stream()).map(ChatService.Card::slug).distinct().toList();
        Map<String, Product> bySlug = productsBySlug(slugs);
        model.addAttribute("s", s);
        model.addAttribute("rows", all.stream().map(m -> new TranscriptRow(m,
                assistant.cardsOf(m).stream().map(c -> bySlug.get(c.slug())).filter(Objects::nonNull).toList())).toList());
        model.addAttribute("leads", leads.findBySessionIdOrderByCreatedAtAsc(id));
        return "admin/chat/session";
    }

    @GetMapping("/leads")
    String leads(@RequestParam(required = false) LeadStatus status, @RequestParam(defaultValue = "0") int page, Model model) {
        var pageable = PageRequest.of(Math.max(page, 0), 30);
        Map<LeadStatus, Long> counts = new EnumMap<>(LeadStatus.class);
        for (LeadStatus st : LeadStatus.values()) counts.put(st, leads.countByStatus(st));
        model.addAttribute("page", status == null ? leads.findAllByOrderByCreatedAtDesc(pageable) : leads.findByStatusOrderByCreatedAtDesc(status, pageable));
        model.addAttribute("status", status);
        model.addAttribute("counts", counts);
        return "admin/chat/leads";
    }

    @GetMapping("/leads/{id}")
    @Transactional(readOnly = true)
    String lead(@PathVariable Long id, Model model) {
        ChatLead l = leads.findById(id).orElseThrow(NotFoundException::new);
        model.addAttribute("l", l);
        model.addAttribute("sessionId", l.getSession() == null ? null : l.getSession().getId());
        model.addAttribute("customerWhatsapp", Links.whatsappTo(l.getPhone(),
                "Përshëndetje " + l.getName() + ", ju shkruajmë nga PCMania për kërkesën tuaj: " + l.getWantedItem() + "."));
        return "admin/chat/lead";
    }

    @PostMapping("/leads/{id}")
    @Transactional
    String updateLead(@PathVariable Long id, @RequestParam LeadStatus status, RedirectAttributes flash) {
        ChatLead l = leads.findById(id).orElseThrow(NotFoundException::new);
        l.setStatus(status);
        flash.addFlashAttribute("success", "Statusi u ndryshua në \"" + status.label + "\".");
        return "redirect:/admin/chat/leads/" + id;
    }

    @PostMapping("/leads/{id}/delete")
    @Transactional
    String deleteLead(@PathVariable Long id, RedirectAttributes flash) {
        leads.delete(leads.findById(id).orElseThrow(NotFoundException::new));
        flash.addFlashAttribute("success", "Kontakti u fshi.");
        return "redirect:/admin/chat/leads";
    }

    private Map<String, Product> productsBySlug(Collection<String> slugs) {
        if (slugs.isEmpty()) return Map.of();
        return products.findBySlugIn(slugs).stream().collect(Collectors.toMap(Product::getSlug, p -> p, (a, b) -> a));
    }
}
