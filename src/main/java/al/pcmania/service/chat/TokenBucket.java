package al.pcmania.service.chat;

/**
 * A requests-per-minute limiter for a provider's free tier: a bucket of {@code perMinute} tokens that
 * refills continuously at that rate. A request takes one token or is refused, and a refused request
 * falls through to the guided finder rather than waiting.
 */
public class TokenBucket {

    private final double capacity;
    private final double refillPerMs;
    private double tokens;
    private long last;

    public TokenBucket(int perMinute) {
        this.capacity = perMinute;
        this.refillPerMs = perMinute / 60_000.0;
        this.tokens = perMinute;
        this.last = System.nanoTime() / 1_000_000;
    }

    public synchronized boolean tryAcquire() {
        long now = System.nanoTime() / 1_000_000;
        tokens = Math.min(capacity, tokens + (now - last) * refillPerMs);
        last = now;
        if (tokens < 1) return false;
        tokens -= 1;
        return true;
    }

    /** Whether a request would be let through right now, without taking a token. */
    public synchronized boolean hasToken() {
        long now = System.nanoTime() / 1_000_000;
        return Math.min(capacity, tokens + (now - last) * refillPerMs) >= 1;
    }
}
