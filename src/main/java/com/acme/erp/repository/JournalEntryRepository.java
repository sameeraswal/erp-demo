package com.acme.erp.repository;

import com.acme.erp.entity.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    long countByTenantId(UUID tenantId);

    @Query("SELECT je FROM JournalEntry je LEFT JOIN FETCH je.lines WHERE je.tenantId = :tenantId AND je.sourceType = :sourceType AND je.sourceId = :sourceId")
    List<JournalEntry> findByTenantIdAndSourceTypeAndSourceId(
            @Param("tenantId") UUID tenantId, @Param("sourceType") String sourceType, @Param("sourceId") UUID sourceId);

    @Query("SELECT je FROM JournalEntry je LEFT JOIN FETCH je.lines WHERE je.tenantId = :tenantId AND je.sourceType = 'PAYMENT' AND je.sourceId IN :paymentIds")
    List<JournalEntry> findPaymentEntries(@Param("tenantId") UUID tenantId, @Param("paymentIds") List<UUID> paymentIds);
}
