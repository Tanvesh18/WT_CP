package com.tripshield.backend.security;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
public interface UserRepository extends JpaRepository<AppUser,Long> {
 Optional<AppUser> findByEmailIgnoreCase(String email);
 @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<AppUser> findWithLockById(Long id);
}
