package io.github.vijteam.didgeridoo.identity.internal.service;

import io.github.vijteam.didgeridoo.identity.api.UserDirectory;
import io.github.vijteam.didgeridoo.identity.api.UserSummary;
import io.github.vijteam.didgeridoo.identity.internal.model.User;
import io.github.vijteam.didgeridoo.identity.internal.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserDirectoryService implements UserDirectory {

    private final UserRepository userRepository;

    public UserDirectoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UserSummary> findById(UUID id) {
        Optional<User> optionalUser = userRepository.findById(id);

        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }

        User user = optionalUser.orElseThrow();

        UserSummary userSummary = toSummary(user);

        return Optional.of(userSummary);
    }

    @Override
    public Optional<UserSummary> findByUsername(String username) {
        Optional<User> optionalUser = userRepository.findByUsername(username);

        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }

        User user = optionalUser.orElseThrow();

        UserSummary userSummary = toSummary(user);

        return Optional.of(userSummary);
    }

    @Override
    public Map<UUID, UserSummary> findByIds(Set<UUID> ids) {
        Map<UUID, UserSummary> result = new HashMap<>();

        if (ids.isEmpty()) {
            return result;
        }

        for (User user : userRepository.findAllById(ids)) {
            UserSummary userSummary = toSummary(user);

            result.put(user.getId(), userSummary);
        }

        return result;
    }

    // TODO: реализовать передачу корректного avatarUrl при создании UserSummary
    private UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(), null);
    }
}
