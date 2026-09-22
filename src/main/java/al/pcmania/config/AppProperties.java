package al.pcmania.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
        Admin admin) {

    public record Admin(String username, String password) {}

    /** Base URL without trailing slash. */
    public String base() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
