package al.pcmania.service;

import org.springframework.util.StringUtils;

public final class Gtin {

    private Gtin() {}

    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String digits = raw.replaceAll("[\\s-]", "");
        return digits.isEmpty() ? null : digits;
    }

    public static boolean isValid(String raw) {
        String g = normalize(raw);
        if (g == null) return true;
        if (!g.matches("\\d{8}|\\d{12,14}")) return false;
        int sum = 0;
        for (int i = g.length() - 2, weight = 3; i >= 0; i--, weight = 4 - weight) {
            sum += (g.charAt(i) - '0') * weight;
        }
        return (10 - sum % 10) % 10 == g.charAt(g.length() - 1) - '0';
    }
}
