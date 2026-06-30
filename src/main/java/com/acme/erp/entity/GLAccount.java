package com.acme.erp.entity;

import com.acme.erp.entity.enums.GLAccountType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "gl_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uq_gl_account_code_per_entity", columnNames = {"legal_entity_id", "code"})
}, indexes = {
        @Index(name = "ix_gl_account_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GLAccount extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "legal_entity_id", nullable = false)
    private UUID legalEntityId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private GLAccountType accountType;

    @Builder.Default
    private boolean active = true;
}
