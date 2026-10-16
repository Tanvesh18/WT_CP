package com.tripshield.backend.repository;

import com.tripshield.backend.model.UserNotification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    List<UserNotification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    long countByUserIdAndReadAtIsNull(Long userId);
    Optional<UserNotification> findByIdAndUserId(Long id, Long userId);
}
