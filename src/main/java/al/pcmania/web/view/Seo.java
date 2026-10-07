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
        boolean noindex) {

    public Seo withNoindex() {
        return new Seo(title, description, canonical, image, imageWidth, imageHeight, imageAlt, type, priceLek, jsonLd, true);
    }

    public Seo withJsonLd(String json) {
        return new Seo(title, description, canonical, image, imageWidth, imageHeight, imageAlt, type, priceLek, json, noindex);
    }

    public Seo withCanonical(String url) {
        return new Seo(title, description, url, image, imageWidth, imageHeight, imageAlt, type, priceLek, jsonLd, noindex);
    }
}
