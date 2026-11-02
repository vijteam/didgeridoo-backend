package io.github.vijteam.didgeridoo.identity.internal.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class UserFieldValidator {

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z0-9]*(?:[._-][A-Za-z0-9]+)*$");

    // TODO 1: расширить формат email (включая не только email на латинице)
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile(
                    "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+"
                            + "(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                            + "@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"
                            + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*$");

    private static final Set<String> RESERVED_USERNAMES =
            Set.of("admin", "root", "support", "system", "didgeridoo");

    private UserFieldValidator() {}

    static String username(String value) {
        if (value == null || value.length() < 3 || value.length() > 30) {
            throw new IllegalArgumentException("Invalid username length");
        }

        if (!USERNAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid username format");
        }

        if (RESERVED_USERNAMES.contains(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Reserved username");
        }

        return value;
    }

    static String displayName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Display name is required");
        }

        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);

            if (Character.isISOControl(cp) || isBidiControl(cp)) {
                throw new IllegalArgumentException(
                        "Display name contains forbidden control characters");
            }

            i += Character.charCount(cp);
        }

        String normalized = Normalizer.normalize(stripOuterSpaces(value), Normalizer.Form.NFC);

        int length = normalized.codePointCount(0, normalized.length());

        if (length < 1 || length > 100) {
            throw new IllegalArgumentException("Invalid display name length");
        }

        return normalized;
    }

    static String email(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Email is required");
        }

        String normalized = stripOuterSpaces(value);

        if (normalized.isEmpty() || normalized.length() > 254) {
            throw new IllegalArgumentException("Invalid email length");
        }

        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }

        return normalized;
    }

    static String passwordHash(String value) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException("Invalid password hash");
        }

        return value;
    }

    private static String stripOuterSpaces(String value) {
        int start = 0;
        int end = value.length();

        while (start < end) {
            int cp = value.codePointAt(start);

            if (!Character.isSpaceChar(cp)) {
                break;
            }

            start += Character.charCount(cp);
        }

        while (start < end) {
            int cp = value.codePointBefore(end);

            if (!Character.isSpaceChar(cp)) {
                break;
            }

            end -= Character.charCount(cp);
        }

        return value.substring(start, end);
    }

    // Проверяет, является ли символ управляющим символом двунаправленного текста
    private static boolean isBidiControl(int cp) {
        return cp == 0x061C
                || cp == 0x200E
                || cp == 0x200F
                || (cp >= 0x202A && cp <= 0x202E)
                || (cp >= 0x2066 && cp <= 0x2069);
    }
}
