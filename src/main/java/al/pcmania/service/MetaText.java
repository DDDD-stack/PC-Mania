package al.pcmania.service;

import org.jsoup.Jsoup;
import org.springframework.util.StringUtils;

import java.util.List;

public final class MetaText {

    public static final int TITLE_MAX = 60;
    public static final int DESCRIPTION_MAX = 155;

    private static final int MIN_TAIL = 40;

    private MetaText() {}

    public static String plain(String text) {
        if (!StringUtils.hasText(text)) return "";
        return Jsoup.parse(text).text().replaceAll("\\s+", " ").trim();
    }

    public static String cap(String text, int max) {
        String t = plain(text);
        if (t.length() <= max) return t;
        String head = t.substring(0, max + 1);
        int end = Math.max(head.lastIndexOf(". "), Math.max(head.lastIndexOf("! "), head.lastIndexOf("? ")));
        if (end >= max / 2) return t.substring(0, end + 1);
        int space = t.lastIndexOf(' ', max - 1);
        String words = (space > 0 ? t.substring(0, space) : t.substring(0, max - 1))
                .replaceAll("[\\s,;:·(\\-–—]+$", "");
        return words.matches(".*[.!?]$") ? words : words + ".";
    }

    public static String fit(List<String> parts, int max) {
        StringBuilder out = new StringBuilder();
        for (String raw : parts) {
            String part = plain(raw);
            if (part.isEmpty()) continue;
            if (!part.matches(".*[.!?]$")) part = part + ".";
            int room = max - out.length() - (out.isEmpty() ? 0 : 1);
            if (part.length() > room) {
                if (!out.isEmpty() && room < MIN_TAIL) continue;
                part = cap(part, room);
            }
            if (!out.isEmpty()) out.append(' ');
            out.append(part);
        }
        return out.toString();
    }
}
