package com.acme.erp.repository;

import com.acme.erp.entity.Invoice;
import com.acme.erp.entity.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.lineItems WHERE i.id = :id AND i.tenantId = :tenantId")
    Optional<Invoice> findByIdAndTenantIdWithLineItems(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.allocations WHERE i.id = :id AND i.tenantId = :tenantId")
    Optional<Invoice> findByIdAndTenantIdWithAllocations(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    List<Invoice> findByTenantIdAndCustomerIdAndStatusInAndAmountDueGreaterThanOrderByDueDateAscInvoiceDateAsc(
            UUID tenantId, UUID customerId, List<InvoiceStatus> statuses, BigDecimal zero);
}
