package com.acme.erp.service;

import com.acme.erp.entity.*;
import com.acme.erp.entity.enums.JournalEntryStatus;
import com.acme.erp.exception.NotFoundException;
import com.acme.erp.exception.ValidationException;
import com.acme.erp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class JournalService {

    private static final Map<String, String> STANDARD_ACCOUNTS = Map.of(
            "CASH", "1000",
            "AR", "1200",
            "REVENUE", "4000",
            "SALES_RETURNS", "4100",
            "BAD_DEBT", "5200"
    );

    private final JournalEntryRepository journalEntryRepository;
    private final GLAccountRepository glAccountRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final AuditService auditService;

    public void assertPeriodOpen(UUID legalEntityId, LocalDate entryDate) {
        accountingPeriodRepository
                .findByLegalEntityIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        legalEntityId, entryDate, entryDate)
                .filter(AccountingPeriod::isClosed)
                .ifPresent(period -> {
                    throw new ValidationException(
                            "Accounting period '" + period.getName() + "' is closed. Cannot post entries dated " + entryDate + ".");
                });
    }

    private GLAccount getStandardAccount(UUID tenantId, UUID legalEntityId, String key) {
        String code = STANDARD_ACCOUNTS.get(key);
        return glAccountRepository.findByTenantIdAndLegalEntityIdAndCodeAndActiveTrue(tenantId, legalEntityId, code)
                .orElseThrow(() -> new NotFoundException("GL account " + code + " not configured for entity"));
    }

    private String nextEntryNumber(UUID tenantId) {
        long count = journalEntryRepository.countByTenantId(tenantId);
        return String.format("JE-%06d", count + 1);
    }

    @Transactional
    public JournalEntry createJournalEntry(UUID tenantId, UUID legalEntityId, LocalDate entryDate,
                                           String description, String sourceType, UUID sourceId,
                                           List<Map<String, Object>> lineData,
                                           String actorId, String actorEmail) {
        assertPeriodOpen(legalEntityId, entryDate);

        JournalEntry entry = JournalEntry.builder()
                .tenantId(tenantId)
                .legalEntityId(legalEntityId)
                .entryNumber(nextEntryNumber(tenantId))
                .entryDate(entryDate)
                .description(description)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .status(JournalEntryStatus.POSTED)
                .build();
        entry.setCreatedBy(actorEmail);
        entry.setUpdatedBy(actorEmail);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (Map<String, Object> data : lineData) {
            BigDecimal debit = (BigDecimal) data.getOrDefault("debit", BigDecimal.ZERO);
            BigDecimal credit = (BigDecimal) data.getOrDefault("credit", BigDecimal.ZERO);
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);

            JournalEntryLine line = JournalEntryLine.builder()
                    .journalEntry(entry)
                    .glAccountId((UUID) data.get("glAccountId"))
                    .debit(debit)
                    .credit(credit)
                    .currency((String) data.getOrDefault("currency", "USD"))
                    .baseDebit((BigDecimal) data.getOrDefault("baseDebit", debit))
                    .baseCredit((BigDecimal) data.getOrDefault("baseCredit", credit))
                    .description((String) data.get("description"))
                    .build();
            entry.getLines().add(line);
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new ValidationException("Journal entry unbalanced: debits=" + totalDebit + ", credits=" + totalCredit);
        }
        if (totalDebit.compareTo(BigDecimal.ZERO) == 0) {
            throw new ValidationException("Journal entry must have non-zero amounts");
        }

        journalEntryRepository.save(entry);
        auditService.log(tenantId, "JournalEntry", entry.getId(), "POST", actorId, actorEmail,
                Map.of("description", description, "sourceType", sourceType));
        return entry;
    }

    @Transactional
    public JournalEntry postInvoiceApproval(UUID tenantId, UUID legalEntityId, UUID invoiceId,
                                            String invoiceNumber, LocalDate entryDate,
                                            BigDecimal amount, BigDecimal baseAmount, String currency,
                                            String actorId, String actorEmail) {
        GLAccount arAccount = getStandardAccount(tenantId, legalEntityId, "AR");
        GLAccount revenueAccount = getStandardAccount(tenantId, legalEntityId, "REVENUE");

        List<Map<String, Object>> lines = List.of(
                line(arAccount.getId(), amount, BigDecimal.ZERO, baseAmount, BigDecimal.ZERO, currency,
                        "AR - Invoice " + invoiceNumber),
                line(revenueAccount.getId(), BigDecimal.ZERO, amount, BigDecimal.ZERO, baseAmount, currency,
                        "Revenue - Invoice " + invoiceNumber)
        );

        return createJournalEntry(tenantId, legalEntityId, entryDate,
                "Invoice " + invoiceNumber + " approval", "INVOICE", invoiceId, lines, actorId, actorEmail);
    }

    @Transactional
    public JournalEntry postPaymentReceipt(UUID tenantId, UUID legalEntityId, UUID paymentId,
                                             String paymentNumber, LocalDate entryDate,
                                             BigDecimal amount, BigDecimal baseAmount, String currency,
                                             String actorId, String actorEmail) {
        GLAccount cashAccount = getStandardAccount(tenantId, legalEntityId, "CASH");
        GLAccount arAccount = getStandardAccount(tenantId, legalEntityId, "AR");

        List<Map<String, Object>> lines = List.of(
                line(cashAccount.getId(), amount, BigDecimal.ZERO, baseAmount, BigDecimal.ZERO, currency,
                        "Cash - Payment " + paymentNumber),
                line(arAccount.getId(), BigDecimal.ZERO, amount, BigDecimal.ZERO, baseAmount, currency,
                        "AR - Payment " + paymentNumber)
        );

        return createJournalEntry(tenantId, legalEntityId, entryDate,
                "Payment " + paymentNumber + " receipt", "PAYMENT", paymentId, lines, actorId, actorEmail);
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> getEntriesForInvoice(UUID tenantId, UUID invoiceId) {
        List<JournalEntry> invoiceEntries = journalEntryRepository
                .findByTenantIdAndSourceTypeAndSourceId(tenantId, "INVOICE", invoiceId);

        List<UUID> paymentIds = paymentAllocationRepository.findPaymentIdsByInvoiceId(invoiceId, tenantId);
        List<JournalEntry> paymentEntries = paymentIds.isEmpty()
                ? List.of()
                : journalEntryRepository.findPaymentEntries(tenantId, paymentIds);

        List<JournalEntry> all = new ArrayList<>(invoiceEntries);
        all.addAll(paymentEntries);
        return all;
    }

    private Map<String, Object> line(UUID glAccountId, BigDecimal debit, BigDecimal credit,
                                     BigDecimal baseDebit, BigDecimal baseCredit, String currency, String description) {
        Map<String, Object> m = new HashMap<>();
        m.put("glAccountId", glAccountId);
        m.put("debit", debit);
        m.put("credit", credit);
        m.put("baseDebit", baseDebit);
        m.put("baseCredit", baseCredit);
        m.put("currency", currency);
        m.put("description", description);
        return m;
    }
}
