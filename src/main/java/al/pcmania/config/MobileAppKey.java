package al.pcmania.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Slf4j
public class MobileAppKey {

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

    public boolean matches(String presented) {
        return key != null && presented != null
                && MessageDigest.isEqual(key, presented.trim().getBytes(StandardCharsets.UTF_8));
    }
}
