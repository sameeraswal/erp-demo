package com.acme.erp.repository;

import com.acme.erp.entity.AccountingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {
    Optional<AccountingPeriod> findByLegalEntityIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID legalEntityId, LocalDate entryDate1, LocalDate entryDate2);
}
