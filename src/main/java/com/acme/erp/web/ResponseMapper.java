package com.acme.erp.web;

import com.acme.erp.dto.Dtos;
import com.acme.erp.entity.*;
import com.acme.erp.repository.GLAccountRepository;
import com.acme.erp.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ResponseMapper {

    private final GLAccountRepository glAccountRepository;
    private final PaymentService paymentService;

    public Dtos.InvoiceResponse toInvoiceResponse(Invoice invoice) {
        Dtos.InvoiceResponse r = new Dtos.InvoiceResponse();
        r.setId(invoice.getId());
        r.setInvoiceNumber(invoice.getInvoiceNumber());
        r.setStatus(invoice.getStatus());
        r.setCustomerId(invoice.getCustomerId());
        r.setLegalEntityId(invoice.getLegalEntityId());
        r.setInvoiceDate(invoice.getInvoiceDate());
        r.setDueDate(invoice.getDueDate());
        r.setCurrency(invoice.getCurrency());
        r.setExchangeRate(invoice.getExchangeRate());
        r.setSubtotal(invoice.getSubtotal());
        r.setTaxAmount(invoice.getTaxAmount());
        r.setTotalAmount(invoice.getTotalAmount());
        r.setBaseTotalAmount(invoice.getBaseTotalAmount());
        r.setAmountPaid(invoice.getAmountPaid());
        r.setAmountDue(invoice.getAmountDue());
        r.setNotes(invoice.getNotes());
        r.setCreatedAt(invoice.getCreatedAt());
        r.setCreatedBy(invoice.getCreatedBy());
        r.setLineItems(invoice.getLineItems().stream().map(li -> {
            Dtos.InvoiceLineItemResponse lir = new Dtos.InvoiceLineItemResponse();
            lir.setId(li.getId());
            lir.setDescription(li.getDescription());
            lir.setQuantity(li.getQuantity());
            lir.setUnitPrice(li.getUnitPrice());
            lir.setLineTotal(li.getLineTotal());
            return lir;
        }).toList());
        r.setPaymentAllocations(invoice.getAllocations().stream().map(a -> {
            Dtos.PaymentAllocationResponse par = new Dtos.PaymentAllocationResponse();
            par.setId(a.getId());
            par.setPaymentId(a.getPayment().getId());
            par.setAmount(a.getAmount());
            par.setAllocationMethod(a.getAllocationMethod());
            par.setCreatedAt(a.getCreatedAt());
            return par;
        }).toList());
        return r;
    }

    public Dtos.JournalEntryResponse toJournalEntryResponse(JournalEntry entry) {
        List<UUID> accountIds = entry.getLines().stream()
                .map(JournalEntryLine::getGlAccountId).toList();
        Map<UUID, GLAccount> accountMap = glAccountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(GLAccount::getId, Function.identity()));

        Dtos.JournalEntryResponse r = new Dtos.JournalEntryResponse();
        r.setId(entry.getId());
        r.setEntryNumber(entry.getEntryNumber());
        r.setEntryDate(entry.getEntryDate());
        r.setDescription(entry.getDescription());
        r.setStatus(entry.getStatus().name());
        r.setSourceType(entry.getSourceType());
        r.setSourceId(entry.getSourceId());
        r.setCreatedAt(entry.getCreatedAt());
        r.setCreatedBy(entry.getCreatedBy());
        r.setLines(entry.getLines().stream().map(line -> {
            GLAccount account = accountMap.get(line.getGlAccountId());
            Dtos.JournalEntryLineResponse lr = new Dtos.JournalEntryLineResponse();
            lr.setId(line.getId());
            lr.setGlAccountId(line.getGlAccountId());
            if (account != null) {
                lr.setAccountCode(account.getCode());
                lr.setAccountName(account.getName());
            }
            lr.setDebit(line.getDebit());
            lr.setCredit(line.getCredit());
            lr.setBaseDebit(line.getBaseDebit());
            lr.setBaseCredit(line.getBaseCredit());
            lr.setDescription(line.getDescription());
            return lr;
        }).toList());
        return r;
    }

    public Dtos.PaymentResponse toPaymentResponse(Payment payment) {
        return paymentService.toResponse(payment);
    }
}
