package com.acme.erp.repository;

import com.acme.erp.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByTenantIdAndIdempotencyKeyAndExpiresAtAfter(
            UUID tenantId, String idempotencyKey, Instant now);
}
