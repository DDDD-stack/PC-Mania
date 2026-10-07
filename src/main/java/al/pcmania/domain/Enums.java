package al.pcmania.domain;

public final class Enums {

    private Enums() {}

    public enum Condition {
        NEW("E re"), OPEN_BOX("Open box"), USED("E përdorur");
        public final String label;
        Condition(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum ProductStatus {
        DRAFT("Draft"), ACTIVE("Aktiv"), RESERVED("I rezervuar"), SOLD("I shitur"), HIDDEN("I fshehur");
        public final String label;
        ProductStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum DeliveryMethod {
        PICKUP_TIRANA("Marrje në Tiranë"), COURIER("Me korrier");
        public final String label;
        DeliveryMethod(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum PaymentMethod {
        CASH_ON_DELIVERY("Para në dorë në dorëzim"), BANK_TRANSFER("Transfertë bankare"),

        CARD_ONLINE("Pagesa me kartë");
        public final String label;
        PaymentMethod(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum OrderStatus {
        NEW("E re"), CONFIRMED("E konfirmuar"), SHIPPED("E dërguar"), DELIVERED("E dorëzuar"), CANCELLED("E anuluar");
        public final String label;
        OrderStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum UpcomingStatus {
        HIDDEN("I fshehur"), VISIBLE("I dukshëm"), ARRIVED("Ka ardhur");
        public final String label;
        UpcomingStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum UseCase {
        GAMING_1080P("Lojëra 1080p"), GAMING_1440P("Lojëra 1440p"), STREAMING("Streaming / krijim përmbajtjeje"),
        WORK_STUDY("Punë / studime"), OTHER("Tjetër");
        public final String label;
        UseCase(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum WishStatus {
        NEW("E re"), SEARCHING("Në kërkim"), FOUND("U gjet"), CLOSED("E mbyllur");
        public final String label;
        WishStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum ContactMethod {
        PHONE("Telefon"), EMAIL("Email");
        public final String label;
        ContactMethod(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum TradeItemType {
        GPU("Kartë grafike", "karta-grafike"), CPU("Procesor", "procesore"), RAM("Memorie RAM", "memorie-ram");
        public final String label;
        public final String categorySlug;
        TradeItemType(String label, String categorySlug) { this.label = label; this.categorySlug = categorySlug; }
        public String getLabel() { return label; }
    }

    public enum TradeMediaType { VIDEO, IMAGE }

    public enum TradeStatus {
        NEW("E re"), REVIEWING("Në shqyrtim"), QUOTED("Me ofertë"), ACCEPTED("E pranuar"),
        DECLINED("E refuzuar"), EXPIRED("E skaduar"), CONVERTED("U bë porosi");
        public final String label;
        TradeStatus(String label) { this.label = label; }
        public String getLabel() { return label; }

        public boolean isOpen() {
            return this == NEW || this == REVIEWING || this == QUOTED || this == ACCEPTED;
        }

        public boolean isClosed() {
            return this == DECLINED || this == EXPIRED || this == CONVERTED;
        }
    }

    public enum BuildStatus {
        NEW("E re"), QUOTED("Me ofertë"), ACCEPTED("E pranuar"), DECLINED("E refuzuar"), CLOSED("E mbyllur");
        public final String label;
        BuildStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum GpuVendor {
        NVIDIA("NVIDIA"), AMD("AMD"), INTEL("Intel");
        public final String label;
        GpuVendor(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum UpscalerVersion {
        NONE("Jo"), V1("1"), V2("2"), V3("3"), V4("4");
        public final String label;
        UpscalerVersion(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum DriverStatus {
        ACTIVE("Merr drajverë"), LEGACY("Legacy"), EOL("Pa drajverë të rinj");
        public final String label;
        DriverStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum MiningRisk {
        LOW("I ulët"), MEDIUM("Mesatar"), HIGH("I lartë");
        public final String label;
        MiningRisk(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum ChatRole { USER, ASSISTANT }

    public enum LeadSource {
        CHAT("Asistenti"), FINDER("Kërkimi i shpejtë"), FORM("Formular");
        public final String label;
        LeadSource(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum LeadStatus {
        NEW("E re"), CONTACTED("Kontaktuar"), CLOSED("E mbyllur");
        public final String label;
        LeadStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
}
