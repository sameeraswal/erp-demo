package com.acme.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_payment_number_per_tenant", columnNames = {"tenant_id", "payment_number"}),
        @UniqueConstraint(name = "uq_payment_idempotency", columnNames = {"tenant_id", "idempotency_key"})
}, indexes = {
        @Index(name = "ix_payment_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "legal_entity_id", nullable = false)
    private UUID legalEntityId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 50)
    private String paymentNumber;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal baseAmount;

    @Column(precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal unallocatedAmount = BigDecimal.ZERO;

    @Column(length = 200)
    private String reference;

    @Column(length = 100)
    private String idempotencyKey;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PaymentAllocation> allocations = new ArrayList<>();
}
