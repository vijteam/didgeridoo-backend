package io.github.vijteam.didgeridoo.identity.internal.controller;

import io.github.vijteam.didgeridoo.identity.internal.service.RegistrationService;
import io.github.vijteam.didgeridoo.identity.internal.dto.RegisterRequest;
import io.github.vijteam.didgeridoo.identity.internal.dto.UserResponse;
import io.github.vijteam.didgeridoo.identity.internal.model.User;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @RequestBody RegisterRequest request
    ) {
        User user = registrationService.register(
                request.username(),
                request.displayName(),
                request.email(),
                request.password()
        );

        UserResponse response = new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
