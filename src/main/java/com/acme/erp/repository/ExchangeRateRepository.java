package com.acme.erp.repository;

import com.acme.erp.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {
    Optional<ExchangeRate> findTopByTenantIdAndFromCurrencyAndToCurrencyAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            UUID tenantId, String fromCurrency, String toCurrency, LocalDate effectiveDate);
}
