package com.trustlayer.repository;

import com.trustlayer.entity.AuthenticationSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface AuthenticationSessionRepository extends JpaRepository<AuthenticationSession, Long> {

    Optional<AuthenticationSession> findBySessionId(String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from AuthenticationSession session
            join fetch session.user
            where session.sessionId = :sessionId
            """)
    Optional<AuthenticationSession> findBySessionIdForUpdate(@Param("sessionId") String sessionId);

    Optional<AuthenticationSession> findBySessionIdAndUser_Id(String sessionId, Long userId);

    Page<AuthenticationSession> findByUser_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(
            Long userId,
            Instant now,
            Pageable pageable
    );

    boolean existsBySessionIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfter(
            String sessionId,
            Long userId,
            Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update AuthenticationSession session
            set session.lastSeenAt = :now
            where session.sessionId = :sessionId
              and session.user.id = :userId
              and session.revokedAt is null
              and session.expiresAt > :now
              and session.lastSeenAt < :staleBefore
            """)
    int touchActiveSessionIfStale(
            @Param("sessionId") String sessionId,
            @Param("userId") Long userId,
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore
    );

    @Modifying
    @Query("""
            update AuthenticationSession session
            set session.revokedAt = :now
            where session.user.id = :userId
              and session.revokedAt is null
            """)
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
