package com.tripshield.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccessTest {
    @Test
    void issuedTokenIsStoredAsHashAndResolvesUser() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        AppSessionRepository sessions = mock(AppSessionRepository.class);
        UserRepository users = mock(UserRepository.class);
        AppUser user = new AppUser(); user.id = 17L; user.role = "TRAVELER";
        Access access = new Access(request, sessions, users);
        String token = access.issue(user);
        assertNotEquals(token, "");
        var captured = org.mockito.ArgumentCaptor.forClass(AppSession.class);
        verify(sessions).save(captured.capture());
        assertEquals(64, captured.getValue().tokenHash.length());
        assertFalse(captured.getValue().tokenHash.contains(token));
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(sessions.findByTokenHash(captured.getValue().tokenHash)).thenReturn(Optional.of(captured.getValue()));
        when(users.findById(17L)).thenReturn(Optional.of(user));
        assertSame(user, access.current());
    }

    @Test
    void expiredSessionIsRejectedAndRemoved() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        AppSessionRepository sessions = mock(AppSessionRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer expired-token");
        AppSession expired = new AppSession(); expired.expiresAt = Instant.now().minusSeconds(1);
        when(sessions.findByTokenHash(any())).thenReturn(Optional.of(expired));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> new Access(request, sessions, users).current());
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verify(sessions).delete(expired);
    }
}
