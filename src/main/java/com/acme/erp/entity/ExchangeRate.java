package com.acme.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "exchange_rates", uniqueConstraints = {
        @UniqueConstraint(name = "uq_exchange_rate_per_day",
                columnNames = {"tenant_id", "from_currency", "to_currency", "effective_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRate extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 3)
    private String fromCurrency;

    @Column(nullable = false, length = 3)
    private String toCurrency;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(nullable = false)
    private LocalDate effectiveDate;
}
