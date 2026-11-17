package com.trustlayer.repository;

import com.trustlayer.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByActor_IdAndEventTypeInOrderByCreatedAtDesc(
            Long userId,
            Collection<String> eventTypes,
            Pageable pageable
    );
}
