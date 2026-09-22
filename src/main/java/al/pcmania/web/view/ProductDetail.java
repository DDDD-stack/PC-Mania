package al.pcmania.web.view;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;
import al.pcmania.domain.ProductImage;

import java.util.List;

/** Public, cost-free projection of a product for the detail page. */
public record ProductDetail(
        Long id,
        String slug,
        String title,
        String brandName,
        String model,
        String categorySlug,
        Condition condition,
        int priceLek,
        int quantity,
        ProductStatus status,
        String shortDescription,
        String fullDescription,
        Integer warrantyDays,
        String testNotes,
        boolean miningFree,
        boolean transportIncluded,
        List<Spec> specs,
        List<String> images) {

    public record Spec(String key, String value) {}

    public static ProductDetail of(Product p) {
        return new ProductDetail(p.getId(), p.getSlug(), p.getTitle(), p.getBrand() == null ? null : p.getBrand().getName(),
                p.getModel(), p.getCategorySlug(), p.getCondition(), p.getPriceLek(), p.getQuantity(), p.getStatus(),
                p.getShortDescription(), p.getFullDescription(), p.getWarrantyDays(), p.getTestNotes(),
                p.isMiningFree(), p.isTransportIncluded(),
                p.getSpecs().stream().map(s -> new Spec(s.getSpecKey(), s.getSpecValue())).toList(),
                p.getImages().stream().map(ProductImage::getFilename).toList());
    }

    public boolean isAvailable() {
        return status == ProductStatus.ACTIVE && quantity > 0;
    }

    public String getPrimaryImage() {
        return images.isEmpty() ? null : images.getFirst();
    }
}
