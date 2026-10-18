package al.pcmania.web.view;

import al.pcmania.domain.Enums.Condition;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

public record CatalogFilter(
        Integer min,
        Integer max,
        Set<Condition> conditions,
        Set<String> brands,
        Map<String, Set<String>> specs,

        boolean trade,
        Set<String> gpus,
        Set<Integer> rams,
        PriceBand band,
        Sort sort,
        int page) {

    public enum PriceBand {
        DERI_50("deri-50k", "Deri 50.000 Lekë", null, 50_000),
        NGA_50_80("50k-80k", "50.000 – 80.000 Lekë", 50_000, 80_000),
        NGA_80_120("80k-120k", "80.000 – 120.000 Lekë", 80_000, 120_000),
        MBI_120("mbi-120k", "Mbi 120.000 Lekë", 120_000, null);
        public final String param;
        public final String label;
        public final Integer min;
        public final Integer max;
        PriceBand(String param, String label, Integer min, Integer max) { this.param = param; this.label = label; this.min = min; this.max = max; }
        public String getParam() { return param; }
        public String getLabel() { return label; }

        static PriceBand of(String param) {
            return param == null ? null : Arrays.stream(values()).filter(b -> b.param.equals(param)).findFirst().orElse(null);
        }
    }

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
        return of(min, max, conditions, brands, specs, trade, null, null, null, sort, page);
    }

    public static CatalogFilter of(Integer min, Integer max, List<String> conditions, List<String> brands,
                                   List<String> specs, Boolean trade, List<String> gpus, List<Integer> rams, String band,
                                   String sort, Integer page) {
        Set<Condition> conds = EnumSet.noneOf(Condition.class);
        if (conditions != null) conditions.forEach(c -> {
            try { conds.add(Condition.valueOf(c)); } catch (IllegalArgumentException ignored) {}
        });
        Map<String, Set<String>> specMap = new LinkedHashMap<>();
        if (specs != null) for (String s : specs) {
            int i = s.indexOf(':');
            if (i > 0 && i < s.length() - 1) specMap.computeIfAbsent(s.substring(0, i), k -> new LinkedHashSet<>()).add(s.substring(i + 1));
        }
        Set<String> gpuSet = new LinkedHashSet<>();
        if (gpus != null) gpus.stream().filter(StringUtils::hasText).map(String::trim).forEach(gpuSet::add);
        Set<Integer> ramSet = new TreeSet<>();
        if (rams != null) rams.stream().filter(r -> r != null && r > 0).forEach(ramSet::add);
        return new CatalogFilter(positive(min), positive(max), conds,
                brands == null ? Set.of() : new LinkedHashSet<>(brands), specMap, Boolean.TRUE.equals(trade),
                gpuSet, ramSet, PriceBand.of(band), Sort.of(sort),
                page == null || page < 1 ? 1 : page);
    }

    private static Integer positive(Integer n) {
        return n == null || n <= 0 ? null : n;
    }

    public boolean hasFilters() {
        return min != null || max != null || !conditions.isEmpty() || trade || !brands.isEmpty() || !specs.isEmpty()
                || !gpus.isEmpty() || !rams.isEmpty() || band != null;
    }

    public boolean hasGpu(String gpu) {
        return gpus.contains(gpu);
    }

    public boolean hasRam(Integer ram) {
        return rams.contains(ram);
    }

    public boolean hasBand(PriceBand b) {
        return band == b;
    }

    public boolean hasSpec(String key, String value) {
        return specs.getOrDefault(key, Set.of()).contains(value);
    }

    public boolean hasCondition(Condition c) {
        return conditions.contains(c);
    }

    public int activeCount() {
        return (min != null || max != null ? 1 : 0) + conditions.size() + (trade ? 1 : 0) + brands.size()
                + specs.values().stream().mapToInt(Set::size).sum() + gpus.size() + rams.size() + (band != null ? 1 : 0);
    }

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
        gpus.forEach(g -> q.add("gpu", g));
        rams.forEach(r -> q.add("ram", r.toString()));
        if (band != null) q.add("cmimi", band.param);
        if (includeSort && sort != Sort.TE_REJAT) q.add("rendit", sort.param);
        return q;
    }
}
