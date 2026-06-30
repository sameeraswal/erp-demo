package com.acme.erp.service;

import com.acme.erp.entity.AuditLog;
import com.acme.erp.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(UUID tenantId, String entityType, UUID entityId, String action,
                    String actorId, String actorEmail, Map<String, Object> changes) {
        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .actorId(actorId)
                .actorEmail(actorEmail)
                .changes(changes)
                .build());
    }
}
