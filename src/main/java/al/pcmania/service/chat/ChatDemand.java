package al.pcmania.service.chat;

import al.pcmania.domain.ChatLead;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.GpuCatalog;
import al.pcmania.repo.ChatLeadRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.service.GpuCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * The demand signal: what customers asked the assistant for, grouped, with whether the shop has it.
 * A lead's {@code wantedItem} is resolved to a catalogue row with the same matcher as the autofill
 * ("rtx 3070", "RTX3070 8gb" and "3070" become one line); text that matches no row is grouped as typed.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatDemand {

    public record Row(String label, GpuCatalog gpu, int count, boolean inStock, LocalDateTime lastAskedAt, Integer maxBudgetLek) {}

    private final ChatLeadRepository leads;
    private final GpuCatalogService catalog;
    private final ProductRepository products;

    /** Most asked for first; the caller splits off what is not in stock. */
    public List<Row> report() {
        Map<String, List<ChatLead>> groups = new LinkedHashMap<>();
        Map<String, GpuCatalog> resolved = new HashMap<>();
        for (ChatLead l : leads.findAllByOrderByCreatedAtDesc()) {
            Optional<GpuCatalog> g = catalog.resolve(l.getWantedItem());
            String key = g.map(x -> "gpu:" + x.getId()).orElseGet(() -> "text:" + GpuCatalogService.normalize(l.getWantedItem()));
            g.ifPresent(x -> resolved.put(key, x));
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
        }
        List<Row> rows = new ArrayList<>();
        groups.forEach((key, list) -> {
            GpuCatalog g = resolved.get(key);
            boolean inStock = g != null && products.existsByGpuModelIdAndStatusAndQuantityGreaterThan(g.getId(), ProductStatus.ACTIVE, 0);
            Integer maxBudget = list.stream().map(ChatLead::getBudgetLek).filter(Objects::nonNull).max(Integer::compareTo).orElse(null);
            rows.add(new Row(g != null ? g.getName() : list.get(0).getWantedItem(), g, list.size(), inStock,
                    list.get(0).getCreatedAt(), maxBudget));
        });
        rows.sort(Comparator.comparingInt(Row::count).reversed().thenComparing(Row::lastAskedAt, Comparator.reverseOrder()));
        return rows;
    }
}
