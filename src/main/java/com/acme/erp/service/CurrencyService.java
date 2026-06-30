package com.acme.erp.service;

import com.acme.erp.entity.ExchangeRate;
import com.acme.erp.entity.Tenant;
import com.acme.erp.exception.NotFoundException;
import com.acme.erp.repository.ExchangeRateRepository;
import com.acme.erp.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CurrencyService {

    private final TenantRepository tenantRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    public String getTenantBaseCurrency(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(Tenant::getBaseCurrency)
                .orElse("USD");
    }

    public record ConversionResult(BigDecimal baseAmount, BigDecimal rate) {}

    public ConversionResult convertToBase(UUID tenantId, BigDecimal amount, String fromCurrency,
                                          String toCurrency, LocalDate effectiveDate) {
        if (fromCurrency.equals(toCurrency)) {
            return new ConversionResult(amount, BigDecimal.ONE);
        }
        ExchangeRate rate = exchangeRateRepository
                .findTopByTenantIdAndFromCurrencyAndToCurrencyAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        tenantId, fromCurrency, toCurrency, effectiveDate)
                .orElseThrow(() -> new NotFoundException(
                        "Exchange rate not found for " + fromCurrency + " -> " + toCurrency));
        BigDecimal baseAmount = amount.multiply(rate.getRate()).setScale(2, RoundingMode.HALF_UP);
        return new ConversionResult(baseAmount, rate.getRate());
    }
}
