package io.github.vijteam.didgeridoo.identity.internal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// TODO 2: добавить тесты на расширенный формат email
class UserTest {

    private User createUser(String username, String displayName, String email) {
        return new User(username, displayName, email, "stored-hash");
    }

    @Test
    void shouldCreateUserWithValidFields() {
        User user = createUser("Alex_123", "Alex", "Alex@Example.com");

        assertEquals("Alex_123", user.getUsername());
        assertEquals("Alex", user.getDisplayName());
        assertEquals("Alex@Example.com", user.getEmail());
        assertEquals("stored-hash", user.getPasswordHash());

        assertNull(user.getId());
        assertNull(user.getEmailVerifiedAt());
        assertNull(user.getAvatarStorageKey());
        assertNull(user.getCreatedAt());
        assertNull(user.getUpdatedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abc", "Alex_123", "a.b-c_d9", "A12345678901234567890123456789"})
    void shouldAcceptValidUsername(String username) {
        User user = createUser(username, "Alex", "alex@example.com");

        assertEquals(username, user.getUsername());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "a",
                "ab",
                "1alex",
                "alex.",
                "alex_",
                "alex-",
                "alex..bob",
                "alex__bob",
                "alex--bob",
                "alex.-bob",
                "alex bob",
                " alex",
                "alex ",
                "алекс",
                "admin",
                "ROOT",
                "Support",
                "SYSTEM",
                "Didgeridoo"
            })
    void shouldRejectInvalidUsername(String username) {
        assertThrows(
                IllegalArgumentException.class,
                () -> createUser(username, "Alex", "alex@example.com"));
    }

    @Test
    void shouldRejectUsernameLongerThan30Characters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createUser("a".repeat(31), "Alex", "alex@example.com"));
    }

    @Test
    void shouldChangeUsername() {
        User user = createUser("Alex_123", "Alex", "alex@example.com");

        user.changeUsername("New.Name-2");

        assertEquals("New.Name-2", user.getUsername());
    }

    @Test
    void shouldKeepUsernameAfterInvalidChange() {
        User user = createUser("Alex_123", "Alex", "alex@example.com");

        assertThrows(IllegalArgumentException.class, () -> user.changeUsername("admin"));

        assertEquals("Alex_123", user.getUsername());
    }

    @Test
    void shouldTrimAndNormalizeDisplayNameToNfc() {
        User user = createUser("alex", "\u00A0 Cafe\u0301 \u00A0", "alex@example.com");

        assertEquals("Café", user.getDisplayName());
    }

    @Test
    void shouldAcceptMultilingualDisplayName() {
        String displayName = "Иван أحمد (Alex) 👩‍💻";

        User user = createUser("alex", displayName, "alex@example.com");

        assertEquals(displayName, user.getDisplayName());
    }

    @Test
    void shouldAcceptDisplayNameOf100CodePoints() {
        String displayName = "😀".repeat(100);

        User user = createUser("alex", displayName, "alex@example.com");

        assertEquals(displayName, user.getDisplayName());
    }

    @Test
    void shouldCheckDisplayNameLengthAfterNfcNormalization() {
        String displayName = "e\u0301".repeat(100);

        User user = createUser("alex", displayName, "alex@example.com");

        assertEquals("é".repeat(100), user.getDisplayName());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\u00A0"})
    void shouldRejectEmptyDisplayName(String displayName) {
        assertThrows(
                IllegalArgumentException.class,
                () -> createUser("alex", displayName, "alex@example.com"));
    }

    @Test
    void shouldRejectDisplayNameLongerThan100CodePoints() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createUser("alex", "😀".repeat(101), "alex@example.com"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x0000, 0x0009, 0x000A, 0x000D, 0x007F, 0x0085})
    void shouldRejectControlCharactersInDisplayName(int codePoint) {
        String displayName = "Alex" + new String(Character.toChars(codePoint));

        assertThrows(
                IllegalArgumentException.class,
                () -> createUser("alex", displayName, "alex@example.com"));
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067,
                0x2068, 0x2069
            })
    void shouldRejectAllBidiControlCharactersInDisplayName(int codePoint) {
        String displayName = "Alex" + new String(Character.toChars(codePoint));

        assertThrows(
                IllegalArgumentException.class,
                () -> createUser("alex", displayName, "alex@example.com"));
    }

    @Test
    void shouldChangeAndNormalizeDisplayName() {
        User user = createUser("alex", "Alex", "alex@example.com");

        user.changeDisplayName("  Cafe\u0301  ");

        assertEquals("Café", user.getDisplayName());
    }

    @Test
    void shouldKeepDisplayNameAfterInvalidChange() {
        User user = createUser("alex", "Alex", "alex@example.com");

        assertThrows(IllegalArgumentException.class, () -> user.changeDisplayName("   "));

        assertEquals("Alex", user.getDisplayName());
    }

    @Test
    void shouldTrimEmailButPreserveItsCase() {
        User user = createUser("alex", "Alex", " \u00A0Alex+tag@Example.COM\u00A0 ");

        assertEquals("Alex+tag@Example.COM", user.getEmail());
    }

    @Test
    void shouldAcceptEmailOf254Characters() {
        String email =
                "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);

        assertEquals(254, email.length());
        assertEquals(email, createUser("alex", "Alex", email).getEmail());
    }

    @Test
    void shouldRejectEmailLongerThan254Characters() {
        String email =
                "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(62);

        assertEquals(255, email.length());
        assertThrows(IllegalArgumentException.class, () -> createUser("alex", "Alex", email));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  \u00A0 "})
    void shouldRejectEmptyEmail(String email) {
        assertThrows(IllegalArgumentException.class, () -> createUser("alex", "Alex", email));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "alexexample.com",
                "alex@",
                "@example.com",
                "alex..bob@example.com",
                "alex bob@example.com",
                "alex@-example.com",
                "alex@example..com",
                "алекс@example.com"
            })
    void shouldRejectInvalidEmailFormat(String email) {
        assertThrows(IllegalArgumentException.class, () -> createUser("alex", "Alex", email));
    }

    @Test
    void shouldAcceptPasswordHashOf255Characters() {
        String passwordHash = "h".repeat(255);

        User user = new User("alex", "Alex", "alex@example.com", passwordHash);

        assertEquals(passwordHash, user.getPasswordHash());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void shouldRejectBlankPasswordHash(String passwordHash) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new User("alex", "Alex", "alex@example.com", passwordHash));
    }

    @Test
    void shouldRejectPasswordHashLongerThan255Characters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new User("alex", "Alex", "alex@example.com", "h".repeat(256)));
    }
}
