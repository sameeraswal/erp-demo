package com.acme.erp.service;

import com.acme.erp.dto.Dtos;
import com.acme.erp.entity.Customer;
import com.acme.erp.entity.Invoice;
import com.acme.erp.entity.InvoiceLineItem;
import com.acme.erp.entity.enums.InvoiceStatus;
import com.acme.erp.exception.NotFoundException;
import com.acme.erp.exception.ValidationException;
import com.acme.erp.repository.CustomerRepository;
import com.acme.erp.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final CurrencyService currencyService;
    private final JournalService journalService;
    private final AuditService auditService;

    private String nextInvoiceNumber(UUID tenantId) {
        return String.format("INV-%06d", invoiceRepository.countByTenantId(tenantId) + 1);
    }

    @Transactional
    public Invoice createInvoice(UUID tenantId, Dtos.InvoiceCreate data, String actorId, String actorEmail) {
        Customer customer = customerRepository.findByIdAndTenantId(data.getCustomerId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Customer not found"));

        if (data.getDueDate().isBefore(data.getInvoiceDate())) {
            throw new ValidationException("due_date must be on or after invoice_date");
        }

        BigDecimal subtotal = data.getLineItems().stream()
                .map(li -> li.getQuantity().multiply(li.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = subtotal.add(data.getTaxAmount() != null ? data.getTaxAmount() : BigDecimal.ZERO);

        String baseCurrency = currencyService.getTenantBaseCurrency(tenantId);
        CurrencyService.ConversionResult conversion = currencyService.convertToBase(
                tenantId, total, data.getCurrency(), baseCurrency, data.getInvoiceDate());

        Invoice invoice = Invoice.builder()
                .tenantId(tenantId)
                .legalEntityId(data.getLegalEntityId())
                .customerId(customer.getId())
                .invoiceNumber(nextInvoiceNumber(tenantId))
                .status(InvoiceStatus.DRAFT)
                .invoiceDate(data.getInvoiceDate())
                .dueDate(data.getDueDate())
                .currency(data.getCurrency())
                .exchangeRate(conversion.rate())
                .subtotal(subtotal)
                .taxAmount(data.getTaxAmount() != null ? data.getTaxAmount() : BigDecimal.ZERO)
                .totalAmount(total)
                .baseTotalAmount(conversion.baseAmount())
                .amountPaid(BigDecimal.ZERO)
                .amountDue(total)
                .notes(data.getNotes())
                .build();
        invoice.setCreatedBy(actorEmail);
        invoice.setUpdatedBy(actorEmail);

        for (Dtos.InvoiceLineItemCreate line : data.getLineItems()) {
            InvoiceLineItem item = InvoiceLineItem.builder()
                    .tenantId(tenantId)
                    .invoice(invoice)
                    .description(line.getDescription())
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitPrice())
                    .lineTotal(line.getQuantity().multiply(line.getUnitPrice()))
                    .build();
            item.setCreatedBy(actorEmail);
            invoice.getLineItems().add(item);
        }

        invoiceRepository.save(invoice);
        auditService.log(tenantId, "Invoice", invoice.getId(), "CREATE", actorId, actorEmail,
                Map.of("invoiceNumber", invoice.getInvoiceNumber(), "total", total.toString()));

        return getInvoice(tenantId, invoice.getId());
    }

    @Transactional(readOnly = true)
    public Invoice getInvoice(UUID tenantId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdAndTenantIdWithLineItems(invoiceId, tenantId)
                .orElseThrow(() -> new NotFoundException("Invoice not found"));
        invoiceRepository.findByIdAndTenantIdWithAllocations(invoiceId, tenantId);
        return invoice;
    }

    @Transactional
    public Invoice approveInvoice(UUID tenantId, UUID invoiceId, String actorId, String actorEmail) {
        Invoice invoice = getInvoice(tenantId, invoiceId);
        InvoiceStateMachine.assertCanApprove(invoice.getStatus());

        if (invoice.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Cannot approve invoice with zero or negative total");
        }

        journalService.postInvoiceApproval(
                tenantId, invoice.getLegalEntityId(), invoice.getId(), invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(), invoice.getTotalAmount(), invoice.getBaseTotalAmount(),
                invoice.getCurrency(), actorId, actorEmail);

        invoice.setStatus(InvoiceStatus.APPROVED);
        invoice.setUpdatedBy(actorEmail);
        invoiceRepository.save(invoice);

        auditService.log(tenantId, "Invoice", invoice.getId(), "APPROVE", actorId, actorEmail,
                Map.of("status", "APPROVED", "glPosted", true));

        return invoice;
    }
}
