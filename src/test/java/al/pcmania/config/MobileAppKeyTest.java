package al.pcmania.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobileAppKeyTest {

    private static MobileAppKey key(String value) {
        return new MobileAppKey(new AppProperties("http://localhost", "PCMania", "./uploads", null, null, null,
                null, null, null, 500, new AppProperties.Admin("admin", null), value));
    }

    @Test
    void blankOrShortKeysAreSwitchedOff() {
        assertFalse(key(null).enabled());
        assertFalse(key("  ").enabled());
        assertFalse(key("too-short").enabled());
        assertFalse(key("too-short").matches("too-short"));
        assertFalse(key(null).matches(null));
    }

    @Test
    void onlyTheExactKeyMatches() {
        MobileAppKey k = key("0123456789abcdef0123456789abcdef");
        assertTrue(k.enabled());
        assertTrue(k.matches("0123456789abcdef0123456789abcdef"));
        assertFalse(k.matches("0123456789abcdef0123456789abcdeF"));
        assertFalse(k.matches(""));
        assertFalse(k.matches(null));
    }
}
