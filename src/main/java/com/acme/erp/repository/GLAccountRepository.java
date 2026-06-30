package com.acme.erp.repository;

import com.acme.erp.entity.GLAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GLAccountRepository extends JpaRepository<GLAccount, UUID> {
    Optional<GLAccount> findByTenantIdAndLegalEntityIdAndCodeAndActiveTrue(
            UUID tenantId, UUID legalEntityId, String code);
}
