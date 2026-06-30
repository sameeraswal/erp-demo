package com.acme.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "legal_entities", uniqueConstraints = {
        @UniqueConstraint(name = "uq_entity_code_per_tenant", columnNames = {"tenant_id", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_entity_id")
    private LegalEntity parent;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    @Builder.Default
    private boolean active = true;
}
