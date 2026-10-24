package al.pcmania.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Locale;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        String siteName,
        String uploadDir,
        String notifyEmail,
        String mailFrom,
        String whatsappNumber,
        String phoneDisplay,
        String contactEmail,
        String facebookUrl,
        int courierShippingLek,
        Admin admin,

        String mobileApiKey) {

    public record Admin(String username, String password) {}

    public String base() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String publicEmail() {
        if (contactEmail == null || contactEmail.isBlank()) return null;
        String email = contactEmail.trim();
        int at = email.lastIndexOf('@');
        String host;
        try {
            host = URI.create(base()).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (at < 0 || host == null) return null;
        String domain = email.substring(at + 1).toLowerCase(Locale.ROOT);
        String site = host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        return domain.equals(site) || site.endsWith("." + domain) ? email : null;
    }
}
