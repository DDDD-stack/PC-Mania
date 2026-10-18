package al.pcmania.web.view;

import al.pcmania.domain.PrebuiltSpec;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record PrebuiltView(
        String cpu,
        String gpu,
        String ram,
        Integer ramGb,
        String storage,
        String psu,
        String motherboard,
        String caseModel,
        String os,
        Integer loadTempCpuC,
        Integer loadTempGpuC,
        LocalDate testedAt,
        String notesSq) {

    public record Row(String label, String value) {}

    public static PrebuiltView of(PrebuiltSpec s) {
        String cpu = s.getCpuName();
        if (cpu != null && (s.getCpuCores() != null || s.getCpuThreads() != null)) {
            List<String> parts = new ArrayList<>();
            if (s.getCpuCores() != null) parts.add(s.getCpuCores() + " bërthama");
            if (s.getCpuThreads() != null) parts.add(s.getCpuThreads() + " thread-e");
            cpu += " (" + String.join(" / ", parts) + ")";
        }
        String ram = null;
        if (s.getRamGb() != null) {
            ram = s.getRamGb() + " GB";
            if (s.getRamSpeedMhz() != null) ram += " · " + s.getRamSpeedMhz() + " MHz";
            if (StringUtils.hasText(s.getRamConfig())) ram += " (" + s.getRamConfig().trim() + ")";
        }
        String storage = s.getStoragePrimary();
        if (StringUtils.hasText(s.getStorageSecondary())) storage = (storage == null ? "" : storage + " + ") + s.getStorageSecondary().trim();
        String psu = null;
        if (s.getPsuWatts() != null || StringUtils.hasText(s.getPsuBrand())) {
            psu = ((s.getPsuWatts() == null ? "" : s.getPsuWatts() + " W ") + (s.getPsuBrand() == null ? "" : s.getPsuBrand().trim())).trim();
        }
        String os = s.isOsInstalled()
                ? (StringUtils.hasText(s.getOsName()) ? s.getOsName().trim() + " – i instaluar" : "I instaluar")
                : "Pa sistem operativ";
        return new PrebuiltView(blankToNull(cpu), s.gpuName(), ram, s.getRamGb(), blankToNull(storage), psu,
                blankToNull(s.getMotherboardModel()), blankToNull(s.getCaseModel()), os,
                s.getLoadTempCpuC(), s.getLoadTempGpuC(), s.getTestedAt(), blankToNull(s.getNotesSq()));
    }

    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        add(rows, "Procesori", cpu);
        add(rows, "Karta grafike", gpu == null ? "E integruar" : gpu);
        add(rows, "Memoria RAM", ram);
        add(rows, "Disku", storage);
        add(rows, "Furnizuesi", psu);
        add(rows, "Pllaka amë", motherboard);
        add(rows, "Kasa", caseModel);
        add(rows, "Sistemi operativ", os);
        return rows;
    }

    public boolean isTested() {
        return loadTempCpuC != null || loadTempGpuC != null;
    }

    public String specLine() {
        List<String> parts = new ArrayList<>();
        if (cpu != null) parts.add(shortCpu());
        if (gpu != null) parts.add(gpu);
        if (ramGb != null) parts.add(ramGb + " GB RAM");
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private String shortCpu() {
        int paren = cpu.indexOf(" (");
        return paren > 0 ? cpu.substring(0, paren) : cpu;
    }

    private static void add(List<Row> rows, String label, String value) {
        if (StringUtils.hasText(value)) rows.add(new Row(label, value));
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
