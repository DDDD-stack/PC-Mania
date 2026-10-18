package al.pcmania.service.notify;

public interface AdminNotifier {
    String name();
    boolean isEnabled();
    void notifyAdmin(String subject, String body);
}
