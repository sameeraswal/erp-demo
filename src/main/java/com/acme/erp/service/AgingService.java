package com.acme.erp.service;

import com.acme.erp.dto.Dtos;
import com.acme.erp.entity.Customer;
import com.acme.erp.entity.Invoice;
import com.acme.erp.entity.enums.InvoiceStatus;
import com.acme.erp.exception.NotFoundException;
import com.acme.erp.repository.CustomerRepository;
import com.acme.erp.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AgingService {

    private static final Set<InvoiceStatus> OPEN_STATUSES = EnumSet.of(
            InvoiceStatus.APPROVED, InvoiceStatus.SENT,
            InvoiceStatus.PARTIALLY_PAID
    );

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public Dtos.CustomerAgingResponse getCustomerAging(UUID tenantId, UUID customerId) {
        Customer customer = customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> new NotFoundException("Customer not found"));

        List<Invoice> invoices = invoiceRepository.findAll().stream()
                .filter(i -> i.getTenantId().equals(tenantId)
                        && i.getCustomerId().equals(customerId)
                        && OPEN_STATUSES.contains(i.getStatus())
                        && i.getAmountDue().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        LocalDate asOf = LocalDate.now();
        Dtos.AgingBucket buckets = new Dtos.AgingBucket();
        List<Map<String, Object>> invoiceDetails = new ArrayList<>();

        for (Invoice invoice : invoices) {
            long daysPastDue = ChronoUnit.DAYS.between(invoice.getDueDate(), asOf);
            BigDecimal due = invoice.getAmountDue();

            if (daysPastDue <= 0) {
                buckets.setCurrent(buckets.getCurrent().add(due));
            } else if (daysPastDue <= 30) {
                buckets.setDays1_30(buckets.getDays1_30().add(due));
            } else if (daysPastDue <= 60) {
                buckets.setDays31_60(buckets.getDays31_60().add(due));
            } else if (daysPastDue <= 90) {
                buckets.setDays61_90(buckets.getDays61_90().add(due));
            } else {
                buckets.setDaysOver90(buckets.getDaysOver90().add(due));
            }
            buckets.setTotal(buckets.getTotal().add(due));

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("invoice_id", invoice.getId());
            detail.put("invoice_number", invoice.getInvoiceNumber());
            detail.put("due_date", invoice.getDueDate().toString());
            detail.put("amount_due", due);
            detail.put("days_past_due", Math.max(0, daysPastDue));
            invoiceDetails.add(detail);
        }

        Dtos.CustomerAgingResponse response = new Dtos.CustomerAgingResponse();
        response.setCustomerId(customerId);
        response.setCustomerName(customer.getName());
        response.setCurrency(customer.getCurrency());
        response.setAsOfDate(asOf);
        response.setBuckets(buckets);
        response.setInvoices(invoiceDetails);
        return response;
    }
}
