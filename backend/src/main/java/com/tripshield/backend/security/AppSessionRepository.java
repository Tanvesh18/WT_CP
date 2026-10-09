package com.tripshield.backend.security;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSessionRepository extends JpaRepository<AppSession, Long> {
    Optional<AppSession> findByTokenHash(String tokenHash);
}
