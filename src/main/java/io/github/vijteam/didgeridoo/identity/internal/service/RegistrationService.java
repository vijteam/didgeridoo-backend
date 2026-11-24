package io.github.vijteam.didgeridoo.identity.internal.service;

import io.github.vijteam.didgeridoo.identity.internal.exception.RegistrationConflictException;
import io.github.vijteam.didgeridoo.identity.internal.model.User;
import io.github.vijteam.didgeridoo.identity.internal.repository.UserRepository;
import io.github.vijteam.didgeridoo.identity.internal.validation.PasswordValidator;

import jakarta.transaction.Transactional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String username, String displayName, String email, String password) {
        PasswordValidator.validate(password);

        String passwordHash = passwordEncoder.encode(password);

        User user = new User(username, displayName, email, passwordHash);

        try {
            return userRepository.saveAndFlush(user);
        }
        catch (DataIntegrityViolationException e) {
            throw new RegistrationConflictException();
        }
    }
}
