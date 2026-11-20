package al.pcmania.web.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class Deals {

    static final int URGENT_DAYS = 3;

    private Deals() {}

    public static String urgency(LocalDateTime endsAt) {
        return urgency(endsAt, LocalDateTime.now());
    }

    static String urgency(LocalDateTime endsAt, LocalDateTime now) {
        if (endsAt == null || !endsAt.isAfter(now)) return null;
        LocalDate lastDay = endsAt.minusSeconds(1).toLocalDate();
        long days = ChronoUnit.DAYS.between(now.toLocalDate(), lastDay);
        if (days <= 0) return "Mbaron sot";
        if (days == 1) return "Mbaron nesër";
        if (days <= URGENT_DAYS) return "Edhe " + days + " ditë";
        return null;
    }

    public static long epochMillis(LocalDateTime t) {
        return t == null ? 0 : t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static int percent(int regularPriceLek, int priceLek) {
        return regularPriceLek <= 0 ? 0 : (int) Math.round((regularPriceLek - priceLek) * 100.0 / regularPriceLek);
    }
}
