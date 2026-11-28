package io.github.vijteam.didgeridoo.spaces.api;

import java.text.Normalizer;

public record SpaceName(String value) {
    public SpaceName {
        value = normalize(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Space name is required");
        }
        value.codePoints()
                .forEach(
                        cp -> {
                            if (isForbiddenCodePoint(cp)) {
                                throw new IllegalArgumentException(
                                        "Space name contains forbidden or malformed characters");
                            }
                        });
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (start < end && isSpace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        String normalized = Normalizer.normalize(value.substring(start, end), Normalizer.Form.NFC);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 150) {
            throw new IllegalArgumentException("Space name must contain 1 to 150 characters");
        }
        return normalized;
    }

    private static boolean isForbiddenCodePoint(int cp) {
        boolean unpairedSurrogate = cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE;
        return Character.isISOControl(cp) || isBidiControl(cp) || unpairedSurrogate;
    }

    private static boolean isSpace(int cp) {
        return Character.isSpaceChar(cp) || Character.isWhitespace(cp);
    }

    private static boolean isBidiControl(int cp) {
        return cp == 0x061C
                || cp == 0x200E
                || cp == 0x200F
                || (cp >= 0x202A && cp <= 0x202E)
                || (cp >= 0x2066 && cp <= 0x2069);
    }
}
