package com.acme.erp.service;

import com.acme.erp.dto.Dtos;
import com.acme.erp.entity.*;
import com.acme.erp.entity.enums.AllocationMethod;
import com.acme.erp.entity.enums.InvoiceStatus;
import com.acme.erp.exception.ConflictException;
import com.acme.erp.exception.NotFoundException;
import com.acme.erp.exception.ValidationException;
import com.acme.erp.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final CurrencyService currencyService;
    private final JournalService journalService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private String nextPaymentNumber(UUID tenantId) {
        return String.format("PAY-%06d", paymentRepository.countByTenantId(tenantId) + 1);
    }

    private String hashRequest(Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ValidationException("Failed to hash request: " + e.getMessage());
        }
    }

    @Transactional
    public Payment createPayment(UUID tenantId, Dtos.PaymentCreate data, String idempotencyKey,
                                 String actorId, String actorEmail) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> existing = idempotencyRecordRepository
                    .findByTenantIdAndIdempotencyKeyAndExpiresAtAfter(tenantId, idempotencyKey, Instant.now());
            if (existing.isPresent()) {
                String requestHash = hashRequest(data);
                if (!existing.get().getRequestHash().equals(requestHash)) {
                    throw new ConflictException("Idempotency key reused with different request body");
                }
                UUID paymentId = UUID.fromString(existing.get().getResponseBody().get("id").toString());
                return paymentRepository.findByIdWithAllocations(paymentId)
                        .orElseThrow(() -> new NotFoundException("Cached payment not found"));
            }
        }

        customerRepository.findByIdAndTenantId(data.getCustomerId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Customer not found"));

        String baseCurrency = currencyService.getTenantBaseCurrency(tenantId);
        CurrencyService.ConversionResult conversion = currencyService.convertToBase(
                tenantId, data.getAmount(), data.getCurrency(), baseCurrency, data.getPaymentDate());

        List<InvoiceAllocation> allocations = resolveAllocations(tenantId, data);
        BigDecimal totalAllocated = allocations.stream()
                .map(InvoiceAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unallocated = data.getAmount().subtract(totalAllocated);

        Payment payment = Payment.builder()
                .tenantId(tenantId)
                .legalEntityId(data.getLegalEntityId())
                .customerId(data.getCustomerId())
                .paymentNumber(nextPaymentNumber(tenantId))
                .paymentDate(data.getPaymentDate())
                .currency(data.getCurrency())
                .exchangeRate(conversion.rate())
                .amount(data.getAmount())
                .baseAmount(conversion.baseAmount())
                .unallocatedAmount(unallocated)
                .reference(data.getReference())
                .idempotencyKey(idempotencyKey)
                .build();
        payment.setCreatedBy(actorEmail);
        payment.setUpdatedBy(actorEmail);

        for (InvoiceAllocation alloc : allocations) {
            PaymentAllocation pa = PaymentAllocation.builder()
                    .tenantId(tenantId)
                    .payment(payment)
                    .invoice(alloc.invoice())
                    .amount(alloc.amount())
                    .allocationMethod(data.getAllocationMethod())
                    .build();
            pa.setCreatedBy(actorEmail);
            payment.getAllocations().add(pa);

            Invoice invoice = alloc.invoice();
            invoice.setAmountPaid(invoice.getAmountPaid().add(alloc.amount()));
            invoice.setAmountDue(invoice.getAmountDue().subtract(alloc.amount()));
            invoice.setStatus(InvoiceStateMachine.paymentStatus(invoice.getAmountDue(), invoice.getAmountPaid()));
            invoice.setUpdatedBy(actorEmail);
            invoiceRepository.save(invoice);
        }

        paymentRepository.save(payment);

        journalService.postPaymentReceipt(
                tenantId, data.getLegalEntityId(), payment.getId(), payment.getPaymentNumber(),
                data.getPaymentDate(), data.getAmount(), conversion.baseAmount(),
                data.getCurrency(), actorId, actorEmail);

        auditService.log(tenantId, "Payment", payment.getId(), "CREATE", actorId, actorEmail,
                Map.of("paymentNumber", payment.getPaymentNumber(), "amount", data.getAmount().toString(),
                        "allocations", allocations.size()));

        Payment saved = paymentRepository.findByIdWithAllocations(payment.getId()).orElse(payment);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Map<String, Object> response = objectMapper.convertValue(
                    toResponse(saved), new TypeReference<>() {});
            idempotencyRecordRepository.save(IdempotencyRecord.builder()
                    .tenantId(tenantId)
                    .idempotencyKey(idempotencyKey)
                    .requestHash(hashRequest(data))
                    .responseBody(response)
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .build());
        }

        return saved;
    }

    private List<InvoiceAllocation> resolveAllocations(UUID tenantId, Dtos.PaymentCreate data) {
        if (data.getAllocationMethod() == AllocationMethod.MANUAL) {
            if (data.getManualAllocations() == null || data.getManualAllocations().isEmpty()) {
                throw new ValidationException("manualAllocations required for MANUAL allocation method");
            }
            List<InvoiceAllocation> result = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (Dtos.ManualAllocation alloc : data.getManualAllocations()) {
                Invoice invoice = invoiceRepository.findByIdAndTenantId(alloc.getInvoiceId(), tenantId)
                        .orElseThrow(() -> new NotFoundException("Invoice " + alloc.getInvoiceId() + " not found"));
                InvoiceStateMachine.assertCanReceivePayment(invoice.getStatus());
                if (alloc.getAmount().compareTo(invoice.getAmountDue()) > 0) {
                    throw new ValidationException("Allocation " + alloc.getAmount()
                            + " exceeds invoice " + invoice.getInvoiceNumber() + " balance " + invoice.getAmountDue());
                }
                result.add(new InvoiceAllocation(invoice, alloc.getAmount()));
                total = total.add(alloc.getAmount());
            }
            if (total.compareTo(data.getAmount()) > 0) {
                throw new ValidationException("Total allocations exceed payment amount");
            }
            return result;
        }
        return fifoAllocations(tenantId, data.getCustomerId(), data.getAmount());
    }

    private List<InvoiceAllocation> fifoAllocations(UUID tenantId, UUID customerId, BigDecimal amount) {
        List<InvoiceStatus> openStatuses = List.of(
                InvoiceStatus.APPROVED, InvoiceStatus.SENT, InvoiceStatus.PARTIALLY_PAID);
        List<Invoice> openInvoices = invoiceRepository
                .findByTenantIdAndCustomerIdAndStatusInAndAmountDueGreaterThanOrderByDueDateAscInvoiceDateAsc(
                        tenantId, customerId, openStatuses, BigDecimal.ZERO);

        BigDecimal remaining = amount;
        List<InvoiceAllocation> allocations = new ArrayList<>();
        for (Invoice invoice : openInvoices) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal allocAmount = remaining.min(invoice.getAmountDue());
            allocations.add(new InvoiceAllocation(invoice, allocAmount));
            remaining = remaining.subtract(allocAmount);
        }
        return allocations;
    }

    public Dtos.PaymentResponse toResponse(Payment payment) {
        Dtos.PaymentResponse response = new Dtos.PaymentResponse();
        response.setId(payment.getId());
        response.setPaymentNumber(payment.getPaymentNumber());
        response.setCustomerId(payment.getCustomerId());
        response.setLegalEntityId(payment.getLegalEntityId());
        response.setPaymentDate(payment.getPaymentDate());
        response.setCurrency(payment.getCurrency());
        response.setAmount(payment.getAmount());
        response.setBaseAmount(payment.getBaseAmount());
        response.setUnallocatedAmount(payment.getUnallocatedAmount());
        response.setReference(payment.getReference());
        response.setCreatedAt(payment.getCreatedAt());
        response.setCreatedBy(payment.getCreatedBy());
        response.setAllocations(payment.getAllocations().stream().map(a -> {
            Dtos.PaymentAllocationDetail d = new Dtos.PaymentAllocationDetail();
            d.setInvoiceId(a.getInvoice().getId());
            d.setInvoiceNumber(a.getInvoice().getInvoiceNumber());
            d.setAmount(a.getAmount());
            return d;
        }).toList());
        return response;
    }

    private record InvoiceAllocation(Invoice invoice, BigDecimal amount) {}
}
