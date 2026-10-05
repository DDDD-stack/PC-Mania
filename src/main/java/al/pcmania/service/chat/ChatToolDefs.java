package al.pcmania.service.chat;

import al.pcmania.domain.Enums.GpuVendor;
import al.pcmania.service.chat.ChatModel.ToolDef;
import al.pcmania.service.chat.ChatTools.StockItem;
import al.pcmania.service.chat.ChatTools.UpgradeOption;
import al.pcmania.service.chat.ChatTools.UseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The tools as the model sees them (name, description, JSON Schema) and the dispatch from a tool call
 * to {@link ChatTools}. A call's result is JSON; the product slugs it surfaced come back beside it so the
 * reply can show them as cards.
 */
@Component
@RequiredArgsConstructor
public class ChatToolDefs {

    /** What running one tool call produced. */
    public record Execution(String resultJson, boolean error, List<String> productSlugs) {}

    private final ChatTools tools;
    private final ObjectMapper json;

    public List<ToolDef> definitions() {
        return List.of(
                new ToolDef("searchStock",
                        "Graphics cards in stock right now, fastest first. Every filter is optional. Returns slug, title, priceLek, "
                                + "condition, warrantyDays, vramGb, tier (1-20 performance rank), psuMinWatts, pcieConnectors, testNotes, "
                                + "isMiningFree and typical fps. Call it before saying anything about availability.",
                        schema(Map.of(
                                "budgetMinLek", prop("integer", "Lowest price the customer will consider, in Lekë"),
                                "budgetMaxLek", prop("integer", "Highest price the customer will pay, in Lekë"),
                                "useCase", enumProp(List.of("ESPORTS_1080P", "AAA_1080P", "AAA_1440P"), "What they play; AAA_1440P drops cards too slow for it"),
                                "minVramGb", prop("integer", "Minimum VRAM in GB"),
                                "maxPsuWatts", prop("integer", "The customer's power supply in watts; cards needing more are left out"),
                                "vendor", enumProp(List.of("NVIDIA", "AMD", "INTEL"), "Only this vendor")), List.of())),
                new ToolDef("getProduct",
                        "One product by slug, in full: price, condition, stock, warranty, specs, test notes and its catalogue data.",
                        schema(Map.of("slug", prop("string", "The product slug from searchStock")), List.of("slug"))),
                new ToolDef("compareProducts",
                        "Two products side by side with tierDelta (B minus A) and the approximate performance gap in percent, positive when B is faster.",
                        schema(Map.of("slugA", prop("string", "First product slug"), "slugB", prop("string", "Second product slug")), List.of("slugA", "slugB"))),
                new ToolDef("recommendUpgrade",
                        "What in stock is a real step up from the customer's current card. Resolves the card name, then returns only "
                                + "higher-tier cards their power supply can run, each with tierDelta and the performance gap. "
                                + "Read the note: it says when nothing in stock is worth the upgrade.",
                        schema(Map.of(
                                "currentCardQuery", prop("string", "The customer's current card as they wrote it, e.g. 'gtx 1660 super'"),
                                "psuWatts", prop("integer", "Their power supply in watts, if known"),
                                "budgetLek", prop("integer", "Their budget in Lekë, if known")), List.of("currentCardQuery"))),
                new ToolDef("checkFit",
                        "Whether a product works with the customer's power supply and fits their case. Verdict OK, TIGHT or NO_FIT with reasons.",
                        schema(Map.of(
                                "slug", prop("string", "The product slug"),
                                "psuWatts", prop("integer", "Their power supply in watts"),
                                "caseLengthMm", prop("integer", "The longest card their case takes, in mm")), List.of("slug"))),
                new ToolDef("createLead",
                        "Record a customer to call back when nothing in stock fits. Ask for their name and phone and get their agreement first.",
                        schema(Map.of(
                                "name", prop("string", "Customer's name"),
                                "phone", prop("string", "Customer's phone number"),
                                "wantedItem", prop("string", "What they are looking for, in their words"),
                                "budgetLek", prop("integer", "Budget in Lekë, if given"),
                                "psuWatts", prop("integer", "Their power supply in watts, if given"),
                                "notes", prop("string", "Anything else useful for the call")), List.of("name", "phone", "wantedItem"))));
    }

    /** What the customer sees while a tool runs; null for tools that are instant. */
    public static String statusOf(String toolName) {
        return switch (toolName) {
            case "searchStock" -> "Po shikoj stokun…";
            case "getProduct" -> "Po lexoj produktin…";
            case "compareProducts" -> "Po krahasoj…";
            case "recommendUpgrade" -> "Po kërkoj një hap përpara…";
            case "checkFit" -> "Po kontrolloj përputhshmërinë…";
            case "createLead" -> "Po ruaj të dhënat…";
            default -> null;
        };
    }

    /** Runs one call. Bad input never throws: the model gets a message it can act on. */
    public Execution execute(String name, JsonNode in, Long sessionId) {
        try {
            return switch (name) {
                case "searchStock" -> {
                    List<StockItem> items = tools.searchStock(intOf(in, "budgetMinLek"), intOf(in, "budgetMaxLek"),
                            enumOf(in, "useCase", UseCase.class), intOf(in, "minVramGb"), intOf(in, "maxPsuWatts"),
                            enumOf(in, "vendor", GpuVendor.class));
                    yield ok(items.isEmpty() ? Map.of("items", items, "note", "Asnjë kartë në stok nuk përputhet me këto kritere.") : Map.of("items", items),
                            items.stream().map(StockItem::slug).toList());
                }
                case "getProduct" -> {
                    Optional<ChatTools.ProductInfo> p = tools.getProduct(str(in, "slug"));
                    yield p.map(info -> ok(info, List.of(info.slug())))
                            .orElseGet(() -> fail("Nuk ka produkt me këtë slug."));
                }
                case "compareProducts" -> {
                    Optional<ChatTools.Comparison> c = tools.compareProducts(str(in, "slugA"), str(in, "slugB"));
                    yield c.map(cmp -> ok(cmp, List.of(cmp.a().slug(), cmp.b().slug())))
                            .orElseGet(() -> fail("Njëri nga produktet nuk ekziston."));
                }
                case "recommendUpgrade" -> {
                    String q = str(in, "currentCardQuery");
                    if (q == null) yield fail("currentCardQuery mungon.");
                    ChatTools.UpgradeAdvice a = tools.recommendUpgrade(q, intOf(in, "psuWatts"), intOf(in, "budgetLek"));
                    yield ok(a, a.options().stream().map(UpgradeOption::product).map(StockItem::slug).toList());
                }
                case "checkFit" -> {
                    Optional<ChatTools.FitCheck> f = tools.checkFit(str(in, "slug"), intOf(in, "psuWatts"), intOf(in, "caseLengthMm"));
                    yield f.map(fit -> ok(fit, List.of(fit.slug())))
                            .orElseGet(() -> fail("Nuk ka produkt me këtë slug."));
                }
                case "createLead" -> {
                    ChatTools.LeadResult r = tools.createLead(sessionId, str(in, "name"), str(in, "phone"), str(in, "wantedItem"),
                            intOf(in, "budgetLek"), intOf(in, "psuWatts"), str(in, "notes"));
                    yield new Execution(write(r), !r.ok(), List.of());
                }
                default -> fail("Mjet i panjohur: " + name);
            };
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    private Execution ok(Object result, List<String> slugs) {
        return new Execution(write(result), false, slugs);
    }

    private Execution fail(String message) {
        return new Execution(write(Map.of("error", message)), true, List.of());
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- Schema helpers ----

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", properties);
        s.put("required", required);
        return s;
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static Map<String, Object> enumProp(List<String> values, String description) {
        return Map.of("type", "string", "enum", new ArrayList<>(values), "description", description);
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
