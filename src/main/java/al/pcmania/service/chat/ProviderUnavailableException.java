package al.pcmania.service.chat;

/** A provider could not take the request: rate limited, over its cap, unreachable or misconfigured. */
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

    /** True for a 429 or the local limiter: the request was fine, the quota was not. */
    public boolean isRateLimited() {
        return rateLimited;
    }
}
