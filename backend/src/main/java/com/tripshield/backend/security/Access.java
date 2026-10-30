package com.tripshield.backend.security;

import com.tripshield.backend.model.Trip;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
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
    public static final String SESSION_COOKIE = "TRIPSHIELD_SESSION";
    public static final String CSRF_COOKIE = "TRIPSHIELD_CSRF";
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
        String token = sessionToken();
        AppSession session = sessions.findByTokenHash(hash(token))
                .orElseThrow(() -> unauthorized("Session expired. Please sign in again."));
        if (!session.expiresAt.isAfter(Instant.now())) {
            sessions.delete(session);
            throw unauthorized("Session expired. Please sign in again.");
        }
        if (cookieAuthentication() && unsafeMethod()) verifyCsrf(session);
        return users.findById(session.userId)
                .orElseThrow(() -> unauthorized("Account unavailable"));
    }

    public void logout() {
        String token = optionalSessionToken();
        if (token == null) return;
        sessions.findByTokenHash(hash(token)).ifPresent(session -> {
            if (cookieAuthentication()) verifyCsrf(session);
            sessions.delete(session);
        });
    }

    public void invalidateExisting() {
        String token = optionalSessionToken();
        if (token != null) sessions.findByTokenHash(hash(token)).ifPresent(sessions::delete);
    }

    public String csrfToken(String token) { return hash(hash(token) + ":tripshield-csrf"); }

    public void coordinator() {
        if (!"COORDINATOR".equals(current().role))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Coordinator access required");
    }

    public boolean canView(Trip trip) {
        AppUser user = current();
        return "COORDINATOR".equals(user.role)
                || (trip.ownerUserId != null && trip.ownerUserId.equals(user.id));
    }

    public void view(Trip trip) {
        if (!canView(trip))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Trip access denied");
    }

    private String sessionToken() {
        String token = optionalSessionToken();
        if (token == null) throw unauthorized("Sign in required");
        return token;
    }

    private String optionalSessionToken() {
        String header = request.getHeader("Authorization");
        if (header != null) return header.startsWith("Bearer ") && header.length() > 7 ? header.substring(7) : null;
        return cookieValue(SESSION_COOKIE);
    }

    private boolean cookieAuthentication() { return request.getHeader("Authorization") == null && cookieValue(SESSION_COOKIE) != null; }
    private boolean unsafeMethod() { return !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()); }
    private String cookieValue(String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
    private void verifyCsrf(AppSession session) {
        String header = request.getHeader("X-TripShield-CSRF");
        String cookie = cookieValue(CSRF_COOKIE);
        String expected = hash(session.tokenHash + ":tripshield-csrf");
        if (header == null || cookie == null || !constantEquals(header, cookie) || !constantEquals(header, expected))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CSRF token missing or invalid");
    }
    private boolean constantEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
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
