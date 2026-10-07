package al.pcmania.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
@EnableScheduling
@Slf4j
public class KeepAwake {

    static final ZoneId ZONE = ZoneId.of("Europe/Tirane");

    static final Duration INTERVAL = Duration.ofMinutes(10);
    static final int FREE_HOURS_PER_MONTH = 750;

    private final boolean enabled;
    private final URI target;
    private final LocalTime from;
    private final LocalTime until;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public KeepAwake(AppProperties app, Environment env) {
        this.from = LocalTime.parse(env.getProperty("app.keep-awake.from", "06:00"));
        this.until = LocalTime.parse(env.getProperty("app.keep-awake.until", "01:00"));
        boolean wanted = env.getProperty("app.keep-awake.enabled", Boolean.class, true);
        this.enabled = wanted && app.base().startsWith("https://");
        this.target = URI.create(app.base() + "/healthz");
    }

    @EventListener(ApplicationReadyEvent.class)
    void announce() {
        if (!enabled) return;
        double perDay = hoursPerDay(from, until);
        log.info("Keep-awake: requesting {} every {} min between {} and {} ({}): {} h a day, about {} h in a 31-day month "
                        + "({}% of Render's {} free hours).", target, INTERVAL.toMinutes(), from, until, ZONE,
                perDay, Math.round(perDay * 31), Math.round(perDay * 31 * 100 / FREE_HOURS_PER_MONTH), FREE_HOURS_PER_MONTH);
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
    void ping() {
        if (!enabled || !inWindow(LocalTime.now(ZONE), from, until)) return;
        try {
            HttpResponse<Void> res = http.send(HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            log.debug("Keep-awake ping: {}", res.statusCode());
        } catch (Exception e) {

            log.warn("Keep-awake ping failed: {}", e.toString());
        }
    }

    static boolean inWindow(LocalTime now, LocalTime from, LocalTime until) {
        if (from.equals(until)) return true;
        return from.isBefore(until)
                ? !now.isBefore(from) && now.isBefore(until)
                : !now.isBefore(from) || now.isBefore(until);
    }

    static double hoursPerDay(LocalTime from, LocalTime until) {
        if (from.equals(until)) return 24;
        long minutes = Duration.between(from, until).toMinutes();
        return (minutes < 0 ? minutes + 24 * 60 : minutes) / 60.0;
    }
}
