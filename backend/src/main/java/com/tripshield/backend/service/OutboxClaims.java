package com.tripshield.backend.service;

import com.tripshield.backend.model.NotificationOutbox;
import com.tripshield.backend.repository.NotificationOutboxRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxClaims {
    private final NotificationOutboxRepository repository;
    public OutboxClaims(NotificationOutboxRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationOutbox claim(Long id) {
        NotificationOutbox job = repository.findWithLockById(id).orElse(null);
        if (job == null || !("PENDING".equals(job.status) || "SENDING".equals(job.status))
                || job.nextAttemptAt.isAfter(Instant.now())) return null;
        job.status = "SENDING";
        job.attempts++;
        job.nextAttemptAt = Instant.now().plusSeconds(600);
        repository.saveAndFlush(job);
        return job;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long id, boolean sent) {
        NotificationOutbox job = repository.findWithLockById(id).orElseThrow();
        if (!"SENDING".equals(job.status)) return;
        if (sent) { job.status = "SENT"; job.sentAt = Instant.now(); job.lastError = null; }
        else {
            job.status = job.attempts >= 5 ? "FAILED" : "PENDING";
            job.nextAttemptAt = Instant.now().plusSeconds(Math.min(3600, 30L << Math.min(job.attempts, 6)));
            job.lastError = "Delivery failed; retry scheduled";
        }
        repository.save(job);
    }
}
