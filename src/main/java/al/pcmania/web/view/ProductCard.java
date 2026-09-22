package al.pcmania.web.view;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;

/** Public, cost-free projection of a product for grids. */
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
        Integer warrantyDays) {

    public static ProductCard of(Product p, String imageFilename) {
        return new ProductCard(p.getSlug(), p.getTitle(), p.getBrand() == null ? null : p.getBrand().getName(),
                p.getCondition(), p.getPriceLek(), p.getStatus(), imageFilename, p.isMiningFree(),
                p.isTransportIncluded(), p.getWarrantyDays());
    }
}
