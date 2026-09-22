package al.pcmania.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter for public forms. Orders reserve stock immediately,
 * so this stops one client from reserving the whole catalogue with fake orders.
 */
@Service
public class RateLimiter {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /** Records a hit and returns false if the key already used {@code max} hits within {@code window}. */
    public boolean tryAcquire(String key, int max, Duration window) {
        Instant now = Instant.now();
        Deque<Instant> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(window))) q.pollFirst();
            if (q.size() >= max) return false;
            q.addLast(now);
            return true;
        }
    }
}
