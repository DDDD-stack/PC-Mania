package al.pcmania.web.view;

import al.pcmania.domain.Enums.Condition;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * Category listing filters, bound from query params:
 * min, max, gjendja (condition), marka (brand slug), spec ("Key:Value"), rendit (sort), faqe (1-based page).
 */
public record CatalogFilter(
        Integer min,
        Integer max,
        Set<Condition> conditions,
        Set<String> brands,
        Map<String, Set<String>> specs,
        /** Only products that accept a trade-in ("Nderrim"). */
        boolean trade,
        Sort sort,
        int page) {

    public enum Sort {
        TE_REJAT("te-rejat", "Më të rejat"), CMIMI_ASC("cmimi-rritje", "Çmimi: nga më i ulëti"), CMIMI_DESC("cmimi-zbritje", "Çmimi: nga më i larti");
        public final String param;
        public final String label;
        Sort(String param, String label) { this.param = param; this.label = label; }
        public String getParam() { return param; }
        public String getLabel() { return label; }

        static Sort of(String param) {
            return Arrays.stream(values()).filter(s -> s.param.equals(param)).findFirst().orElse(TE_REJAT);
        }
    }

    public static CatalogFilter of(Integer min, Integer max, List<String> conditions, List<String> brands,
                                   List<String> specs, Boolean trade, String sort, Integer page) {
        Set<Condition> conds = EnumSet.noneOf(Condition.class);
        if (conditions != null) conditions.forEach(c -> {
            try { conds.add(Condition.valueOf(c)); } catch (IllegalArgumentException ignored) {}
        });
        Map<String, Set<String>> specMap = new LinkedHashMap<>();
        if (specs != null) for (String s : specs) {
            int i = s.indexOf(':');
            if (i > 0 && i < s.length() - 1) specMap.computeIfAbsent(s.substring(0, i), k -> new LinkedHashSet<>()).add(s.substring(i + 1));
        }
        return new CatalogFilter(positive(min), positive(max), conds,
                brands == null ? Set.of() : new LinkedHashSet<>(brands), specMap, Boolean.TRUE.equals(trade), Sort.of(sort),
                page == null || page < 1 ? 1 : page);
    }

    private static Integer positive(Integer n) {
        return n == null || n <= 0 ? null : n;
    }

    public boolean hasFilters() {
        return min != null || max != null || !conditions.isEmpty() || trade || !brands.isEmpty() || !specs.isEmpty();
    }

    public boolean hasSpec(String key, String value) {
        return specs.getOrDefault(key, Set.of()).contains(value);
    }

    public boolean hasCondition(Condition c) {
        return conditions.contains(c);
    }

    public int activeCount() {
        return (min != null || max != null ? 1 : 0) + conditions.size() + (trade ? 1 : 0) + brands.size()
                + specs.values().stream().mapToInt(Set::size).sum();
    }

    /** Relative URL for the same filters on another page. */
    public String pageUrl(String path, int page) {
        MultiValueMap<String, String> q = params(true);
        if (page > 1) q.add("faqe", String.valueOf(page));
        return UriComponentsBuilder.fromPath(path).queryParams(q).encode().build().toUriString();
    }

    private MultiValueMap<String, String> params(boolean includeSort) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        if (min != null) q.add("min", min.toString());
        if (max != null) q.add("max", max.toString());
        conditions.forEach(c -> q.add("gjendja", c.name()));
        if (trade) q.add("nderrim", "true");
        brands.stream().filter(StringUtils::hasText).forEach(b -> q.add("marka", b));
        specs.forEach((k, vs) -> vs.forEach(v -> q.add("spec", k + ":" + v)));
        if (includeSort && sort != Sort.TE_REJAT) q.add("rendit", sort.param);
        return q;
    }
}
