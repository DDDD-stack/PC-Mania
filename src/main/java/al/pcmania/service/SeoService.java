package al.pcmania.service;

import al.pcmania.config.AppProperties;
import al.pcmania.domain.Category;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.web.Fmt;
import al.pcmania.web.view.ProductDetail;
import al.pcmania.web.view.Seo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SeoService {

    public static final String DEFAULT_IMAGE = "/images/og-default.png";
    private static final int DEFAULT_IMAGE_W = 1200, DEFAULT_IMAGE_H = 630;

    private final AppProperties props;
    private final ImageStorage images;
    private final ObjectMapper json;

    public String abs(String path) {
        return props.base() + path;
    }

    public Seo page(String title, String description, String path) {
        return new Seo(title + " | " + props.siteName(), description, abs(path), abs(DEFAULT_IMAGE),
                DEFAULT_IMAGE_W, DEFAULT_IMAGE_H, props.siteName(), "website", null, null, false);
    }

    public Seo home() {
        Map<String, Object> org = new LinkedHashMap<>();
        org.put("@context", "https://schema.org");
        org.put("@type", "Store");
        org.put("name", props.siteName());
        org.put("url", props.base() + "/");
        org.put("image", abs(DEFAULT_IMAGE));
        org.put("telephone", props.phoneDisplay());
        org.put("email", props.contactEmail());
        org.put("currenciesAccepted", "ALL");
        org.put("paymentAccepted", "Cash, Bank transfer");
        org.put("address", Map.of("@type", "PostalAddress", "addressLocality", "Tiranë", "addressCountry", "AL"));
        String title = props.siteName() + " – Karta grafike dhe pjesë kompjuteri në Tiranë";
        return new Seo(title,
                "Karta grafike dhe pjesë PC të reja e të përdorura, të testuara, me garanci. Pagesë në dorëzim, transport në gjithë Shqipërinë.",
                props.base() + "/", abs(DEFAULT_IMAGE), DEFAULT_IMAGE_W, DEFAULT_IMAGE_H, props.siteName(), "website", null,
                toJson(org), false);
    }

    public Seo category(Category c, int page) {
        String path = "/kategori/" + c.getSlug() + (page > 1 ? "?faqe=" + page : "");
        return page(c.getNameSq() + (page > 1 ? " – faqja " + page : ""),
                c.getNameSq() + " të reja dhe të përdorura në Tiranë. Çmime në Lekë, pagesë në dorëzim dhe transport në gjithë Shqipërinë.",
                path);
    }

    public Seo product(ProductDetail p, Category category) {
        String url = abs("/produkt/" + p.slug());
        String title = p.title() + " – " + Fmt.lek(p.priceLek());
        String description = description(p);

        String image = abs(DEFAULT_IMAGE);
        Integer w = DEFAULT_IMAGE_W, h = DEFAULT_IMAGE_H;
        if (p.getPrimaryImage() != null) {
            image = abs(ImageStorage.url(ImageStorage.Size.full, p.getPrimaryImage()));
            int[] dim = images.dimensions(ImageStorage.Size.full, p.getPrimaryImage());
            w = dim == null ? null : dim[0];
            h = dim == null ? null : dim[1];
        }

        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("@type", "Offer");
        offer.put("url", url);
        offer.put("priceCurrency", "ALL");
        offer.put("price", p.priceLek());
        offer.put("availability", switch (p.status()) {
            case ACTIVE -> p.quantity() > 0 ? "https://schema.org/InStock" : "https://schema.org/OutOfStock";
            case SOLD -> "https://schema.org/SoldOut";
            default -> "https://schema.org/OutOfStock";
        });
        offer.put("itemCondition", switch (p.condition()) {
            case NEW -> "https://schema.org/NewCondition";
            case OPEN_BOX, USED -> "https://schema.org/UsedCondition";
        });
        offer.put("seller", Map.of("@type", "Organization", "name", props.siteName()));

        Map<String, Object> product = new LinkedHashMap<>();
        product.put("@context", "https://schema.org");
        product.put("@type", "Product");
        product.put("name", p.title());
        product.put("sku", "PM-" + p.id());
        if (p.model() != null) product.put("mpn", p.model());
        if (p.brandName() != null) product.put("brand", Map.of("@type", "Brand", "name", p.brandName()));
        product.put("description", description);
        product.put("image", p.images().isEmpty() ? List.of(image)
                : p.images().stream().map(f -> abs(ImageStorage.url(ImageStorage.Size.full, f))).toList());
        if (!p.specs().isEmpty()) {
            product.put("additionalProperty", p.specs().stream()
                    .map(s -> Map.of("@type", "PropertyValue", "name", s.key(), "value", s.value())).toList());
        }
        product.put("offers", offer);

        Map<String, Object> breadcrumbs = Map.of(
                "@context", "https://schema.org",
                "@type", "BreadcrumbList",
                "itemListElement", List.of(
                        crumb(1, "Kryefaqja", props.base() + "/"),
                        crumb(2, category == null ? "Produkte" : category.getNameSq(),
                                abs(category == null ? "/" : "/kategori/" + category.getSlug())),
                        crumb(3, p.title(), url)));

        return new Seo(title + " | " + props.siteName(), description, url, image, w, h, p.title(), "product",
                p.status() == ProductStatus.ACTIVE ? p.priceLek() : null,
                toJson(List.of(product, breadcrumbs)), false);
    }

    /** wa.me deep link prefilled with the product title, price and URL. */
    public String whatsappLink(ProductDetail p) {
        String text = "Përshëndetje! Jam i interesuar për: " + p.title() + " (" + Fmt.lek(p.priceLek()) + ")\n"
                + abs("/produkt/" + p.slug());
        return whatsappLink(text);
    }

    public String whatsappLink(String text) {
        return "https://wa.me/" + props.whatsappNumber().replaceAll("\\D", "") + "?text="
                + URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String description(ProductDetail p) {
        String base = StringUtils.hasText(p.shortDescription()) ? p.shortDescription()
                : StringUtils.hasText(p.fullDescription()) ? Jsoup.parse(p.fullDescription()).text() : p.title();
        String prefix = p.condition().label + " · " + Fmt.lek(p.priceLek()) + " · ";
        String d = prefix + base;
        return d.length() > 200 ? d.substring(0, 197) + "…" : d;
    }

    private static Map<String, Object> crumb(int pos, String name, String url) {
        return Map.of("@type", "ListItem", "position", pos, "name", name, "item", url);
    }

    private String toJson(Object o) {
        try {
            // Prevent "</script>" inside values from terminating the script element.
            return json.writeValueAsString(o).replace("</", "<\\/");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
