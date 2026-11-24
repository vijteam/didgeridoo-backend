package io.github.vijteam.didgeridoo.identity.internal.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.identity.internal.model.User;
import io.github.vijteam.didgeridoo.identity.internal.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
public class RegistrationServiceTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext private EntityManager entityManager;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void shouldSuccessfullyRegisterUserWithCorrectProvidedData() {
        String password = "correct_paSSword_123!";

        var user = registrationService.register(
                "Correct.Username",
                "Correct display name",
                "correct.email@example.com",
                password
        );

        entityManager.flush();
        entityManager.clear();

        User savedUser = userRepository.findById(user.getId()).orElseThrow();

        assertTrue(passwordEncoder.matches(
                password,
                savedUser.getPasswordHash()
        ));
    }

    @Test
    void shouldRejectRegistrationOfUserWithExistingEmail() {
        String email = "equal@example.com";

        registrationService.register(
            "first_user",
            "Correct display name",
            email,
            "correct_paSSword_123!"
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> registrationService.register(
                "second_user",
                "Correct display name",
                email.toUpperCase(),
                "correct_paSSword_123!"
            )
        );
    }

    @Test
    void shouldRejectRegistrationOfUserWithExistingUsername() {
        String username = "equal.username";

        registrationService.register(
            username.toUpperCase(),
            "Correct display name",
            "email1@example.com",
            "correct_paSSword_123!"
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> registrationService.register(
                username,
                "Correct display name",
                "email2@example.com",
                "correct_paSSword_123!"
            )
        );
    }
}
