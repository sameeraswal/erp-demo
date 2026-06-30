package com.acme.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customers", uniqueConstraints = {
        @UniqueConstraint(name = "uq_customer_code_per_tenant", columnNames = {"tenant_id", "code"})
}, indexes = {
        @Index(name = "ix_customer_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "legal_entity_id", nullable = false)
    private UUID legalEntityId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(length = 200)
    private String email;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    @OneToMany(mappedBy = "customer")
    @Builder.Default
    private List<Invoice> invoices = new ArrayList<>();
}
