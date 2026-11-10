package al.pcmania.web.view;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;

import java.util.List;

public record ProductCard(
        String slug,
        String title,
        String brandName,
        Condition condition,
        int priceLek,
        ProductStatus status,
        String imageFilename,
        List<String> imageFilenames,
        boolean miningFree,
        boolean transportIncluded,
        Integer warrantyDays,

        boolean tradeEligible,
        String specLine) {

    private static final int MAX_CARD_IMAGES = 6;

    public static ProductCard of(Product p, String imageFilename) {
        return of(p, imageFilename == null ? List.of() : List.of(imageFilename), null);
    }

    public static ProductCard of(Product p, List<String> imageFilenames, String specLine) {
        List<String> capped = imageFilenames == null ? List.of()
                : imageFilenames.stream().limit(MAX_CARD_IMAGES).toList();
        return new ProductCard(p.getSlug(), p.getTitle(), p.getBrand() == null ? null : p.getBrand().getName(),
                p.getCondition(), p.getPriceLek(), p.getStatus(), capped.isEmpty() ? null : capped.get(0), capped,
                p.isMiningFree(), p.isTransportIncluded(), p.getWarrantyDays(), p.isTradeEligible(), specLine);
    }

    public boolean hasGallery() {
        return imageFilenames != null && imageFilenames.size() > 1;
    }

    public boolean isReserved() {
        return status == ProductStatus.RESERVED;
    }

    public boolean isSold() {
        return status == ProductStatus.SOLD;
    }
}
