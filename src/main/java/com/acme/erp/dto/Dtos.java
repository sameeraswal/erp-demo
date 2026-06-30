package com.acme.erp.dto;

import com.acme.erp.entity.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Dtos {

    private Dtos() {}

    @Data
    public static class LoginRequest {
        @NotBlank
        private String email;
        @NotBlank
        private String password;
    }

    @Data
    public static class TokenResponse {
        private String accessToken;
        private String tokenType = "bearer";
        private UUID userId;
        private String email;
        private UserRole role;
        private UUID tenantId;
    }

    @Data
    public static class InvoiceLineItemCreate {
        @NotBlank
        private String description;
        @NotNull @DecimalMin("0.0001")
        private BigDecimal quantity;
        @NotNull @DecimalMin("0")
        private BigDecimal unitPrice;
    }

    @Data
    public static class InvoiceCreate {
        @NotNull
        private UUID customerId;
        @NotNull
        private UUID legalEntityId;
        @NotNull
        private LocalDate invoiceDate;
        @NotNull
        private LocalDate dueDate;
        private String currency = "USD";
        @DecimalMin("0")
        private BigDecimal taxAmount = BigDecimal.ZERO;
        private String notes;
        @NotEmpty @Valid
        private List<InvoiceLineItemCreate> lineItems;
    }

    @Data
    public static class InvoiceLineItemResponse {
        private UUID id;
        private String description;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }

    @Data
    public static class PaymentAllocationResponse {
        private UUID id;
        private UUID paymentId;
        private BigDecimal amount;
        private AllocationMethod allocationMethod;
        private Instant createdAt;
    }

    @Data
    public static class InvoiceResponse {
        private UUID id;
        private String invoiceNumber;
        private InvoiceStatus status;
        private UUID customerId;
        private UUID legalEntityId;
        private LocalDate invoiceDate;
        private LocalDate dueDate;
        private String currency;
        private BigDecimal exchangeRate;
        private BigDecimal subtotal;
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
        private BigDecimal baseTotalAmount;
        private BigDecimal amountPaid;
        private BigDecimal amountDue;
        private String notes;
        private List<InvoiceLineItemResponse> lineItems;
        private List<PaymentAllocationResponse> paymentAllocations;
        private Instant createdAt;
        private String createdBy;
    }

    @Data
    public static class ManualAllocation {
        @NotNull
        private UUID invoiceId;
        @NotNull @DecimalMin("0.01")
        private BigDecimal amount;
    }

    @Data
    public static class PaymentCreate {
        @NotNull
        private UUID customerId;
        @NotNull
        private UUID legalEntityId;
        @NotNull
        private LocalDate paymentDate;
        @NotNull @DecimalMin("0.01")
        private BigDecimal amount;
        private String currency = "USD";
        private String reference;
        private AllocationMethod allocationMethod = AllocationMethod.FIFO;
        private List<ManualAllocation> manualAllocations;
    }

    @Data
    public static class PaymentAllocationDetail {
        private UUID invoiceId;
        private String invoiceNumber;
        private BigDecimal amount;
    }

    @Data
    public static class PaymentResponse {
        private UUID id;
        private String paymentNumber;
        private UUID customerId;
        private UUID legalEntityId;
        private LocalDate paymentDate;
        private String currency;
        private BigDecimal amount;
        private BigDecimal baseAmount;
        private BigDecimal unallocatedAmount;
        private String reference;
        private List<PaymentAllocationDetail> allocations;
        private Instant createdAt;
        private String createdBy;
    }

    @Data
    public static class AgingBucket {
        private BigDecimal current = BigDecimal.ZERO;
        private BigDecimal days1_30 = BigDecimal.ZERO;
        private BigDecimal days31_60 = BigDecimal.ZERO;
        private BigDecimal days61_90 = BigDecimal.ZERO;
        private BigDecimal daysOver90 = BigDecimal.ZERO;
        private BigDecimal total = BigDecimal.ZERO;
    }

    @Data
    public static class CustomerAgingResponse {
        private UUID customerId;
        private String customerName;
        private String currency;
        private LocalDate asOfDate;
        private AgingBucket buckets;
        private List<Map<String, Object>> invoices;
    }

    @Data
    public static class JournalEntryLineResponse {
        private UUID id;
        private UUID glAccountId;
        private String accountCode;
        private String accountName;
        private BigDecimal debit;
        private BigDecimal credit;
        private BigDecimal baseDebit;
        private BigDecimal baseCredit;
        private String description;
    }

    @Data
    public static class JournalEntryResponse {
        private UUID id;
        private String entryNumber;
        private LocalDate entryDate;
        private String description;
        private String status;
        private String sourceType;
        private UUID sourceId;
        private List<JournalEntryLineResponse> lines;
        private Instant createdAt;
        private String createdBy;
    }

    @Data
    public static class ErrorResponse {
        private String detail;

        public ErrorResponse(String detail) {
            this.detail = detail;
        }
    }
}
