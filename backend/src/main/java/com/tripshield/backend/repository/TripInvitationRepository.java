package com.tripshield.backend.repository;

import com.tripshield.backend.model.TripInvitation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface TripInvitationRepository extends JpaRepository<TripInvitation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TripInvitation> findByTokenHash(String tokenHash);
}
