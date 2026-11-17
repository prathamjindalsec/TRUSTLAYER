package com.trustlayer.repository;

import com.trustlayer.entity.ExtensionAuthorizationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExtensionAuthorizationCodeRepository extends JpaRepository<ExtensionAuthorizationCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select authorizationCode
            from ExtensionAuthorizationCode authorizationCode
            join fetch authorizationCode.user
            where authorizationCode.codeHash = :codeHash
            """)
    Optional<ExtensionAuthorizationCode> findByCodeHashForUpdate(@Param("codeHash") String codeHash);
}
