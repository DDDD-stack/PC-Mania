package al.pcmania.domain;

import al.pcmania.domain.Enums.DriverStatus;
import al.pcmania.domain.Enums.GpuVendor;
import al.pcmania.domain.Enums.MiningRisk;
import al.pcmania.domain.Enums.UpscalerVersion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "gpu_catalog")
@Getter
@Setter
public class GpuCatalog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String slug;
    private String name;
    @Enumerated(EnumType.STRING)
    private GpuVendor vendor;

    private String aliases;
    private Integer releaseYear;
    private String architecture;
    private Integer vramGb;
    private String memoryType;
    private Integer memoryBusBits;
    private Integer tdpWatts;
    private Integer psuMinWatts;
    private String pcieConnectors;

    private Integer lengthMm;

    private Integer tier;
    @Enumerated(EnumType.STRING)
    private UpscalerVersion supportsDlss;
    private boolean frameGeneration;
    @Enumerated(EnumType.STRING)
    private UpscalerVersion supportsFsr;
    private boolean rayTracing;
    @Enumerated(EnumType.STRING)
    private DriverStatus driverStatus;
    @Enumerated(EnumType.STRING)
    private MiningRisk miningRisk;
    @Column(name = "fps_esports_1080p")
    private Integer fpsEsports1080p;
    @Column(name = "fps_aaa_1080p")
    private Integer fpsAaa1080p;
    @Column(name = "fps_aaa_1440p")
    private Integer fpsAaa1440p;
    private String notesSq;

    public List<String> aliasList() {
        List<String> out = new ArrayList<>();
        if (aliases == null) return out;
        for (String a : aliases.split(",")) {
            String t = a.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public List<String> missingFields() {
        List<String> m = new ArrayList<>();
        if (vramGb == null) m.add("VRAM");
        if (tdpWatts == null) m.add("TDP");
        if (psuMinWatts == null) m.add("PSU min.");
        if (pcieConnectors == null) m.add("Konektorët");
        if (lengthMm == null) m.add("Gjatësia");
        if (tier == null) m.add("Tier");
        if (supportsDlss == null) m.add("DLSS");
        if (supportsFsr == null) m.add("FSR");
        if (driverStatus == null) m.add("Drajverët");
        if (miningRisk == null) m.add("Rreziku i minimit");
        if (fpsEsports1080p == null) m.add("FPS esports 1080p");
        if (fpsAaa1080p == null) m.add("FPS AAA 1080p");
        if (fpsAaa1440p == null) m.add("FPS AAA 1440p");
        return m;
    }
}
