package io.github.vijteam.didgeridoo.spaces.internal.controller;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SpaceSessionSecurity {
    @Bean
    @Order(10)
    SecurityFilterChain spaceSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/api/v1/spaces",
                        "/api/v1/spaces/{spaceId}",
                        "/api/v1/spaces/{spaceId}/memberships",
                        "/api/v1/spaces/{spaceId}/invitations",
                        "/api/v1/invitations/{invitationId}",
                        "/api/v1/users/me/invitations")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .csrf(csrf -> {})
                .requestCache(cache -> cache.disable())
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                                (request, response, failure) ->
                                                        error(response, 401, "UNAUTHENTICATED"))
                                        .accessDeniedHandler(
                                                (request, response, failure) -> {
                                                    var auth =
                                                            SecurityContextHolder.getContext()
                                                                    .getAuthentication();
                                                    boolean loggedIn =
                                                            auth != null
                                                                    && auth.isAuthenticated()
                                                                    && !(auth
                                                                            instanceof
                                                                            AnonymousAuthenticationToken);
                                                    error(
                                                            response,
                                                            loggedIn ? 403 : 401,
                                                            loggedIn
                                                                    ? "INVALID_CSRF_TOKEN"
                                                                    : "UNAUTHENTICATED");
                                                }));
        return http.build();
    }

    private static void error(HttpServletResponse response, int status, String code)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write("{\"code\":\"" + code + "\",\"message\":\"Request is not authorized\"}");
    }
}
