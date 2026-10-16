package com.tripshield.backend.repository;

import com.tripshield.backend.model.TripCreateKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripCreateKeyRepository extends JpaRepository<TripCreateKey, Long> {
    Optional<TripCreateKey> findByActorUserIdAndRequestKey(Long actorUserId, String requestKey);
}
