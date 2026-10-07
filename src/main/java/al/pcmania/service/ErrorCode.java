package al.pcmania.service;

import java.security.SecureRandom;

public final class ErrorCode {

    private static final String UNAMBIGUOUS_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ErrorCode() {
    }

    public static String next() {
        StringBuilder code = new StringBuilder("PM-");
        for (int i = 0; i < LENGTH; i++) {
            code.append(UNAMBIGUOUS_ALPHABET.charAt(RANDOM.nextInt(UNAMBIGUOUS_ALPHABET.length())));
        }
        return code.toString();
    }
}
