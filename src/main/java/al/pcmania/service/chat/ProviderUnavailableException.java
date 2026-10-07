package al.pcmania.service.chat;

public class ProviderUnavailableException extends Exception {

    private final boolean rateLimited;

    public ProviderUnavailableException(String message, boolean rateLimited) {
        super(message);
        this.rateLimited = rateLimited;
    }

    public ProviderUnavailableException(String message, boolean rateLimited, Throwable cause) {
        super(message, cause);
        this.rateLimited = rateLimited;
    }

    public boolean isRateLimited() {
        return rateLimited;
    }
}
