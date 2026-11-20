package al.pcmania.web.view;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Enums.PromoTheme;
import al.pcmania.domain.Product;
import al.pcmania.service.PricingService;

import java.time.LocalDateTime;
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
        String specLine,
        Integer regularPriceLek,
        LocalDateTime saleEndsAt,
        PromoTheme saleTheme) {

    private static final int MAX_CARD_IMAGES = 6;

    public static ProductCard of(Product p, String imageFilename) {
        return of(p, imageFilename == null ? List.of() : List.of(imageFilename), null, null);
    }

    public static ProductCard of(Product p, List<String> imageFilenames, String specLine, PricingService.Sale sale) {
        List<String> capped = imageFilenames == null ? List.of()
                : imageFilenames.stream().limit(MAX_CARD_IMAGES).toList();
        return new ProductCard(p.getSlug(), p.getTitle(), p.getBrand() == null ? null : p.getBrand().getName(),
                p.getCondition(), sale == null ? p.getPriceLek() : sale.priceLek(), p.getStatus(),
                capped.isEmpty() ? null : capped.get(0), capped,
                p.isMiningFree(), p.isTransportIncluded(), p.getWarrantyDays(), p.isTradeEligible(), specLine,
                sale == null ? null : sale.regularPriceLek(), sale == null ? null : sale.endsAt(),
                sale == null ? null : sale.theme());
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

    public boolean isOnSale() {
        return regularPriceLek != null && regularPriceLek > priceLek;
    }

    public int getSavingsLek() {
        return isOnSale() ? regularPriceLek - priceLek : 0;
    }

    public int getDiscountPercent() {
        return isOnSale() ? Deals.percent(regularPriceLek, priceLek) : 0;
    }

    public String getSaleUrgency() {
        return isOnSale() ? Deals.urgency(saleEndsAt) : null;
    }

    public String getSaleThemeClass() {
        return "sale-theme-" + (saleTheme == null ? PromoTheme.FIRE : saleTheme).name();
    }
}
