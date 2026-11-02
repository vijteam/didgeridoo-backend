package io.github.vijteam.didgeridoo.identity.internal.repository;

import io.github.vijteam.didgeridoo.identity.internal.model.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query(
            value = "SELECT * FROM users WHERE username = CAST(:username AS citext)",
            nativeQuery = true)
    Optional<User> findByUsername(@Param("username") String username);

    @Query(
            value =
                    "SELECT EXISTS (SELECT 1 FROM users WHERE username = CAST(:username AS citext))",
            nativeQuery = true)
    boolean existsByUsername(@Param("username") String username);

    @Query(
            value = "SELECT EXISTS (SELECT 1 FROM users WHERE email = CAST(:email AS citext))",
            nativeQuery = true)
    boolean existsByEmail(@Param("email") String email);
}
