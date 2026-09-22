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
        CASH_ON_DELIVERY("Para në dorë në dorëzim"), BANK_TRANSFER("Transfertë bankare");
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

    public enum BuildStatus {
        NEW("E re"), QUOTED("Me ofertë"), ACCEPTED("E pranuar"), DECLINED("E refuzuar"), CLOSED("E mbyllur");
        public final String label;
        BuildStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
}
