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
}
