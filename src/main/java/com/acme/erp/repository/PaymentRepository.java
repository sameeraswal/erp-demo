package com.acme.erp.repository;

import com.acme.erp.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    long countByTenantId(UUID tenantId);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.allocations a LEFT JOIN FETCH a.invoice WHERE p.id = :id")
    Optional<Payment> findByIdWithAllocations(@Param("id") UUID id);
}
