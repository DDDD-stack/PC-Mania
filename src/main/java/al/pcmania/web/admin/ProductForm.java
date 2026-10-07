package al.pcmania.web.admin;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.Product;
import al.pcmania.domain.ProductSpec;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductForm {
    @NotBlank(message = "Titulli është i detyrueshëm")
    @Size(max = 200)
    private String title;
    private Long brandId;
    @Size(max = 80)
    private String newBrand;
    @Size(max = 120)
    private String model;
    @NotBlank(message = "Zgjidhni kategorinë")
    private String categorySlug;
    @Size(max = 200)
    @Pattern(regexp = "^$|^[a-z0-9]+(-[a-z0-9]+)*$", message = "Vetëm shkronja të vogla, numra dhe viza")
    private String slug;
    @NotNull
    private Condition condition = Condition.USED;
    @NotNull(message = "Çmimi është i detyrueshëm")
    @Min(0)
    private Integer priceLek;
    @NotNull(message = "Kostoja është e detyrueshme")
    @Min(0)
    private Integer costLek;
    @NotNull
    @Min(0)
    private Integer quantity = 1;
    @NotNull
    private ProductStatus status = ProductStatus.DRAFT;
    @Size(max = 500)
    private String shortDescription;
    private String fullDescription;
    @Min(0)
    private Integer warrantyDays;
    private String testNotes;
    private boolean miningFree;
    private boolean transportIncluded;

    private boolean tradeEligible;

    @Min(0)
    private Integer maxTradeValueLek;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate listedAt;

    private Long gpuModelId;
    private List<String> specKeys = new ArrayList<>();
    private List<String> specValues = new ArrayList<>();

    public static ProductForm from(Product p) {
        ProductForm f = new ProductForm();
        f.title = p.getTitle();
        f.brandId = p.getBrand() == null ? null : p.getBrand().getId();
        f.model = p.getModel();
        f.categorySlug = p.getCategorySlug();
        f.slug = p.getSlug();
        f.condition = p.getCondition();
        f.priceLek = p.getPriceLek();
        f.costLek = p.getCostLek();
        f.quantity = p.getQuantity();
        f.status = p.getStatus();
        f.shortDescription = p.getShortDescription();
        f.fullDescription = p.getFullDescription();
        f.warrantyDays = p.getWarrantyDays();
        f.testNotes = p.getTestNotes();
        f.miningFree = p.isMiningFree();
        f.transportIncluded = p.isTransportIncluded();
        f.tradeEligible = p.isTradeEligible();
        f.maxTradeValueLek = p.getMaxTradeValueLek();
        f.listedAt = p.getListedAt() == null ? null : p.getListedAt().toLocalDate();
        f.gpuModelId = p.getGpuModel() == null ? null : p.getGpuModel().getId();
        for (ProductSpec s : p.getSpecs()) {
            f.specKeys.add(s.getSpecKey());
            f.specValues.add(s.getSpecValue());
        }
        return f;
    }
}
