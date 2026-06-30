package com.acme.erp.repository;

import com.acme.erp.entity.LegalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LegalEntityRepository extends JpaRepository<LegalEntity, UUID> {
}
