package al.pcmania.web.view;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;

public record ProductCard(
        String slug,
        String title,
        String brandName,
        Condition condition,
        int priceLek,
        ProductStatus status,
        String imageFilename,
        boolean miningFree,
        boolean transportIncluded,
        Integer warrantyDays,

        boolean tradeEligible,
        String specLine) {

    public static ProductCard of(Product p, String imageFilename) {
        return of(p, imageFilename, null);
    }

    public static ProductCard of(Product p, String imageFilename, String specLine) {
        return new ProductCard(p.getSlug(), p.getTitle(), p.getBrand() == null ? null : p.getBrand().getName(),
                p.getCondition(), p.getPriceLek(), p.getStatus(), imageFilename, p.isMiningFree(),
                p.isTransportIncluded(), p.getWarrantyDays(), p.isTradeEligible(), specLine);
    }

    public boolean isReserved() {
        return status == ProductStatus.RESERVED;
    }

    public boolean isSold() {
        return status == ProductStatus.SOLD;
    }
}
