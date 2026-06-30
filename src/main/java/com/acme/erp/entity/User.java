package com.acme.erp.entity;

import com.acme.erp.entity.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_email_per_tenant", columnNames = {"tenant_id", "email"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false, length = 200)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Builder.Default
    private boolean active = true;
}
