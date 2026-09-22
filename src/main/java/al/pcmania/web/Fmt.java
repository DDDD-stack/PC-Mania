package al.pcmania.web;

import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Formatting helpers exposed to templates as {@code @fmt}. */
@Component("fmt")
public class Fmt {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private static String grouped(Number n) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0", symbols).format(n);
    }

    /** 125000 -> "125.000 Lekë" */
    public static String lek(Number amount) {
        return amount == null ? "" : grouped(amount) + " Lekë";
    }

    public String price(Number amount) {
        return lek(amount);
    }

    public String num(Number n) {
        return n == null ? "" : grouped(n);
    }

    public String pct(double value) {
        return String.format("%.1f%%", value).replace('.', ',');
    }

    public String date(LocalDateTime t) {
        return t == null ? "" : DATE.format(t);
    }

    public String dateTime(LocalDateTime t) {
        return t == null ? "" : DATE_TIME.format(t);
    }

    public long daysSince(LocalDateTime t) {
        return t == null ? 0 : ChronoUnit.DAYS.between(t, LocalDateTime.now());
    }

    public String warranty(Integer days) {
        if (days == null || days <= 0) return null;
        if (days % 365 == 0) return days / 365 == 1 ? "1 vit garanci" : days / 365 + " vite garanci";
        if (days >= 30 && days % 30 == 0) return days / 30 == 1 ? "1 muaj garanci" : days / 30 + " muaj garanci";
        return days + " ditë garanci";
    }
}
