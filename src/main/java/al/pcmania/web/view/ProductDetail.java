package al.pcmania.web.view;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;
import al.pcmania.domain.ProductImage;
import al.pcmania.domain.ProductSpec;
import al.pcmania.service.FieldTemplateService;

import java.util.ArrayList;
import java.util.List;

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
        String metaDescription,
        Integer warrantyDays,
        String testNotes,
        boolean miningFree,
        boolean transportIncluded,

        boolean tradeEligible,
        List<Spec> specs,
        List<String> images,
        PrebuiltView prebuilt,
        String specLine) {

    public record Spec(String key, String value) {}

    public static ProductDetail of(Product p) {
        return of(p, null, null);
    }

    public static ProductDetail of(Product p, FieldTemplateService.Template template, PrebuiltView prebuilt) {
        List<Spec> specs = new ArrayList<>();
        List<String> listing = new ArrayList<>();
        for (ProductSpec s : p.getSpecs()) {
            if (template == null) {
                specs.add(new Spec(s.getSpecKey(), s.getSpecValue()));
                continue;
            }
            specs.add(new Spec(template.label(s.getSpecKey()), template.display(s.getSpecKey(), s.getSpecValue())));
            template.field(s.getSpecKey()).filter(FieldTemplateService.Field::showInListing)
                    .ifPresent(f -> listing.add(f.display(s.getSpecValue())));
        }
        String specLine = prebuilt != null ? prebuilt.specLine() : listing.isEmpty() ? null : String.join(" · ", listing);
        return new ProductDetail(p.getId(), p.getSlug(), p.getTitle(), p.getBrand() == null ? null : p.getBrand().getName(),
                p.getModel(), p.getCategorySlug(), p.getCondition(), p.getPriceLek(), p.getQuantity(), p.getStatus(),
                p.getShortDescription(), p.getFullDescription(), p.getMetaDescription(), p.getWarrantyDays(), p.getTestNotes(),
                p.isMiningFree(), p.isTransportIncluded(), p.isTradeEligible(), specs,
                p.getImages().stream().map(ProductImage::getFilename).toList(), prebuilt, specLine);
    }

    public boolean isTradeable() {
        return tradeEligible && isAvailable();
    }

    public boolean isAvailable() {
        return status == ProductStatus.ACTIVE && quantity > 0;
    }

    public boolean isSold() {
        return status == ProductStatus.SOLD;
    }

    public boolean isReserved() {
        return status == ProductStatus.RESERVED;
    }

    public String getPrimaryImage() {
        return images.isEmpty() ? null : images.getFirst();
    }
}
