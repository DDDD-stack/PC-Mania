package al.pcmania.service;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugs {

    private Slugs() {}

    /** "Kartë Grafike RTX 3060 12GB" -> "karte-grafike-rtx-3060-12gb" */
    public static String of(String text) {
        String s = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (s.length() > 200) s = s.substring(0, 200).replaceAll("-+$", "");
        return s.isEmpty() ? "produkt" : s;
    }
}
