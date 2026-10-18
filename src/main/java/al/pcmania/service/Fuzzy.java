package al.pcmania.service;

import java.util.List;
import java.util.Locale;

public final class Fuzzy {

    private Fuzzy() {}

    public static int score(List<String> names, String normalizedQuery) {
        String q = normalizedQuery;
        int best = 0;
        for (String raw : names) {
            String t = normalize(raw);
            if (t.isEmpty()) continue;
            int s;
            if (t.equals(q)) s = 1000;
            else if (t.startsWith(q)) s = 800 + q.length();
            else if (t.contains(q)) s = 600 + q.length();
            else if (t.length() >= 4 && q.contains(t)) {
                int end = q.indexOf(t) + t.length();
                s = 400 + Math.min(end, 40) * 4 + Math.min(t.length(), 30);
            } else s = 0;
            best = Math.max(best, s);
        }
        return best;
    }

    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString();
    }
}
