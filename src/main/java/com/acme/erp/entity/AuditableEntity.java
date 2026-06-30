package com.acme.erp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity {

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 100)
    private String createdBy;

    @Column(length = 100)
    private String updatedBy;

    @Version
    private int version;
}
