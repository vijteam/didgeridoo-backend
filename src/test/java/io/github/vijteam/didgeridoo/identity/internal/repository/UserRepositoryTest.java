package io.github.vijteam.didgeridoo.identity.internal.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.identity.internal.model.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserRepositoryTest {

    @Autowired private UserRepository userRepository;

    @PersistenceContext private EntityManager entityManager;

    @Test
    void shouldReturnEmptyForUnknownUsername() {
        Optional<User> result = userRepository.findByUsername("nonexistent_repo_user_98765");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindExistingUsernameIgnoringCase() {
        User user = new User("RepoAlice", "Alice", "alice.repo@example.com", "hash");

        userRepository.saveAndFlush(user);

        assertTrue(userRepository.existsByUsername("repoalice"));
        assertTrue(userRepository.existsByUsername("REPOALICE"));
        assertFalse(userRepository.existsByUsername("another_repo_user"));

        User found = userRepository.findByUsername("repoalice").orElseThrow();

        assertEquals("RepoAlice", found.getUsername());
    }

    @Test
    void shouldFindExistingEmailIgnoringCase() {
        User user = new User("RepoBob", "Bob", "Bob.Repo@Example.com", "hash");

        userRepository.saveAndFlush(user);

        assertTrue(userRepository.existsByEmail("bob.repo@example.com"));
        assertTrue(userRepository.existsByEmail("BOB.REPO@EXAMPLE.COM"));
        assertFalse(userRepository.existsByEmail("unknown.repo@example.com"));
    }

    @Test
    void shouldRejectDuplicateUsernameIgnoringCase() {
        User first = new User("RepoCharlie", "Charlie", "charlie.first@example.com", "hash");

        User second =
                new User("repocharlie", "Another Charlie", "charlie.second@example.com", "hash");

        userRepository.saveAndFlush(first);

        assertThrows(
                DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(second));
    }

    @Test
    void shouldRejectDuplicateEmailIgnoringCase() {
        User first = new User("RepoDavid1", "David", "David.Repo@Example.com", "hash");

        User second = new User("RepoDavid2", "Another David", "david.repo@example.com", "hash");

        userRepository.saveAndFlush(first);

        assertThrows(
                DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(second));
    }

    @Test
    void shouldPersistAndLoadUserFields() {
        User user =
                new User(
                        "RepoEdward", "Edward",
                        "edward.repo@example.com", "stored-hash");

        userRepository.saveAndFlush(user);

        var id = user.getId();
        assertNotNull(id);

        entityManager.clear();

        User loaded = userRepository.findById(id).orElseThrow();

        assertEquals(id, loaded.getId());
        assertEquals("RepoEdward", loaded.getUsername());
        assertEquals("Edward", loaded.getDisplayName());
        assertEquals("edward.repo@example.com", loaded.getEmail());
        assertEquals("stored-hash", loaded.getPasswordHash());

        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
    }
}
