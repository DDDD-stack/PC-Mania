package al.pcmania.web.view;

public record Seo(
        String title,
        String description,
        String canonical,
        String image,
        Integer imageWidth,
        Integer imageHeight,
        String imageAlt,
        String type,
        Integer priceLek,
        String jsonLd,
        boolean noindex,
        String ogTitle) {

    public Seo(String title, String description, String canonical, String image, Integer imageWidth, Integer imageHeight,
               String imageAlt, String type, Integer priceLek, String jsonLd, boolean noindex) {
        this(title, description, canonical, image, imageWidth, imageHeight, imageAlt, type, priceLek, jsonLd, noindex, null);
    }

    public String getShareTitle() {
        return ogTitle != null ? ogTitle : title;
    }

    public Seo withNoindex() {
        return new Seo(title, description, canonical, image, imageWidth, imageHeight, imageAlt, type, priceLek, jsonLd, true, ogTitle);
    }

    public Seo withJsonLd(String json) {
        return new Seo(title, description, canonical, image, imageWidth, imageHeight, imageAlt, type, priceLek, json, noindex, ogTitle);
    }

    public Seo withCanonical(String url) {
        return new Seo(title, description, url, image, imageWidth, imageHeight, imageAlt, type, priceLek, jsonLd, noindex, ogTitle);
    }

    public Seo withOgTitle(String shareTitle) {
        return new Seo(title, description, canonical, image, imageWidth, imageHeight, imageAlt, type, priceLek, jsonLd, noindex, shareTitle);
    }
}
