package al.pcmania.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class Links {

    private Links() {}

    /** wa.me link to a customer's Albanian phone number (06x… becomes 3556x…), or null if the number is unusable. */
    public static String whatsappTo(String phone, String text) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 8) return null;
        if (digits.startsWith("00")) digits = digits.substring(2);
        else if (digits.startsWith("0")) digits = "355" + digits.substring(1);
        String url = "https://wa.me/" + digits;
        return text == null ? url : url + "?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
