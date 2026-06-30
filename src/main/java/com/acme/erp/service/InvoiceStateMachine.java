package com.acme.erp.service;

import com.acme.erp.entity.enums.InvoiceStatus;
import com.acme.erp.exception.ValidationException;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

public final class InvoiceStateMachine {

    private InvoiceStateMachine() {}

    public static void assertCanApprove(InvoiceStatus status) {
        if (status != InvoiceStatus.DRAFT) {
            throw new ValidationException("Only DRAFT invoices can be approved. Current status: " + status);
        }
    }

    public static void assertEditable(InvoiceStatus status) {
        if (status != InvoiceStatus.DRAFT) {
            throw new ValidationException("Only DRAFT invoices can be edited. Current status: " + status);
        }
    }

    public static void assertCanReceivePayment(InvoiceStatus status) {
        Set<InvoiceStatus> allowed = EnumSet.of(
                InvoiceStatus.APPROVED, InvoiceStatus.SENT,
                InvoiceStatus.PARTIALLY_PAID
        );
        if (!allowed.contains(status)) {
            throw new ValidationException("Invoice in status " + status + " cannot receive payments");
        }
    }

    public static InvoiceStatus paymentStatus(BigDecimal amountDue, BigDecimal amountPaid) {
        if (amountDue.compareTo(BigDecimal.ZERO) <= 0) {
            return InvoiceStatus.PAID;
        }
        if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            return InvoiceStatus.PARTIALLY_PAID;
        }
        return InvoiceStatus.APPROVED;
    }
}
