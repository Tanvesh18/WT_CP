package com.tripshield.backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tripshield.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {
    private final EmailAlerts alerts;
    public OutboxPoller(EmailAlerts alerts) { this.alerts = alerts; }
    @Scheduled(fixedDelayString = "${tripshield.outbox.poll-ms:30000}")
    public void poll() { alerts.deliverDue(); }
}
