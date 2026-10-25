package al.pcmania.service;

import al.pcmania.config.AppProperties;
import al.pcmania.domain.Category;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.web.Fmt;
import al.pcmania.web.view.ComboView;
import al.pcmania.web.view.ProductCard;
import al.pcmania.web.view.ProductDetail;
import al.pcmania.web.view.Seo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SeoService {

    public static final String DEFAULT_IMAGE = "/images/og-default.png";
    private static final int DEFAULT_IMAGE_W = 1200, DEFAULT_IMAGE_H = 630;

    private final AppProperties props;
    private final ImageStorage images;
    private final ObjectMapper json;

    @Value("${app.price-range:}")
    private String priceRange;

    @Value("${app.return-days:3}")
    private int returnDays;
    @Value("${app.handling-days-min:0}")
    private int handlingDaysMin;
    @Value("${app.handling-days-max:1}")
    private int handlingDaysMax;
    @Value("${app.transit-days-min:1}")
    private int transitDaysMin;
    @Value("${app.transit-days-max:3}")
    private int transitDaysMax;

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
        if (props.publicEmail() != null) org.put("email", props.publicEmail());
        if (StringUtils.hasText(props.facebookUrl())) org.put("sameAs", List.of(props.facebookUrl()));
        if (StringUtils.hasText(priceRange)) org.put("priceRange", priceRange);
        org.put("currenciesAccepted", "ALL");
        org.put("paymentAccepted", "Cash, Bank transfer");
        org.put("address", Map.of("@type", "PostalAddress", "addressLocality", "Tiranë", "addressCountry", "AL"));
        org.put("areaServed", Map.of("@type", "Country", "name", "Albania"));
        String title = props.siteName() + " – Karta grafike dhe pjesë kompjuteri në Tiranë";
        return new Seo(title,
                "Karta grafike dhe pjesë PC të reja e të përdorura, të testuara, me garanci. Pagesë në dorëzim, transport në gjithë Shqipërinë.",
                props.base() + "/", abs(DEFAULT_IMAGE), DEFAULT_IMAGE_W, DEFAULT_IMAGE_H, props.siteName(), "website", null,
                toJson(org), false);
    }

    public Seo category(Category c, int page, int pageSize, List<ProductCard> shown) {
        String path = "/kategori/" + c.getSlug() + (page > 1 ? "?faqe=" + page : "");
        String url = abs(path);
        String title = categoryTitle(c) + (page > 1 ? " – faqja " + page : "");
        String description = categoryDescription(c);

        Map<String, Object> list = new LinkedHashMap<>();
        list.put("@context", "https://schema.org");
        list.put("@type", "ItemList");
        list.put("@id", url + "#produktet");
        list.put("numberOfItems", shown.size());
        List<Map<String, Object>> elements = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            ProductCard p = shown.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("@type", "ListItem");
            item.put("position", (page - 1) * pageSize + i + 1);
            item.put("url", abs("/produkt/" + p.slug()));
            item.put("name", p.title());
            if (p.imageFilename() != null) item.put("image", abs(ImageStorage.url(ImageStorage.Size.full, p.imageFilename())));
            elements.add(item);
        }
        list.put("itemListElement", elements);

        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("@context", "https://schema.org");
        collection.put("@type", "CollectionPage");
        collection.put("name", c.getNameSq());
        collection.put("description", description);
        collection.put("url", url);
        collection.put("mainEntity", Map.of("@id", url + "#produktet"));

        Map<String, Object> breadcrumbs = Map.of(
                "@context", "https://schema.org",
                "@type", "BreadcrumbList",
                "itemListElement", List.of(
                        crumb(1, "Kryefaqja", props.base() + "/"),
                        crumb(2, c.getNameSq(), abs("/kategori/" + c.getSlug()))));

        return new Seo(title, description, url, abs(DEFAULT_IMAGE), DEFAULT_IMAGE_W, DEFAULT_IMAGE_H, props.siteName(),
                "website", null, toJson(List.of(collection, list, breadcrumbs)), false);
    }

    public String categoryTitle(Category c) {
        if (StringUtils.hasText(c.getMetaTitle())) return c.getMetaTitle().trim();
        String name = c.getNameSq().trim();
        String suffix = " | " + props.siteName();
        String full = name + " të Reja dhe të Përdorura në Tiranë" + suffix;
        if (full.length() <= MetaText.TITLE_MAX) return full;
        String used = name + " të Përdorura në Tiranë" + suffix;
        if (used.length() <= MetaText.TITLE_MAX) return used;
        return name + " të Përdorura në Tiranë";
    }

    public String categoryDescription(Category c) {
        if (StringUtils.hasText(c.getMetaDescription())) return c.getMetaDescription().trim();
        return MetaText.cap(c.getNameSq().trim() + " në " + props.siteName() + ", Tiranë: produkte të reja dhe të përdorura, "
                + "të testuara dhe me garanci. Pagesë në dorëzim dhe transport në gjithë Shqipërinë.", MetaText.DESCRIPTION_MAX);
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
        offer.put("shippingDetails", shipping(p.transportIncluded()));
        offer.put("hasMerchantReturnPolicy", returnPolicy());

        Map<String, Object> product = new LinkedHashMap<>();
        product.put("@context", "https://schema.org");
        product.put("@type", "Product");
        product.put("name", p.title());
        product.put("sku", "PM-" + p.id());
        if (p.gtin() != null) product.put("gtin", p.gtin());
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
                toJson(List.of(product, breadcrumbs)), false, p.title());
    }

    public Seo combo(ComboView c) {
        String url = abs("/paketa/" + c.slug());
        String image = abs(DEFAULT_IMAGE);
        Integer w = DEFAULT_IMAGE_W, h = DEFAULT_IMAGE_H;
        if (c.primaryImage() != null) {
            image = abs(ImageStorage.url(ImageStorage.Size.full, c.primaryImage()));
            int[] dim = images.dimensions(ImageStorage.Size.full, c.primaryImage());
            w = dim == null ? null : dim[0];
            h = dim == null ? null : dim[1];
        }
        String members = String.join(" + ", c.members().stream().map(m -> m.product().title()).toList());
        String description = MetaText.fit(List.of(
                Fmt.lek(c.comboPriceLek()) + " në vend të " + Fmt.lek(c.originalTotalLek()) + " – "
                        + Fmt.thousands(c.savingsLek()).replace(" lekë", " lekë më pak") + ".",
                "Paketë: " + members + ".",
                "Pagesë në dorëzim, dërgesë në gjithë Shqipërinë."), MetaText.DESCRIPTION_MAX);
        return new Seo("Ofertë: " + c.title() + " – " + Fmt.lek(c.comboPriceLek()) + " | " + props.siteName(), description,
                url, image, w, h, c.title(), "product", c.visible() ? c.comboPriceLek() : null, null, false, c.title());
    }

    public String whatsappLink(ProductDetail p) {
        String text = "Përshëndetje! Jam i interesuar për: " + p.title() + " (" + Fmt.lek(p.priceLek()) + ")\n"
                + abs("/produkt/" + p.slug());
        return whatsappLink(text);
    }

    public String whatsappLink(String text) {
        return "https://wa.me/" + props.whatsappNumber().replaceAll("\\D", "") + "?text="
                + URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String description(ProductDetail p) {
        if (StringUtils.hasText(p.metaDescription())) return p.metaDescription().trim();
        List<String> parts = new ArrayList<>();
        String state = p.isSold() ? "E shitur: " : p.isReserved() ? "E rezervuar: " : "";
        parts.add(state + p.title() + " (" + p.condition().label.toLowerCase(Locale.ROOT) + ") – " + Fmt.lek(p.priceLek()) + ".");
        parts.add(p.transportIncluded() ? "Pagesë në dorëzim dhe transport falas në gjithë Shqipërinë."
                : "Pagesë në dorëzim, dërgesë në gjithë Shqipërinë.");
        String warranty = Fmt.warrantyLabel(p.warrantyDays());
        if (warranty != null) parts.add(Character.toUpperCase(warranty.charAt(0)) + warranty.substring(1) + ".");
        parts.add(StringUtils.hasText(p.shortDescription()) ? p.shortDescription() : p.fullDescription());
        if (StringUtils.hasText(p.specLine())) parts.add("Specifikime: " + p.specLine() + ".");
        return MetaText.fit(parts, MetaText.DESCRIPTION_MAX);
    }

    private Map<String, Object> shipping(boolean free) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("@type", "OfferShippingDetails");
        details.put("shippingRate", Map.of("@type", "MonetaryAmount", "value", free ? 0 : props.courierShippingLek(), "currency", "ALL"));
        details.put("shippingDestination", Map.of("@type", "DefinedRegion", "addressCountry", "AL"));
        details.put("deliveryTime", Map.of(
                "@type", "ShippingDeliveryTime",
                "handlingTime", days(handlingDaysMin, handlingDaysMax),
                "transitTime", days(transitDaysMin, transitDaysMax)));
        return details;
    }

    private Map<String, Object> returnPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@type", "MerchantReturnPolicy");
        policy.put("applicableCountry", "AL");
        if (returnDays > 0) {
            policy.put("returnPolicyCategory", "https://schema.org/MerchantReturnFiniteReturnWindow");
            policy.put("merchantReturnDays", returnDays);
        } else {
            policy.put("returnPolicyCategory", "https://schema.org/MerchantReturnNotPermitted");
        }
        policy.put("merchantReturnLink", abs("/kushtet-e-perdorimit"));
        return policy;
    }

    private static Map<String, Object> days(int min, int max) {
        return Map.of("@type", "QuantitativeValue", "minValue", min, "maxValue", max, "unitCode", "DAY");
    }

    private static Map<String, Object> crumb(int pos, String name, String url) {
        return Map.of("@type", "ListItem", "position", pos, "name", name, "item", url);
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o).replace("</", "<\\/");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
