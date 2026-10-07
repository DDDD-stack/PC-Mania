package al.pcmania.service.chat;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class PiiStripper {

    public static final String PHONE_PLACEHOLDER = "[numër telefoni hequr]";
    public static final String EMAIL_PLACEHOLDER = "[email hequr]";

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern PHONE_LIKE = Pattern.compile("\\+?\\d(?:[\\d\\s().-]{6,}\\d)");

    private PiiStripper() {}

    public record Result(String text, boolean stripped) {}

    public static Result strip(String input) {
        if (input == null) return new Result("", false);
        boolean[] stripped = {false};
        String out = EMAIL.matcher(input).replaceAll(m -> {
            stripped[0] = true;
            return EMAIL_PLACEHOLDER;
        });
        Matcher m = PHONE_LIKE.matcher(out);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            String run = m.group();
            long digits = run.chars().filter(Character::isDigit).count();
            if (digits >= 9) {
                stripped[0] = true;
                m.appendReplacement(b, Matcher.quoteReplacement(PHONE_PLACEHOLDER));
            } else {
                m.appendReplacement(b, Matcher.quoteReplacement(run));
            }
        }
        m.appendTail(b);
        if (stripped[0]) log.info("Chat input: a phone number or email address was stripped before the provider saw it.");
        return new Result(b.toString(), stripped[0]);
    }
}
