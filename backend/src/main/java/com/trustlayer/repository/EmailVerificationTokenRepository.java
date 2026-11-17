package com.trustlayer.repository;

import com.trustlayer.entity.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from EmailVerificationToken token
            join fetch token.user
            where token.tokenHash = :tokenHash
            """)
    Optional<EmailVerificationToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            update EmailVerificationToken token
            set token.consumedAt = :now
            where token.user.id = :userId
              and token.consumedAt is null
            """)
    int invalidateOutstandingForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
