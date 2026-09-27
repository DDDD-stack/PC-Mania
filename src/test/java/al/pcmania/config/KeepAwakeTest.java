package al.pcmania.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class KeepAwakeTest {

    private static final LocalTime SIX = LocalTime.of(6, 0), ONE = LocalTime.of(1, 0);

    @Test
    void windowRunsPastMidnight() {
        assertTrue(KeepAwake.inWindow(LocalTime.of(6, 0), SIX, ONE));
        assertTrue(KeepAwake.inWindow(LocalTime.of(14, 30), SIX, ONE));
        assertTrue(KeepAwake.inWindow(LocalTime.of(23, 59), SIX, ONE));
        assertTrue(KeepAwake.inWindow(LocalTime.of(0, 50), SIX, ONE));
        assertFalse(KeepAwake.inWindow(LocalTime.of(1, 0), SIX, ONE));
        assertFalse(KeepAwake.inWindow(LocalTime.of(3, 0), SIX, ONE));
        assertFalse(KeepAwake.inWindow(LocalTime.of(5, 59), SIX, ONE));
    }

    @Test
    void windowWithinOneDay() {
        LocalTime eight = LocalTime.of(8, 0), ten = LocalTime.of(22, 0);
        assertTrue(KeepAwake.inWindow(LocalTime.NOON, eight, ten));
        assertFalse(KeepAwake.inWindow(LocalTime.of(23, 0), eight, ten));
        assertFalse(KeepAwake.inWindow(LocalTime.of(7, 0), eight, ten));
    }

    /** The default window is the 80% budget: 19 h a day, 589 h in a 31-day month, under 600 of 750. */
    @Test
    void defaultWindowStaysWithinEightyPercentOfTheFreeHours() {
        assertEquals(19.0, KeepAwake.hoursPerDay(SIX, ONE));
        assertTrue(KeepAwake.hoursPerDay(SIX, ONE) * 31 <= 0.8 * KeepAwake.FREE_HOURS_PER_MONTH);
        assertEquals(24.0, KeepAwake.hoursPerDay(SIX, SIX));
    }

    @Test
    void onlyRunsWhenDeployed() {
        assertFalse(enabled("http://localhost:8070", "true"), "local runs must not ping");
        assertTrue(enabled("https://pc-mania.onrender.com", "true"));
        assertFalse(enabled("https://pc-mania.onrender.com", "false"), "KEEP_AWAKE=false turns it off");
    }

    private static boolean enabled(String base, String flag) {
        AppProperties app = new AppProperties(base, "PCMania", "./uploads", null, null, null, null, null, null, 500,
                new AppProperties.Admin("admin", null), null);
        KeepAwake k = new KeepAwake(app, new MockEnvironment().withProperty("app.keep-awake.enabled", flag));
        try {
            var f = KeepAwake.class.getDeclaredField("enabled");
            f.setAccessible(true);
            return f.getBoolean(k);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
