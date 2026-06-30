package com.acme.erp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "ix_audit_tenant", columnList = "tenant_id"),
        @Index(name = "ix_audit_entity", columnList = "entity_type, entity_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 50)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 100)
    private String actorId;

    @Column(length = 200)
    private String actorEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> changes;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
