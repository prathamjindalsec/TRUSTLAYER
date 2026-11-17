package com.trustlayer.service;

import com.trustlayer.entity.AuditLog;
import com.trustlayer.entity.UserAccount;
import com.trustlayer.entity.enums.AuditOutcome;
import com.trustlayer.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(
            UserAccount actor,
            String eventType,
            String entityType,
            String entityId,
            AuditOutcome outcome,
            RequestMetadata metadata
    ) {
        auditLogRepository.save(new AuditLog(
                actor,
                eventType,
                entityType,
                entityId,
                outcome,
                metadata.ipAddress(),
                metadata.userAgent()
        ));
    }
}
