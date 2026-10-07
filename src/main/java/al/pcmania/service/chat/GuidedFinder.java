package al.pcmania.service.chat;

import al.pcmania.domain.Enums.GpuVendor;
import al.pcmania.service.chat.ChatTools.StockItem;
import al.pcmania.service.chat.ChatTools.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GuidedFinder {

    public record Option(String value, String label) {}

    public record Step(String key, int number, int total, String question, String kind, List<Option> options,
                       Integer min, Integer max, Integer stepSize, Integer defaultValue, Map<String, String> answers,
                       boolean done, List<StockItem> results, String resultNote, boolean showLeadForm, String wantedItem,
                       Integer budgetLek, Integer psuWatts) {}

    private static final int TOTAL = 4;

    private final ChatTools tools;

    public Step step(Map<String, String> raw) {
        Map<String, String> answers = new java.util.LinkedHashMap<>();
        String use = pick(raw.get("use"), "esports", "aaa", "work");
        String res = pick(raw.get("res"), "1080p", "1440p");
        Integer budget = budget(raw.get("budget"));
        String psu = pick(raw.get("psu"), "450", "550", "650", "unknown");
        if (use != null) answers.put("use", use);
        if (res != null) answers.put("res", res);
        if (budget != null) answers.put("budget", String.valueOf(budget));
        if (psu != null) answers.put("psu", psu);

        if (use == null) return question("use", 1, "Çfarë luan më shumë?", List.of(
                new Option("esports", "Esports (CS2, Valorant, LoL, Fortnite)"),
                new Option("aaa", "Lojëra AAA (Cyberpunk, GTA, Call of Duty…)"),
                new Option("work", "Punë / edit video / AI")), answers);
        if (res == null) return question("res", 2, "Në çfarë rezolucioni?", List.of(
                new Option("1080p", "1080p (Full HD)"),
                new Option("1440p", "1440p (2K)")), answers);
        if (budget == null) return new Step("budget", 3, TOTAL, "Sa është buxheti?", "range", List.of(),
                80_000, 300_000, 10_000, 150_000, answers, false, List.of(), null, false, null, null, null);
        if (psu == null) return question("psu", 4, "Çfarë furnizuesi (PSU) ke?", List.of(
                new Option("450", "450 W"), new Option("550", "550 W"), new Option("650", "650 W ose më shumë"),
                new Option("unknown", "Nuk e di")), answers);

        UseCase useCase = switch (use) {
            case "esports" -> UseCase.ESPORTS_1080P;
            case "aaa" -> res.equals("1440p") ? UseCase.AAA_1440P : UseCase.AAA_1080P;
            default -> null;
        };
        Integer minVram = use.equals("work") ? 8 : null;
        Integer maxPsu = psu.equals("unknown") || psu.equals("650") ? null : Integer.valueOf(psu);
        List<StockItem> results = tools.searchStock(null, budget, useCase, minVram, maxPsu, (GpuVendor) null);
        String wanted = "Kartë grafike për " + label(use) + " në " + res + ", buxhet " + (budget / 1000) + " mijë Lekë"
                + (maxPsu != null ? ", PSU " + maxPsu + " W" : "");
        String note = results.isEmpty()
                ? "Asnjë kartë në stok nuk përputhet me këto kritere. Lër të dhënat dhe të telefonojmë kur të kemi diçka."
                : psu.equals("unknown")
                ? "Kontrollo furnizuesin (PSU) para se të porositësh: çdo kartë tregon minimumin e saj."
                : "Të gjitha punojnë me PSU-në tënde. Hap kartën për fotot dhe shënimet e testit.";
        return new Step(null, TOTAL, TOTAL, null, "results", List.of(), null, null, null, null, answers, true,
                results, note, results.isEmpty(), wanted, budget, maxPsu);
    }

    private static Step question(String key, int number, String q, List<Option> options, Map<String, String> answers) {
        return new Step(key, number, TOTAL, q, "choice", options, null, null, null, null, answers, false, List.of(), null, false, null, null, null);
    }

    private static String pick(String value, String... allowed) {
        if (value == null) return null;
        for (String a : allowed) if (a.equals(value.trim().toLowerCase())) return a;
        return null;
    }

    private static Integer budget(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            int b = Integer.parseInt(value.replaceAll("[^0-9]", ""));
            return b < 1000 ? b * 1000 : Math.max(10_000, b);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String label(String use) {
        return switch (use) {
            case "esports" -> "esports";
            case "aaa" -> "lojëra AAA";
            default -> "punë / AI";
        };
    }

    public static List<String> keys() {
        return new ArrayList<>(List.of("use", "res", "budget", "psu"));
    }
}
