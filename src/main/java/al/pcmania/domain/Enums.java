package al.pcmania.domain;

/** Domain enums, each carrying its Albanian display label. */
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
        /** Not chargeable yet: see PaymentProvider. Checkout shows it greyed out and refuses it. */
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

    /** Lifecycle of a "Së shpejti" teaser. */
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

    /** Lifecycle of a customer's request for a product the shop does not stock yet. */
    public enum WishStatus {
        NEW("E re"), SEARCHING("Në kërkim"), FOUND("U gjet"), CLOSED("E mbyllur");
        public final String label;
        WishStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /** How a trade-in customer wants to hear back: exactly one of phone or email is kept. */
    public enum ContactMethod {
        PHONE("Telefon"), EMAIL("Email");
        public final String label;
        ContactMethod(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /** What can be traded in. {@link #categorySlug} is where it goes when taken into stock. */
    public enum TradeItemType {
        GPU("Kartë grafike", "karta-grafike"), CPU("Procesor", "procesore"), RAM("Memorie RAM", "memorie-ram");
        public final String label;
        public final String categorySlug;
        TradeItemType(String label, String categorySlug) { this.label = label; this.categorySlug = categorySlug; }
        public String getLabel() { return label; }
    }

    public enum TradeMediaType { VIDEO, IMAGE }

    /**
     * Lifecycle of a trade-in quote request. Submitting one never creates an order or reserves stock:
     * the order is made only once the operator has quoted and the customer has accepted.
     */
    public enum TradeStatus {
        NEW("E re"), REVIEWING("Në shqyrtim"), QUOTED("Me ofertë"), ACCEPTED("E pranuar"),
        DECLINED("E refuzuar"), EXPIRED("E skaduar"), CONVERTED("U bë porosi");
        public final String label;
        TradeStatus(String label) { this.label = label; }
        public String getLabel() { return label; }

        /** Still needs the operator: shown on the dashboard. */
        public boolean isOpen() {
            return this == NEW || this == REVIEWING || this == QUOTED || this == ACCEPTED;
        }

        /** Finished either way; the proof media is deleted some time after this. */
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

    // ---- GPU catalogue ----

    public enum GpuVendor {
        NVIDIA("NVIDIA"), AMD("AMD"), INTEL("Intel");
        public final String label;
        GpuVendor(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /** Which generation of an upscaler (DLSS or FSR) a card supports; NONE when it has no support at all. */
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

    /** How likely a used card of this model is to have been mined on. */
    public enum MiningRisk {
        LOW("I ulët"), MEDIUM("Mesatar"), HIGH("I lartë");
        public final String label;
        MiningRisk(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ---- Customer assistant ----

    public enum ChatRole { USER, ASSISTANT }

    /** Where a lead came from: the chat's contact form, the guided finder, or a plain form on the site. */
    public enum LeadSource {
        CHAT("Asistenti"), FINDER("Kërkimi i shpejtë"), FORM("Formular");
        public final String label;
        LeadSource(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /** Lifecycle of a lead the assistant captured: the operator calls the customer back. */
    public enum LeadStatus {
        NEW("E re"), CONTACTED("Kontaktuar"), CLOSED("E mbyllur");
        public final String label;
        LeadStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
}
