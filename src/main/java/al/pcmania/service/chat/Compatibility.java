package al.pcmania.service.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Compatibility {

    public enum Verdict { OK, TIGHT, UNKNOWN, NO }

    public record Part(String name, String kind, String origin, Map<String, String> values) {
        String get(String key) {
            String v = values.get(key);
            return v == null || v.isBlank() ? null : v.trim();
        }

        Integer num(String key) {
            String v = get(key);
            if (v == null) return null;
            Matcher m = Pattern.compile("(\\d+)").matcher(v);
            return m.find() ? Integer.valueOf(m.group(1)) : null;
        }
    }

    public record Check(String parts, Verdict verdict, String reason) {}

    public static final String STOCK = "dyqani", CATALOGUE = "katalogu", NAME = "vetëm nga emri";

    private static final Map<String, String> CHIPSET_SOCKET = new LinkedHashMap<>();
    private static final Map<String, String> CHIPSET_RAM = new LinkedHashMap<>();

    static {
        for (String c : List.of("a320", "b350", "x370", "b450", "x470", "a520", "b550", "x570")) {
            CHIPSET_SOCKET.put(c, "AM4");
            CHIPSET_RAM.put(c, "DDR4");
        }
        for (String c : List.of("a620", "b650", "x670", "b840", "b850", "x870")) {
            CHIPSET_SOCKET.put(c, "AM5");
            CHIPSET_RAM.put(c, "DDR5");
        }
        for (String c : List.of("h610", "b660", "h670", "z690", "b760", "h770", "z790")) CHIPSET_SOCKET.put(c, "LGA1700");
        for (String c : List.of("h410", "b460", "h470", "z490", "h510", "b560", "h570", "z590")) {
            CHIPSET_SOCKET.put(c, "LGA1200");
            CHIPSET_RAM.put(c, "DDR4");
        }
        for (String c : List.of("h310", "b360", "b365", "h370", "z370", "z390")) {
            CHIPSET_SOCKET.put(c, "LGA1151");
            CHIPSET_RAM.put(c, "DDR4");
        }
        for (String c : List.of("h810", "b860", "z890")) {
            CHIPSET_SOCKET.put(c, "LGA1851");
            CHIPSET_RAM.put(c, "DDR5");
        }
    }

    private static final Set<String> AM4_OLD_BIOS = Set.of("a320", "b350", "x370", "b450", "x470");
    private static final Set<String> AM4_NO_ZEN1 = Set.of("a520", "b550");
    private static final Set<String> INTEL_600 = Set.of("h610", "b660", "h670", "z690");

    private static final Pattern CHIPSET = Pattern.compile("\\b([abhxzq]\\d{3})[a-z]{0,2}\\b");
    private static final Pattern WATTS = Pattern.compile("\\b(\\d{3,4})\\s*w\\b");
    private static final Pattern RYZEN = Pattern.compile("ryzen\\s*[3579]\\s*(?:pro\\s*)?(\\d)(\\d{3})\\s*(x3d|xt|x|gt|g|f)?");
    private static final Pattern INTEL = Pattern.compile("\\bi[3579]\\s*-?\\s*(\\d{4,5})\\s*(kf|k|f|t)?\\b");
    private static final Pattern CORE_ULTRA = Pattern.compile("ultra\\s*[579]\\s*2\\d{2}");
    private static final Pattern SOCKET = Pattern.compile("\\b(am[345])\\b|\\blga\\s*-?\\s*(\\d{4})\\b");
    private static final Pattern DDR = Pattern.compile("\\bddr\\s*([345])\\b");
    private static final Pattern GB = Pattern.compile("\\b(\\d{1,3})\\s*gb\\b");
    private static final Pattern KIT = Pattern.compile("\\b(\\d)\\s*x\\s*(\\d{1,2})\\s*gb\\b");
    private static final Pattern MM = Pattern.compile("\\b(\\d{3})\\s*mm\\b");
    private static final Pattern GPU = Pattern.compile("\\b(rtx|gtx|rx|arc|radeon|geforce)\\b");

    private Compatibility() {}

    public static String kindFromText(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        if (GPU.matcher(t).find()) return "gpu";
        if (RYZEN.matcher(t).find() || INTEL.matcher(t).find() || CORE_ULTRA.matcher(t).find()) return "cpu";
        if (t.matches(".*\\b(kas[aëe]|case)\\b.*")) return "case";
        if (t.matches(".*\\b(ftoh[eë]s|ftohje|cooler|aio)\\b.*")) return "cooling";
        if (t.matches(".*\\b(psu|furnizues)\\b.*") || WATTS.matcher(t).find()) return "psu";
        if (t.matches(".*\\b(ssd|nvme|hdd|disk)\\b.*")) return "storage";
        if (DDR.matcher(t).find() && (GB.matcher(t).find() || KIT.matcher(t).find())) return "ram";
        if (CHIPSET.matcher(t).find() || t.matches(".*\\b(pllak|motherboard|mobo)\\w*.*")) return "motherboard";
        return null;
    }

    public static Part fromText(String text, String kind) {
        String t = text.toLowerCase(Locale.ROOT);
        Map<String, String> v = new LinkedHashMap<>();
        Matcher m;
        switch (kind == null ? "" : kind) {
            case "cpu" -> {
                if ((m = RYZEN.matcher(t)).find()) {
                    int series = Integer.parseInt(m.group(1));
                    v.put("socket", series >= 7 ? "AM5" : "AM4");
                    v.put("generation", "Ryzen " + series + "000");
                    if ("g".equals(m.group(3)) && (series == 2 || series == 3)
                            && m.group(2).matches("[24]\\d\\d")) v.put("zen_plus_apu", "true");
                } else if ((m = INTEL.matcher(t)).find()) {
                    String digits = m.group(1);
                    int gen = digits.length() == 5 ? Integer.parseInt(digits.substring(0, 2)) : Integer.parseInt(digits.substring(0, 1));
                    String socket = gen >= 12 && gen <= 14 ? "LGA1700" : gen >= 10 ? "LGA1200" : gen >= 8 ? "LGA1151" : null;
                    if (socket != null) v.put("socket", socket);
                    v.put("generation", "Intel gjenerata " + gen);
                } else if (CORE_ULTRA.matcher(t).find()) {
                    v.put("socket", "LGA1851");
                    v.put("generation", "Intel Core Ultra 200");
                }
            }
            case "motherboard" -> {
                if ((m = CHIPSET.matcher(t)).find() && CHIPSET_SOCKET.containsKey(m.group(1))) {
                    v.put("chipset", m.group(1).toUpperCase(Locale.ROOT));
                    v.put("socket", CHIPSET_SOCKET.get(m.group(1)));
                    if (CHIPSET_RAM.containsKey(m.group(1))) v.put("ram_type", CHIPSET_RAM.get(m.group(1)));
                }
                if (t.contains("itx")) v.put("form_factor", "Mini-ITX");
                else if (t.matches(".*\\b[abhxzq]\\d{3}m\\b.*") || t.contains("matx") || t.contains("micro")) v.put("form_factor", "Micro-ATX");
            }
            case "ram" -> {
                if ((m = KIT.matcher(t)).find()) {
                    v.put("kit_config", m.group(1) + "x" + m.group(2) + "GB");
                    v.put("capacity_gb", String.valueOf(Integer.parseInt(m.group(1)) * Integer.parseInt(m.group(2))));
                } else if ((m = GB.matcher(t)).find()) {
                    v.put("capacity_gb", m.group(1));
                }
            }
            case "psu" -> {
                if ((m = WATTS.matcher(t)).find()) v.put("wattage", m.group(1));
            }
            case "case" -> {
                if ((m = MM.matcher(t)).find()) v.put("max_gpu_length_mm", m.group(1));
            }
            case "storage" -> {
                if (t.contains("nvme") || t.contains("m.2") || t.contains("m2")) v.put("type", "NVMe");
                else if (t.contains("hdd")) v.put("type", "HDD");
                else if (t.contains("ssd")) v.put("type", "SATA SSD");
            }
            default -> {
            }
        }
        if ((m = DDR.matcher(t)).find()) v.putIfAbsent("ram".equals(kind) ? "type" : "ram_type", "DDR" + m.group(1));
        if ((m = SOCKET.matcher(t)).find() && !"cooling".equals(kind)) {
            v.putIfAbsent("socket", m.group(1) != null ? m.group(1).toUpperCase(Locale.ROOT) : "LGA" + m.group(2));
        }
        return new Part(text.trim(), kind, NAME, v);
    }

    public static Set<String> coolerSockets(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        String prefix = null;
        for (String raw : text.toUpperCase(Locale.ROOT).split("[,/;\\s]+")) {
            String tok = raw.replace("-", "");
            if (tok.matches("LGA\\d{3,4}X?")) {
                prefix = "LGA";
                out.add(tok);
            } else if (tok.matches("AM\\d")) {
                prefix = "AM";
                out.add(tok);
            } else if (tok.matches("\\d{3,4}X?") && "LGA".equals(prefix)) {
                out.add("LGA" + tok);
            } else if (tok.matches("\\d") && "AM".equals(prefix)) {
                out.add("AM" + tok);
            } else if (!tok.matches("INTEL|AMD")) {
                prefix = null;
            }
        }
        return out;
    }

    static boolean coolerFits(Set<String> sockets, String socket) {
        String s = normSocket(socket);
        for (String c : sockets) {
            if (c.equals(s)) return true;
            if (c.endsWith("X") && s.startsWith(c.substring(0, c.length() - 1)) && s.length() == c.length()) return true;
        }
        return false;
    }

    static String normSocket(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
    }

    public static List<Check> check(List<Part> parts) {
        List<Check> out = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            for (int j = 0; j < parts.size(); j++) {
                if (i == j) continue;
                Part a = parts.get(i), b = parts.get(j);
                if (a.kind() == null || b.kind() == null) continue;
                switch (a.kind() + "+" + b.kind()) {
                    case "cpu+motherboard" -> out.add(cpuBoard(a, b));
                    case "ram+motherboard" -> out.addAll(ramBoard(a, b));
                    case "cooling+cpu" -> out.add(coolerSocket(a, b));
                    case "cooling+motherboard" -> {
                        if (parts.stream().noneMatch(p -> "cpu".equals(p.kind()))) out.add(coolerSocket(a, b));
                    }
                    case "cooling+case" -> out.add(coolerCase(a, b));
                    case "gpu+case" -> out.add(gpuCase(a, b));
                    case "gpu+psu" -> out.add(gpuPsu(a, b));
                    case "motherboard+case" -> out.add(boardCase(a, b));
                    case "storage+motherboard" -> {
                        Check c = storageBoard(a, b);
                        if (c != null) out.add(c);
                    }
                    default -> {
                    }
                }
            }
        }
        return out;
    }

    public static Verdict worst(List<Check> checks) {
        Verdict w = Verdict.OK;
        for (Check c : checks) if (c.verdict().ordinal() > w.ordinal()) w = c.verdict();
        return w;
    }

    private static String pair(Part a, Part b) {
        return a.name() + " + " + b.name();
    }

    private static Check unknown(Part a, Part b, Part missingIn, String what) {
        return new Check(pair(a, b), Verdict.UNKNOWN, "Mungon " + what + " për " + missingIn.name() + ": pyet klientin ose thuaj se nuk mund ta konfirmosh.");
    }

    private static Check cpuBoard(Part cpu, Part board) {
        String cs = normSocket(cpu.get("socket")), bs = normSocket(board.get("socket"));
        if (cs == null) return unknown(cpu, board, cpu, "soketi");
        if (bs == null) return unknown(cpu, board, board, "soketi");
        if (!cs.equals(bs)) {
            return new Check(pair(cpu, board), Verdict.NO, cpu.name() + " është për soketin " + cs + ", ndërsa " + board.name()
                    + " është " + bs + ": nuk përputhen.");
        }
        String chipset = board.get("chipset") == null ? "" : board.get("chipset").toLowerCase(Locale.ROOT);
        String gen = cpu.get("generation") == null ? "" : cpu.get("generation").toLowerCase(Locale.ROOT);
        boolean zenPlusApu = "true".equals(cpu.get("zen_plus_apu"));
        if (AM4_NO_ZEN1.contains(chipset) && (gen.contains("1000") || gen.contains("2000") || zenPlusApu)) {
            return new Check(pair(cpu, board), Verdict.NO, "Soketi është i njëjti (AM4), por pllakat " + chipset.toUpperCase(Locale.ROOT)
                    + " nuk mbështesin Ryzen 1000 dhe 2000, as APU-të 2200G, 2400G, 3200G, 3400G.");
        }
        if (AM4_OLD_BIOS.contains(chipset) && gen.contains("5000")) {
            return new Check(pair(cpu, board), Verdict.TIGHT, "Soketi përputhet (AM4), por pllakat " + chipset.toUpperCase(Locale.ROOT)
                    + " kanë nevojë për një BIOS të ri për Ryzen 5000. Nëse pllaka nuk është përditësuar, duhet bërë kjo para se të montohet procesori.");
        }
        if (INTEL_600.contains(chipset) && (gen.contains("gjenerata 13") || gen.contains("gjenerata 14"))) {
            return new Check(pair(cpu, board), Verdict.TIGHT, "Soketi përputhet (LGA1700), por pllakat " + chipset.toUpperCase(Locale.ROOT)
                    + " mund të kenë nevojë për përditësim BIOS-i për Intel gjenerata 13 dhe 14.");
        }
        return new Check(pair(cpu, board), Verdict.OK, "Të dyja janë soket " + cs + ".");
    }

    private static List<Check> ramBoard(Part ram, Part board) {
        List<Check> out = new ArrayList<>();
        String rt = ram.get("type"), bt = board.get("ram_type");
        if (rt == null) out.add(unknown(ram, board, ram, "tipi i RAM-it (DDR4/DDR5)"));
        else if (bt == null) out.add(unknown(ram, board, board, "tipi i RAM-it që mbështet"));
        else if (!rt.equalsIgnoreCase(bt)) {
            out.add(new Check(pair(ram, board), Verdict.NO, ram.name() + " është " + rt + ", ndërsa pllaka mbështet vetëm " + bt + ". Nuk hyn në slot."));
        } else {
            out.add(new Check(pair(ram, board), Verdict.OK, "Të dyja " + rt.toUpperCase(Locale.ROOT) + "."));
        }
        Integer cap = ram.num("capacity_gb"), max = board.num("max_ram_gb");
        if (cap != null && max != null && cap > max) {
            out.add(new Check(pair(ram, board), Verdict.NO, cap + " GB është më shumë se maksimumi i pllakës (" + max + " GB)."));
        }
        Integer modules = modules(ram.get("kit_config")), slots = board.num("ram_slots");
        if (modules != null && slots != null && modules > slots) {
            out.add(new Check(pair(ram, board), Verdict.NO, "Kiti ka " + modules + " module, pllaka ka vetëm " + slots + " slote."));
        }
        return out;
    }

    private static Integer modules(String kit) {
        if (kit == null) return null;
        Matcher m = Pattern.compile("(\\d)\\s*x").matcher(kit.toLowerCase(Locale.ROOT));
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static Check coolerSocket(Part cooler, Part other) {
        Set<String> sockets = coolerSockets(cooler.get("sockets"));
        String socket = other.get("socket");
        if (sockets.isEmpty()) return unknown(cooler, other, cooler, "lista e soketeve");
        if (socket == null) return unknown(cooler, other, other, "soketi");
        if (coolerFits(sockets, socket)) return new Check(pair(cooler, other), Verdict.OK, cooler.name() + " mbështet soketin " + normSocket(socket) + ".");
        return new Check(pair(cooler, other), Verdict.NO, cooler.name() + " nuk e liston soketin " + normSocket(socket)
                + " (mbështet: " + cooler.get("sockets") + ").");
    }

    private static Check coolerCase(Part cooler, Part box) {
        String type = cooler.get("type") == null ? "" : cooler.get("type").toLowerCase(Locale.ROOT);
        Integer radiator = cooler.num("radiator_mm");
        if (type.contains("ujë") || type.contains("aio") || radiator != null) {
            return new Check(pair(cooler, box), Verdict.UNKNOWN, cooler.name() + " ka radiator" + (radiator == null ? "" : " " + radiator + " mm")
                    + ". Nuk kemi të dhëna se sa radiator pranon kasa: kontrolloni vendin sipër ose përpara.");
        }
        Integer h = cooler.num("height_mm"), max = box.num("max_cooler_height_mm");
        if (h == null) return unknown(cooler, box, cooler, "lartësia");
        if (max == null) return unknown(cooler, box, box, "lartësia maksimale e ftohësit");
        if (h <= max - 5) return new Check(pair(cooler, box), Verdict.OK, "Ftohësi është " + h + " mm, kasa pranon deri në " + max + " mm.");
        if (h <= max) return new Check(pair(cooler, box), Verdict.TIGHT, "Ftohësi është " + h + " mm dhe kasa pranon " + max + " mm: hyn, por ngushtë.");
        return new Check(pair(cooler, box), Verdict.NO, "Ftohësi është " + h + " mm, kasa pranon vetëm " + max + " mm: nuk hyn.");
    }

    private static Check gpuCase(Part gpu, Part box) {
        Integer len = gpu.num("length_mm"), max = box.num("max_gpu_length_mm");
        if (len == null) return unknown(gpu, box, gpu, "gjatësia");
        if (max == null) return unknown(gpu, box, box, "gjatësia maksimale e kartës");
        if (max >= len + 10) return new Check(pair(gpu, box), Verdict.OK, "Karta është " + len + " mm, kasa pranon " + max + " mm: hyn.");
        if (max >= len) return new Check(pair(gpu, box), Verdict.TIGHT, "Karta është " + len + " mm dhe kasa pranon " + max + " mm: hyn ngushtë, kontrolloni kabllot e energjisë.");
        return new Check(pair(gpu, box), Verdict.NO, "Karta është " + len + " mm, kasa pranon vetëm " + max + " mm: nuk hyn.");
    }

    private static Check gpuPsu(Part gpu, Part psu) {
        Integer min = gpu.num("psu_min_watts"), w = psu.num("wattage");
        if (min == null) return unknown(gpu, psu, gpu, "furnizuesi minimal");
        if (w == null) return unknown(gpu, psu, psu, "fuqia (W)");
        String connectors = gpu.get("pcie_connectors") == null ? "" : " Karta kërkon " + gpu.get("pcie_connectors")
                + (psu.get("pcie_connectors") == null ? "; kontrolloni që furnizuesi i ka." : "; furnizuesi ka " + psu.get("pcie_connectors") + ".");
        if (w >= min) return new Check(pair(gpu, psu), Verdict.OK, w + " W mjaftojnë (minimumi " + min + " W)." + connectors);
        if (w >= min - 50) return new Check(pair(gpu, psu), Verdict.TIGHT, w + " W është në kufi: rekomandohen " + min + " W. Varet nga cilësia e furnizuesit dhe procesori." + connectors);
        return new Check(pair(gpu, psu), Verdict.NO, w + " W nuk mjaftojnë: karta kërkon të paktën " + min + " W.");
    }

    private static Check boardCase(Part board, Part box) {
        int b = boardSize(board.get("form_factor")), c = caseSize(box.get("form_factor"));
        if (b == 0) return unknown(board, box, board, "formati");
        if (c == 0) return unknown(board, box, box, "formati");
        if (b <= c) return new Check(pair(board, box), Verdict.OK, "Pllaka " + board.get("form_factor") + " hyn në kasë " + box.get("form_factor") + ".");
        if (b == 4 && c == 3) return new Check(pair(board, box), Verdict.TIGHT, "Pllakat E-ATX hyjnë vetëm në disa kasa Mid Tower: kontrolloni specifikimin e kasës.");
        return new Check(pair(board, box), Verdict.NO, "Kasa " + box.get("form_factor") + " nuk pranon pllaka " + board.get("form_factor") + ".");
    }

    private static int boardSize(String f) {
        String n = f == null ? "" : f.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return switch (n) {
            case "miniitx", "itx" -> 1;
            case "microatx", "matx" -> 2;
            case "atx" -> 3;
            case "eatx" -> 4;
            default -> 0;
        };
    }

    private static int caseSize(String f) {
        String n = f == null ? "" : f.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return switch (n) {
            case "miniitx", "itx" -> 1;
            case "microatx", "matx" -> 2;
            case "midtower", "atx" -> 3;
            case "fulltower", "eatx" -> 4;
            default -> 0;
        };
    }

    private static Check storageBoard(Part disk, Part board) {
        String type = disk.get("type");
        if (type == null || !type.toLowerCase(Locale.ROOT).contains("nvme")) return null;
        Integer slots = board.num("m2_slots");
        if (slots == null) return unknown(disk, board, board, "numri i sloteve M.2");
        if (slots > 0) return new Check(pair(disk, board), Verdict.OK, "Pllaka ka " + slots + " slot M.2 për diskun NVMe.");
        return new Check(pair(disk, board), Verdict.NO, "Pllaka nuk ka slot M.2: disku NVMe nuk lidhet dot.");
    }
}
