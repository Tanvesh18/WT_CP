package com.tripshield.backend.security;

import com.tripshield.backend.model.Trip;
import com.tripshield.backend.model.TripEvent;
import com.tripshield.backend.model.TripInvitation;
import com.tripshield.backend.repository.TripEventRepository;
import com.tripshield.backend.repository.TripInvitationRepository;
import com.tripshield.backend.repository.TripRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InvitationController {
    private final Access access;
    private final TripRepository trips;
    private final TripInvitationRepository invitations;
    private final TripEventRepository events;
    private final SecureRandom random = new SecureRandom();

    public InvitationController(Access access, TripRepository trips, TripInvitationRepository invitations, TripEventRepository events) {
        this.access = access; this.trips = trips; this.invitations = invitations; this.events = events;
    }

    public record InvitationCode(String code, Instant expiresAt) {}
    public record ClaimInput(@NotBlank String code) {}

    @PostMapping("/api/trips/{id}/invitation")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InvitationCode issue(@PathVariable Long id) {
        access.coordinator();
        Trip trip = trips.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        if (trip.ownerUserId != null || "CANCELLED".equals(trip.lifecycle) || "REJECTED".equals(trip.lifecycle)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip cannot be invited");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        TripInvitation invite = new TripInvitation();
        invite.tripId = id; invite.email = trip.travelerEmail.toLowerCase(); invite.tokenHash = hash(code);
        invite.expiresAt = Instant.now().plus(7, ChronoUnit.DAYS); invite.issuedByUserId = access.current().id;
        invitations.save(invite);
        TripEvent event = new TripEvent(id, null, "INVITATION_ISSUED", "Single-use traveler invitation issued");
        event.actorUserId = access.current().id; event.actorRole = "COORDINATOR"; event.changeJson = "{\"expiresAt\":\"" + invite.expiresAt + "\"}";
        events.save(event);
        return new InvitationCode(code, invite.expiresAt);
    }

    @PostMapping("/api/auth/claim-trip")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void claim(@Valid @RequestBody ClaimInput input) {
        AppUser user = access.current();
        TripInvitation invite = invitations.findByTokenHash(hash(input.code()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid invitation"));
        if (invite.usedAt != null || !invite.expiresAt.isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation expired or already used");
        if (!user.email.equalsIgnoreCase(invite.email))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invitation email does not match your account");
        Trip trip = trips.findByIdForUpdate(invite.tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        if (trip.ownerUserId != null || "CANCELLED".equals(trip.lifecycle) || "REJECTED".equals(trip.lifecycle)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip cannot be claimed");
        trip.ownerUserId = user.id; invite.usedAt = Instant.now();
        trips.save(trip); invitations.save(invite);
        TripEvent event = new TripEvent(trip.id, null, "TRIP_CLAIMED", "Traveler claimed guest itinerary with a single-use invitation");
        event.actorUserId = user.id; event.actorRole = user.role; event.changeJson = "{\"ownerUserId\":" + user.id + "}";
        events.save(event);
    }

    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
