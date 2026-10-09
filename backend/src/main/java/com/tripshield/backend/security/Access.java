package com.tripshield.backend.security;

import com.tripshield.backend.model.Trip;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class Access {
    private final HttpServletRequest request;
    private final AppSessionRepository sessions;
    private final UserRepository users;
    private final SecureRandom random = new SecureRandom();

    public Access(HttpServletRequest request, AppSessionRepository sessions, UserRepository users) {
        this.request = request;
        this.sessions = sessions;
        this.users = users;
    }

    public String issue(AppUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AppSession session = new AppSession();
        session.tokenHash = hash(token);
        session.userId = user.id;
        session.expiresAt = Instant.now().plus(12, ChronoUnit.HOURS);
        sessions.save(session);
        return token;
    }

    public AppUser current() {
        String token = bearerToken();
        AppSession session = sessions.findByTokenHash(hash(token))
                .orElseThrow(() -> unauthorized("Session expired. Please sign in again."));
        if (!session.expiresAt.isAfter(Instant.now())) {
            sessions.delete(session);
            throw unauthorized("Session expired. Please sign in again.");
        }
        return users.findById(session.userId)
                .orElseThrow(() -> unauthorized("Account unavailable"));
    }

    public void logout() {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            sessions.findByTokenHash(hash(header.substring(7))).ifPresent(sessions::delete);
        }
    }

    public void coordinator() {
        if (!"COORDINATOR".equals(current().role))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Coordinator access required");
    }

    public boolean canView(Trip trip) {
        AppUser user = current();
        return "COORDINATOR".equals(user.role)
                || user.email.equalsIgnoreCase(trip.travelerEmail == null ? "" : trip.travelerEmail);
    }

    public void view(Trip trip) {
        if (!canView(trip))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Trip access denied");
    }

    private String bearerToken() {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7)
            throw unauthorized("Sign in required");
        return header.substring(7);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
