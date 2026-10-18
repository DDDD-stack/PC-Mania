package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
public class PrebuiltSpec {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    private String cpuName;
    private Integer cpuCores;
    private Integer cpuThreads;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gpu_catalog_id")
    private SpecCatalogEntry gpuCatalog;
    private String gpuNameOverride;
    private Integer ramGb;
    private Integer ramSpeedMhz;
    private String ramConfig;
    private String storagePrimary;
    private String storageSecondary;
    private Integer psuWatts;
    private String psuBrand;
    private String motherboardModel;
    private String caseModel;
    private boolean osInstalled;
    private String osName;
    @Column(name = "load_temp_cpu_c")
    private Integer loadTempCpuC;
    @Column(name = "load_temp_gpu_c")
    private Integer loadTempGpuC;
    private LocalDate testedAt;
    private String notesSq;

    public String gpuName() {
        if (gpuNameOverride != null && !gpuNameOverride.isBlank()) return gpuNameOverride.trim();
        return gpuCatalog == null ? null : gpuCatalog.getName();
    }
}
