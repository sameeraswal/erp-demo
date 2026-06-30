package com.acme.erp.entity;

import com.acme.erp.entity.enums.JournalEntryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uq_journal_entry_per_tenant", columnNames = {"tenant_id", "entry_number"})
}, indexes = {
        @Index(name = "ix_journal_tenant", columnList = "tenant_id"),
        @Index(name = "ix_journal_source", columnList = "source_type, source_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "legal_entity_id", nullable = false)
    private UUID legalEntityId;

    @Column(nullable = false, length = 50)
    private String entryNumber;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private JournalEntryStatus status = JournalEntryStatus.POSTED;

    @Column(nullable = false, length = 50)
    private String sourceType;

    @Column(nullable = false)
    private UUID sourceId;

    @Column(name = "reversed_entry_id")
    private UUID reversedEntryId;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JournalEntryLine> lines = new ArrayList<>();
}
