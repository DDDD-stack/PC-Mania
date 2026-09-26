package al.pcmania.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The phone app is only ever used by the shop's owner, so instead of a sign-in screen it carries one
 * long random key, built into the APK and set on the server as {@code MOBILE_API_KEY}. A request
 * bearing that key is treated like a signed-in admin on the /api routes, and nowhere else.
 *
 * The APK can only be downloaded from /admin, behind the admin login. If a phone with the app on it is
 * lost, changing MOBILE_API_KEY on the server locks that copy out at once; the app then has to be
 * rebuilt with the new key. Signing in with a username and password keeps working either way.
 */
@Component
@Slf4j
public class MobileAppKey {

    /** Anything shorter would be guessable; such a key is ignored rather than trusted. */
    static final int MIN_LENGTH = 32;

    private final byte[] key;

    public MobileAppKey(AppProperties props) {
        String configured = props.mobileApiKey();
        if (!StringUtils.hasText(configured)) {
            key = null;
        } else if (configured.trim().length() < MIN_LENGTH) {
            log.warn("MOBILE_API_KEY is shorter than {} characters and is ignored: the phone app will have to sign in.", MIN_LENGTH);
            key = null;
        } else {
            key = configured.trim().getBytes(StandardCharsets.UTF_8);
        }
    }

    public boolean enabled() {
        return key != null;
    }

    /** Constant-time comparison, so response timing gives nothing away about the key. */
    public boolean matches(String presented) {
        return key != null && presented != null
                && MessageDigest.isEqual(key, presented.trim().getBytes(StandardCharsets.UTF_8));
    }
}
