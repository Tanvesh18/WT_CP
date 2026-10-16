package com.tripshield.backend.repository;

import com.tripshield.backend.model.NotificationOutbox;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    List<NotificationOutbox> findTop20ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(String status, Instant now);
    List<NotificationOutbox> findTop20ByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(List<String> statuses, Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<NotificationOutbox> findWithLockById(Long id);
}
