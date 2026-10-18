package al.pcmania.web.admin;

import al.pcmania.domain.Enums.Condition;
import al.pcmania.domain.Enums.ProductStatus;
import al.pcmania.domain.PrebuiltSpec;
import al.pcmania.domain.Product;
import al.pcmania.domain.ProductSpec;
import jakarta.validation.Valid;
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

    @Valid
    private PrebuiltForm prebuilt = new PrebuiltForm();

    @Data
    public static class PrebuiltForm {
        @Size(max = 120)
        private String cpuName;
        @Min(1) @Max(256)
        private Integer cpuCores;
        @Min(1) @Max(512)
        private Integer cpuThreads;
        private Long gpuCatalogId;
        private String gpuCatalogName;
        @Size(max = 120)
        private String gpuNameOverride;
        @Min(1) @Max(1024)
        private Integer ramGb;
        @Min(1) @Max(20000)
        private Integer ramSpeedMhz;
        @Size(max = 40)
        private String ramConfig;
        @Size(max = 120)
        private String storagePrimary;
        @Size(max = 120)
        private String storageSecondary;
        @Min(1) @Max(5000)
        private Integer psuWatts;
        @Size(max = 80)
        private String psuBrand;
        @Size(max = 120)
        private String motherboardModel;
        @Size(max = 120)
        private String caseModel;
        private boolean osInstalled;
        @Size(max = 80)
        private String osName;
        @Min(0) @Max(150)
        private Integer loadTempCpuC;
        @Min(0) @Max(150)
        private Integer loadTempGpuC;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate testedAt;
        private String notesSq;

        public static PrebuiltForm from(PrebuiltSpec s) {
            PrebuiltForm f = new PrebuiltForm();
            f.cpuName = s.getCpuName();
            f.cpuCores = s.getCpuCores();
            f.cpuThreads = s.getCpuThreads();
            f.gpuCatalogId = s.getGpuCatalog() == null ? null : s.getGpuCatalog().getId();
            f.gpuCatalogName = s.getGpuCatalog() == null ? null : s.getGpuCatalog().getName();
            f.gpuNameOverride = s.getGpuNameOverride();
            f.ramGb = s.getRamGb();
            f.ramSpeedMhz = s.getRamSpeedMhz();
            f.ramConfig = s.getRamConfig();
            f.storagePrimary = s.getStoragePrimary();
            f.storageSecondary = s.getStorageSecondary();
            f.psuWatts = s.getPsuWatts();
            f.psuBrand = s.getPsuBrand();
            f.motherboardModel = s.getMotherboardModel();
            f.caseModel = s.getCaseModel();
            f.osInstalled = s.isOsInstalled();
            f.osName = s.getOsName();
            f.loadTempCpuC = s.getLoadTempCpuC();
            f.loadTempGpuC = s.getLoadTempGpuC();
            f.testedAt = s.getTestedAt();
            f.notesSq = s.getNotesSq();
            return f;
        }
    }

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
