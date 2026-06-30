package com.acme.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 3)
    @Builder.Default
    private String baseCurrency = "USD";

    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "tenant")
    @Builder.Default
    private List<LegalEntity> legalEntities = new ArrayList<>();

    @OneToMany(mappedBy = "tenant")
    @Builder.Default
    private List<User> users = new ArrayList<>();
}
