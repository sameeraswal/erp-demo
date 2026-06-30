package com.acme.erp.entity;

import com.acme.erp.entity.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "invoices", uniqueConstraints = {
        @UniqueConstraint(name = "uq_invoice_number_per_tenant", columnNames = {"tenant_id", "invoice_number"})
}, indexes = {
        @Index(name = "ix_invoice_tenant", columnList = "tenant_id"),
        @Index(name = "ix_invoice_customer_status", columnList = "customer_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "legal_entity_id", nullable = false)
    private UUID legalEntityId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Column(nullable = false, length = 50)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal baseTotalAmount = BigDecimal.ZERO;

    @Column(precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal amountDue = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @OneToMany(mappedBy = "invoice")
    @Builder.Default
    private Set<PaymentAllocation> allocations = new HashSet<>();
}
