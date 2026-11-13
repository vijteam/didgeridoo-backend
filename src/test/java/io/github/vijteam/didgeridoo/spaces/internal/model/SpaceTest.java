package io.github.vijteam.didgeridoo.spaces.internal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SpaceTest {

    @Test
    void shouldTrimNameAndLeaveGeneratedFieldsUnset() {
        Space space = new Space("  Team space  ");

        assertEquals("Team space", space.getName());
        assertNull(space.getId());
        assertNull(space.getCreatedAt());
        assertNull(space.getUpdatedAt());
    }

    @Test
    void shouldValidateNameLengthAfterTrimming() {
        String name = "  " + "a".repeat(150) + "  ";

        Space space = new Space(name);

        assertEquals("a".repeat(150), space.getName());
    }

    @Test
    void shouldRejectNameContainingOnlyWhitespace() {
        assertThrows(IllegalArgumentException.class, () -> new Space("   "));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void shouldRejectMissingOrBlankName(String name) {
        assertThrows(IllegalArgumentException.class, () -> new Space(name));
    }

    @Test
    void shouldAcceptMaximumLengthName() {
        String name = "a".repeat(150);

        assertEquals(name, new Space(name).getName());
    }

    @Test
    void shouldRejectOverlongName() {
        assertThrows(IllegalArgumentException.class, () -> new Space("a".repeat(151)));
    }

    @Test
    void shouldCountSupplementaryCharactersAsSingleCharacters() {
        String symbol = new String(Character.toChars(0x1F680));
        String name = symbol.repeat(150);

        assertEquals(name, new Space(name).getName());
        assertThrows(IllegalArgumentException.class, () -> new Space(symbol.repeat(151)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u00A0", "\u2003", "\u202F", "\u3000"})
    void shouldTrimUnicodeSpaces(String whitespace) {
        assertEquals("Team space", new Space(whitespace + "Team space" + whitespace).getName());
        assertThrows(IllegalArgumentException.class, () -> new Space(whitespace));
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                0, 9, 10, 27, 127, 0x85, 0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D,
                0x202E, 0x2066, 0x2067, 0x2068, 0x2069
            })
    void shouldRejectControlsBeforeTrimming(int codePoint) {
        String control = new String(Character.toChars(codePoint));
        assertThrows(IllegalArgumentException.class, () -> new Space(control + "Team"));
        assertThrows(IllegalArgumentException.class, () -> new Space("Team" + control));
        assertThrows(IllegalArgumentException.class, () -> new Space("Te" + control + "am"));
    }

    @Test
    void shouldNormalizeBeforeCheckingLength() {
        assertEquals("é".repeat(150), new Space("e\u0301".repeat(150)).getName());
        assertEquals("Команда 🚀", new Space("Команда 🚀").getName());
    }

    @ParameterizedTest
    @ValueSource(ints = {0xD800, 0xDBFF, 0xDC00, 0xDFFF})
    void shouldRejectUnpairedSurrogates(int codePoint) {
        String malformed = new String(Character.toChars(codePoint));
        assertThrows(IllegalArgumentException.class, () -> new Space(malformed));
        assertThrows(IllegalArgumentException.class, () -> new Space("Team" + malformed));
    }
}
