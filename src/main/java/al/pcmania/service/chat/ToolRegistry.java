package al.pcmania.service.chat;

import al.pcmania.domain.Enums.GpuVendor;
import al.pcmania.service.chat.ChatTools.*;
import al.pcmania.service.chat.ToolDef.ParamSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The six tools, defined once as neutral {@link ToolDef}s over {@link ChatTools}, and the dispatch from
 * a model's call to a result. None takes or returns a name, a phone number or an address: when the
 * assistant wants to take a request it calls {@code requestContactForm}, which only returns a signal
 * for the site to show its own form.
 */
@Component
@Slf4j
public class ToolRegistry {

    /** Every tool answers with JSON; the slugs it surfaced and any UI action ride beside it. */
    public record Execution(String name, String resultJson, boolean error, List<String> productSlugs, JsonNode action) {}

    /** What the customer sees while a tool runs. */
    public static String statusOf(String toolName) {
        return switch (toolName) {
            case "searchStock" -> "Po shikoj stokun…";
            case "getProduct" -> "Po lexoj produktin…";
            case "compareProducts" -> "Po krahasoj…";
            case "recommendUpgrade" -> "Po kërkoj një hap përpara…";
            case "checkFit" -> "Po kontrolloj përputhshmërinë…";
            default -> null;
        };
    }

    private final ChatTools tools;
    private final ObjectMapper json;
    private final Map<String, ToolDef> defs = new LinkedHashMap<>();

    public ToolRegistry(ChatTools tools, ObjectMapper json) {
        this.tools = tools;
        this.json = json;
        for (ToolDef d : build()) defs.put(d.name(), d);
    }

    public List<ToolDef> definitions() {
        return List.copyOf(defs.values());
    }

    public Optional<ToolDef> get(String name) {
        return Optional.ofNullable(defs.get(name));
    }

    /** Runs one call. Bad input never throws: the model gets a message it can act on. */
    public Execution execute(String name, JsonNode args) {
        ToolDef def = defs.get(name);
        if (def == null) return fail(name, "Mjet i panjohur: " + name);
        try {
            Object result = def.fn().apply(args == null ? json.createObjectNode() : args);
            if (result instanceof Error e) return fail(name, e.error());
            JsonNode action = result instanceof LeadFormSignal ? json.valueToTree(result) : null;
            return new Execution(name, write(result), false, slugsOf(result), action);
        } catch (IllegalArgumentException e) {
            return fail(name, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Tool {} failed", name, e);
            return fail(name, "Mjeti dështoi. Thuaji klientit të provojë përsëri ose të na shkruajë në WhatsApp.");
        }
    }

    private List<ToolDef> build() {
        Map<String, ParamSpec> search = new LinkedHashMap<>();
        search.put("budgetMinLek", ParamSpec.integer("Lowest price the customer will consider, in Lekë"));
        search.put("budgetMaxLek", ParamSpec.integer("Highest price the customer will pay, in Lekë"));
        search.put("useCase", ParamSpec.oneOf("What they play; AAA_1440P drops cards too slow for it", List.of("ESPORTS_1080P", "AAA_1080P", "AAA_1440P")));
        search.put("minVramGb", ParamSpec.integer("Minimum VRAM in GB"));
        search.put("maxPsuWatts", ParamSpec.integer("The customer's power supply in watts; cards needing more are left out"));
        search.put("vendor", ParamSpec.oneOf("Only this vendor", List.of("NVIDIA", "AMD", "INTEL")));

        Map<String, ParamSpec> product = new LinkedHashMap<>();
        product.put("slug", ParamSpec.requiredString("The product slug from searchStock"));

        Map<String, ParamSpec> compare = new LinkedHashMap<>();
        compare.put("slugA", ParamSpec.requiredString("First product slug"));
        compare.put("slugB", ParamSpec.requiredString("Second product slug"));

        Map<String, ParamSpec> upgrade = new LinkedHashMap<>();
        upgrade.put("currentCardQuery", ParamSpec.requiredString("The customer's current card as they wrote it, e.g. 'gtx 1660 super'"));
        upgrade.put("psuWatts", ParamSpec.integer("Their power supply in watts, if known"));
        upgrade.put("budgetLek", ParamSpec.integer("Their budget in Lekë, if known"));

        Map<String, ParamSpec> fit = new LinkedHashMap<>();
        fit.put("slug", ParamSpec.requiredString("The product slug"));
        fit.put("psuWatts", ParamSpec.integer("Their power supply in watts"));
        fit.put("caseLengthMm", ParamSpec.integer("The longest card their case takes, in mm"));

        Map<String, ParamSpec> contact = new LinkedHashMap<>();
        contact.put("wantedItem", ParamSpec.requiredString("What the customer is looking for, in their words (no personal details)"));
        contact.put("budgetLek", ParamSpec.integer("Budget in Lekë, if given"));
        contact.put("psuWatts", ParamSpec.integer("Their power supply in watts, if given"));

        return List.of(
                new ToolDef("searchStock",
                        "Graphics cards in stock right now, fastest first, at most 6. Every filter is optional. Returns slug, title, "
                                + "priceLek, condition, warrantyDays, vramGb, tier (1-20 performance rank), psuMinWatts, pcieConnectors, "
                                + "lengthMm, testNotes, isMiningFree and typical fps. Call it before saying anything about availability.",
                        search, in -> {
                            List<StockItem> items = tools.searchStock(intOf(in, "budgetMinLek"), intOf(in, "budgetMaxLek"),
                                    enumOf(in, "useCase", UseCase.class), intOf(in, "minVramGb"), intOf(in, "maxPsuWatts"),
                                    enumOf(in, "vendor", GpuVendor.class));
                            return new StockSearch(items, items.isEmpty() ? "Asnjë kartë në stok nuk përputhet me këto kritere." : null);
                        }),
                new ToolDef("getProduct",
                        "One product by slug, in full: price, condition, stock, warranty, specs, test notes and its catalogue data.",
                        product, in -> tools.getProduct(str(in, "slug")).<Object>map(p -> p).orElse(new Error("Nuk ka produkt me këtë slug."))),
                new ToolDef("compareProducts",
                        "Two products side by side with tierDelta (B minus A) and the approximate performance gap in percent, positive when B is faster.",
                        compare, in -> tools.compareProducts(str(in, "slugA"), str(in, "slugB")).<Object>map(c -> c)
                                .orElse(new Error("Njëri nga produktet nuk ekziston."))),
                new ToolDef("recommendUpgrade",
                        "What in stock is a real step up from the customer's current card. Resolves the card name, then returns only "
                                + "higher-tier cards their power supply can run, each with tierDelta and the performance gap. "
                                + "Read the note: it says when nothing in stock is worth the upgrade.",
                        upgrade, in -> {
                            String q = str(in, "currentCardQuery");
                            if (q == null) return new Error("currentCardQuery mungon.");
                            return tools.recommendUpgrade(q, intOf(in, "psuWatts"), intOf(in, "budgetLek"));
                        }),
                new ToolDef("checkFit",
                        "Whether a product works with the customer's power supply and fits their case. Verdict OK, TIGHT or NO_FIT with reasons.",
                        fit, in -> tools.checkFit(str(in, "slug"), intOf(in, "psuWatts"), intOf(in, "caseLengthMm")).<Object>map(f -> f)
                                .orElse(new Error("Nuk ka produkt me këtë slug."))),
                new ToolDef("requestContactForm",
                        "When nothing in stock fits, ask the site to show its contact form so the shop can call the customer back. "
                                + "Pass only what they are looking for; never a name, phone number or address. Returns a signal; "
                                + "tell the customer the form is below and that the shop will call.",
                        contact, in -> {
                            String wanted = str(in, "wantedItem");
                            if (wanted == null) return new Error("wantedItem mungon.");
                            return tools.requestContactForm(wanted, intOf(in, "budgetLek"), intOf(in, "psuWatts"));
                        }));
    }

    /** A tool's own validation message, returned to the model as an error result. */
    record Error(String error) {}

    private Execution fail(String name, String message) {
        return new Execution(name, write(Map.of("error", message)), true, List.of(), null);
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The products a result talks about, so the reply can show them as cards. */
    static List<String> slugsOf(Object result) {
        List<String> out = new ArrayList<>();
        if (result instanceof StockSearch s) s.items().forEach(i -> out.add(i.slug()));
        else if (result instanceof ProductInfo p) out.add(p.slug());
        else if (result instanceof Comparison c) {
            out.add(c.a().slug());
            out.add(c.b().slug());
        } else if (result instanceof UpgradeAdvice a) a.options().forEach(o -> out.add(o.product().slug()));
        else if (result instanceof FitCheck f) out.add(f.slug());
        return out;
    }

    // ---- Input helpers: tolerant of the model sending a number as a string or an empty value ----

    static Integer intOf(JsonNode in, String field) {
        JsonNode v = in == null ? null : in.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isNumber()) return v.intValue();
        String s = v.asText().replaceAll("[^0-9-]", "");
        if (s.isEmpty() || s.equals("-")) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " nuk është numër.");
        }
    }

    static String str(JsonNode in, String field) {
        JsonNode v = in == null ? null : in.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    static <E extends Enum<E>> E enumOf(JsonNode in, String field, Class<E> type) {
        String s = str(in, field);
        if (s == null) return null;
        try {
            return Enum.valueOf(type, s.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null; // an unknown value just does not filter
        }
    }
}
