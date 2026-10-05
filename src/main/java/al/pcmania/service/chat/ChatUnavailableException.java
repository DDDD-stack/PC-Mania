package al.pcmania.service.chat;

/** The model could not answer right now: rate limited, overloaded, unreachable, or misconfigured. */
public class ChatUnavailableException extends RuntimeException {

    /** True when trying again in a moment may well work (rate limit, overload, network). */
    private final boolean transientFailure;

    public ChatUnavailableException(String message, boolean transientFailure, Throwable cause) {
        super(message, cause);
        this.transientFailure = transientFailure;
    }

    public boolean isTransientFailure() {
        return transientFailure;
    }
}
