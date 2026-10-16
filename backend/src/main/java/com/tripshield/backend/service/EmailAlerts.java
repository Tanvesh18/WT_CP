package com.tripshield.backend.service;

import com.tripshield.backend.model.NotificationOutbox;
import com.tripshield.backend.model.Trip;
import com.tripshield.backend.repository.NotificationOutboxRepository;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailAlerts {
    private final NotificationOutboxRepository outbox;
    private final ObjectProvider<JavaMailSender> sender;
    private final OutboxClaims claims;
    @Value("${tripshield.alert.to:}") private String to;
    @Value("${spring.mail.username:}") private String from;

    public EmailAlerts(NotificationOutboxRepository outbox, ObjectProvider<JavaMailSender> sender, OutboxClaims claims) {
        this.outbox = outbox; this.sender = sender; this.claims = claims;
    }

    /** Called inside the trip transaction; no network request occurs here. */
    public void helpRequested(Trip trip) {
        NotificationOutbox job = new NotificationOutbox();
        job.tripId = trip.id; job.recipient = to; job.subject = "TripShield help request: trip #" + trip.id;
        job.body = "Traveler: " + trip.traveler + "\nRoute: " + trip.origin + " to " + trip.destination
                + "\nNote: " + (trip.checkInNote == null ? "" : trip.checkInNote);
        if (to.isBlank() || from.isBlank()) job.status = "SKIPPED";
        outbox.save(job);
    }

    public void deliverDue() {
        if (to.isBlank() || from.isBlank() || sender.getIfAvailable() == null) return;
        for (NotificationOutbox due : outbox.findTop20ByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(java.util.List.of("PENDING", "SENDING"), Instant.now().plusMillis(1))) {
            NotificationOutbox job = claims.claim(due.id);
            if (job == null) continue;
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(job.recipient); message.setFrom(from); message.setSubject(job.subject); message.setText(job.body);
            try {
                sender.getObject().send(message);
                claims.complete(job.id, true);
            } catch (Exception ex) {
                claims.complete(job.id, false);
            }
        }
    }
}
