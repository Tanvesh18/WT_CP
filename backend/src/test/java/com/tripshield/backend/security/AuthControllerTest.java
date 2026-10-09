package com.tripshield.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    @Test
    void registrationSavesHashedPasswordAndReturnsSession() {
        UserRepository users = mock(UserRepository.class);
        Access access = mock(Access.class);
        when(users.findByEmailIgnoreCase("traveler@example.com")).thenReturn(Optional.empty());
        when(users.save(any(AppUser.class))).thenAnswer(call -> {
            AppUser user = call.getArgument(0);
            user.id = 1L;
            return user;
        });
        when(access.issue(any(AppUser.class))).thenReturn("test-token");
        AuthController controller = new AuthController(users, access);

        AuthController.Session result = controller.register(
                new AuthController.Registration("  Test Traveler  ", "Traveler@Example.com", "correct-horse-123"));

        assertEquals("test-token", result.token());
        assertEquals("TRAVELER", result.role());
        assertEquals("traveler@example.com", result.email());
        assertEquals("Test Traveler", result.name());
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        AppUser saved = captor.getValue();
        assertNotEquals("correct-horse-123", saved.passwordHash);
        assertTrue(new BCryptPasswordEncoder().matches("correct-horse-123", saved.passwordHash));
    }
    @Test
    void duplicateEmailIsRejected() {
        UserRepository users = mock(UserRepository.class);
        Access access = mock(Access.class);
        when(users.findByEmailIgnoreCase("traveler@example.com")).thenReturn(Optional.of(new AppUser()));
        AuthController controller = new AuthController(users, access);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                controller.register(new AuthController.Registration("Traveler", "traveler@example.com", "correct-horse-123")));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(users, never()).save(any(AppUser.class));
    }
}
