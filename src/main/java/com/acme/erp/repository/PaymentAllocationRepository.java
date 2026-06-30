package com.acme.erp.repository;

import com.acme.erp.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {

    @Query("SELECT DISTINCT pa.payment.id FROM PaymentAllocation pa WHERE pa.invoice.id = :invoiceId AND pa.tenantId = :tenantId")
    List<UUID> findPaymentIdsByInvoiceId(@Param("invoiceId") UUID invoiceId, @Param("tenantId") UUID tenantId);
}
