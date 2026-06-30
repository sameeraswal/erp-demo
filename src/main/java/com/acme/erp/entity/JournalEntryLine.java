package com.acme.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "journal_entry_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntryLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @Column(name = "gl_account_id", nullable = false)
    private UUID glAccountId;

    @Column(precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal baseDebit = BigDecimal.ZERO;

    @Column(precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal baseCredit = BigDecimal.ZERO;

    @Column(length = 500)
    private String description;
}
