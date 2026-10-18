package al.pcmania.web.view;

import java.util.List;

public record ComboView(
        Long id,
        String slug,
        String title,
        String subtitle,
        int comboPriceLek,
        int originalTotalLek,
        boolean visible,
        List<Member> members) {

    public record Member(ProductCard product, int quantity, List<ProductDetail.Spec> specs, String shortDescription) {

        public int lineTotalLek() {
            return product.priceLek() * quantity;
        }

        public boolean isAvailable() {
            return product.status() == al.pcmania.domain.Enums.ProductStatus.ACTIVE;
        }
    }

    public int savingsLek() {
        return originalTotalLek - comboPriceLek;
    }

    public List<String> thumbs() {
        return members.stream().map(m -> m.product().imageFilename()).toList();
    }

    public String primaryImage() {
        return members.stream().map(m -> m.product().imageFilename()).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    public int itemCount() {
        return members.stream().mapToInt(Member::quantity).sum();
    }
}
