package com.storelense.auth.domain.repository;

import com.storelense.auth.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Page<User> findByStoreIdAndActiveTrue(UUID storeId, Pageable pageable);

    Page<User> findByStoreId(UUID storeId, Pageable pageable);

    Page<User> findByActiveTrue(Pageable pageable);

    Page<User> findAllBy(Pageable pageable);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1, " +
           "u.lockedUntil = CASE WHEN u.failedLoginAttempts >= 4 THEN :lockUntil ELSE u.lockedUntil END " +
           "WHERE u.id = :id")
    void incrementFailedAttempts(@Param("id") UUID id, @Param("lockUntil") OffsetDateTime lockUntil);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = null, u.lastLoginAt = :loginAt WHERE u.id = :id")
    void recordSuccessfulLogin(@Param("id") UUID id, @Param("loginAt") OffsetDateTime loginAt);
}
