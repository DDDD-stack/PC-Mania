package al.pcmania.service;

import al.pcmania.domain.Combo;
import al.pcmania.domain.ComboItem;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;
import al.pcmania.domain.ProductSpec;
import al.pcmania.repo.ComboRepository;
import al.pcmania.repo.ProductRepository;
import al.pcmania.web.Fmt;
import al.pcmania.web.view.ComboView;
import al.pcmania.web.view.ProductCard;
import al.pcmania.web.view.ProductDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComboService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public record Line(Long productId, int quantity) {}

    public record ComboForm(String titleSq, String subtitleSq, String slug, Integer comboPriceLek, boolean active,
                            Integer sortOrder, LocalDateTime startsAt, LocalDateTime endsAt, List<Line> items) {}

    public record AdminRow(Combo combo, boolean visible, List<String> reasons, int originalTotalLek) {

        public String state() {
            return visible ? "I dukshëm" : "I fshehur: " + String.join("; ", reasons);
        }
    }

    private final ComboRepository combos;
    private final ProductRepository products;
    private final CatalogService catalog;
    private final FieldTemplateService templates;

    public List<ComboView> visible(int limit) {
        return combos.findVisible(LocalDateTime.now(), PageRequest.of(0, limit)).stream().map(c -> view(c, true)).toList();
    }

    public List<Combo> visibleEntities() {
        return combos.findVisible(LocalDateTime.now(), PageRequest.of(0, 500));
    }

    public Optional<ComboView> bySlug(String slug) {
        return combos.findBySlug(slug).map(c -> view(c, combos.isVisible(c.getId(), LocalDateTime.now())));
    }

    public boolean isVisible(Long id) {
        return combos.isVisible(id, LocalDateTime.now());
    }

    private ComboView view(Combo c, boolean visible) {
        List<Product> members = c.getItems().stream().map(ComboItem::getProduct).toList();
        Map<Long, ProductCard> cards = new HashMap<>();
        List<ProductCard> built = catalog.cards(members);
        for (int i = 0; i < members.size(); i++) cards.put(members.get(i).getId(), built.get(i));
        List<ComboView.Member> out = new ArrayList<>();
        for (ComboItem item : c.getItems()) {
            Product p = item.getProduct();
            FieldTemplateService.Template t = templates.forCategory(p.getCategorySlug());
            List<ProductDetail.Spec> specs = new ArrayList<>();
            for (ProductSpec s : p.getSpecs()) specs.add(new ProductDetail.Spec(t.label(s.getSpecKey()), t.display(s.getSpecKey(), s.getSpecValue())));
            out.add(new ComboView.Member(cards.get(p.getId()), item.getQuantity(), specs, p.getShortDescription()));
        }
        return new ComboView(c.getId(), c.getSlug(), c.getTitleSq(), c.getSubtitleSq(), c.getComboPriceLek(),
                c.originalTotalLek(), visible, out);
    }

    public List<AdminRow> adminRows() {
        LocalDateTime now = LocalDateTime.now();
        Set<Long> visibleIds = new HashSet<>();
        combos.findVisible(now, PageRequest.of(0, 500)).forEach(c -> visibleIds.add(c.getId()));
        return combos.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(c -> new AdminRow(c, visibleIds.contains(c.getId()), reasons(c, now), c.originalTotalLek()))
                .toList();
    }

    public Combo get(Long id) {
        return combos.findById(id).orElseThrow(NotFoundException::new);
    }

    public List<String> reasons(Combo c) {
        return reasons(c, LocalDateTime.now());
    }

    static List<String> reasons(Combo c, LocalDateTime now) {
        List<String> r = new ArrayList<>();
        if (!c.isActive()) r.add("paketa është e çaktivizuar");
        if (c.getStartsAt() != null && c.getStartsAt().isAfter(now)) r.add("fillon më " + WHEN.format(c.getStartsAt()));
        if (c.getEndsAt() != null && !c.getEndsAt().isAfter(now)) r.add("përfundoi më " + WHEN.format(c.getEndsAt()));
        if (c.getItems().isEmpty()) r.add("paketa nuk ka produkte");
        for (ComboItem i : c.getItems()) {
            Product p = i.getProduct();
            String name = p.getTitle();
            switch (p.getStatus()) {
                case SOLD -> r.add(name + " është SHITUR");
                case RESERVED -> r.add(name + " është REZERVUAR");
                case HIDDEN -> r.add(name + " është I FSHEHUR");
                case DRAFT -> r.add(name + " është ende DRAFT");
                case ACTIVE -> {
                    if (p.getQuantity() < i.getQuantity()) {
                        r.add(name + (p.getQuantity() == 0 ? " nuk ka stok" : " ka vetëm " + p.getQuantity() + " copë (duhen " + i.getQuantity() + ")"));
                    }
                }
            }
        }
        return r;
    }

    @Transactional
    public Combo save(Long id, ComboForm form) {
        Combo c = id == null ? new Combo() : get(id);
        if (!StringUtils.hasText(form.titleSq())) throw new IllegalArgumentException("Shkruani titullin e paketës.");
        if (form.comboPriceLek() == null || form.comboPriceLek() <= 0) throw new IllegalArgumentException("Shkruani çmimin e paketës.");
        if (form.startsAt() != null && form.endsAt() != null && !form.endsAt().isAfter(form.startsAt())) {
            throw new IllegalArgumentException("Data e mbarimit duhet të jetë pas datës së fillimit.");
        }

        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (Line l : form.items() == null ? List.<Line>of() : form.items()) {
            if (l.productId() == null) continue;
            int q = l.quantity() < 1 ? 1 : l.quantity();
            wanted.merge(l.productId(), q, Integer::sum);
        }
        if (wanted.values().stream().mapToInt(Integer::intValue).sum() < 2) {
            throw new IllegalArgumentException("Një paketë ka nevojë për të paktën dy produkte.");
        }
        Map<Long, Product> found = new HashMap<>();
        products.findAllById(wanted.keySet()).forEach(p -> found.put(p.getId(), p));
        if (found.size() != wanted.size()) throw new IllegalArgumentException("Një nga produktet nuk ekziston më.");
        int original = wanted.entrySet().stream().mapToInt(e -> found.get(e.getKey()).getPriceLek() * e.getValue()).sum();
        if (form.comboPriceLek() >= original) {
            throw new IllegalArgumentException("Çmimi i paketës (" + Fmt.lek(form.comboPriceLek()) + ") duhet të jetë më i ulët se totali i produkteve veç e veç ("
                    + Fmt.lek(original) + "), përndryshe nuk është ofertë.");
        }

        c.setTitleSq(form.titleSq().trim());
        c.setSubtitleSq(StringUtils.hasText(form.subtitleSq()) ? form.subtitleSq().trim() : null);
        c.setComboPriceLek(form.comboPriceLek());
        c.setActive(form.active());
        c.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());
        c.setStartsAt(form.startsAt());
        c.setEndsAt(form.endsAt());
        String wantedSlug = StringUtils.hasText(form.slug()) ? Slugs.of(form.slug()) : (c.getSlug() != null ? c.getSlug() : Slugs.of(form.titleSq()));
        if (!wantedSlug.equals(c.getSlug())) c.setSlug(uniqueSlug(wantedSlug));

        c.getItems().removeIf(i -> !wanted.containsKey(i.getProduct().getId()));
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            Optional<ComboItem> existing = c.getItems().stream().filter(i -> i.getProduct().getId().equals(e.getKey())).findFirst();
            if (existing.isPresent()) existing.get().setQuantity(e.getValue());
            else c.getItems().add(new ComboItem(c, found.get(e.getKey()), e.getValue()));
        }
        return combos.save(c);
    }

    @Transactional
    public void delete(Long id) {
        combos.delete(get(id));
    }

    public List<Product> pickableProducts(Combo current) {
        List<Product> out = new ArrayList<>(products.findByStatusNotOrderByTitleAsc(ProductStatus.SOLD));
        if (current != null) {
            Set<Long> ids = new HashSet<>();
            out.forEach(p -> ids.add(p.getId()));
            current.getItems().stream().map(ComboItem::getProduct).filter(p -> ids.add(p.getId())).forEach(out::add);
        }
        return out;
    }

    public record Priced(int index, int quantity, int unitPriceLek) {}

    public static List<Priced> allocate(int[] unitPrices, int[] quantities, int total) {
        long original = 0;
        for (int i = 0; i < unitPrices.length; i++) original += (long) unitPrices[i] * quantities[i];
        int[] units = new int[unitPrices.length];
        long sum = 0;
        for (int i = 0; i < unitPrices.length; i++) {
            units[i] = original == 0 ? 0 : (int) ((long) unitPrices[i] * total / original);
            sum += (long) units[i] * quantities[i];
        }
        int remainder = (int) (total - sum);
        int single = -1, priciest = 0;
        for (int i = 0; i < units.length; i++) {
            if (quantities[i] == 1 && (single < 0 || unitPrices[i] > unitPrices[single])) single = i;
            if (unitPrices[i] > unitPrices[priciest]) priciest = i;
        }
        List<Priced> out = new ArrayList<>();
        for (int i = 0; i < units.length; i++) {
            if (remainder == 0 || (single >= 0 && i != single) || (single < 0 && i != priciest)) {
                out.add(new Priced(i, quantities[i], units[i]));
            } else if (single >= 0) {
                out.add(new Priced(i, 1, units[i] + remainder));
            } else {
                out.add(new Priced(i, quantities[i] - 1, units[i]));
                out.add(new Priced(i, 1, units[i] + remainder));
            }
        }
        return out;
    }

    private String uniqueSlug(String base) {
        if (!combos.existsBySlug(base)) return base;
        for (int i = 2; ; i++) {
            if (!combos.existsBySlug(base + "-" + i)) return base + "-" + i;
        }
    }
}
