package com.acme.erp.web;

import com.acme.erp.dto.Dtos;
import com.acme.erp.entity.enums.UserRole;
import com.acme.erp.security.RequestContext;
import com.acme.erp.security.RoleChecker;
import com.acme.erp.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApiController {

    private final AuthService authService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final AgingService agingService;
    private final JournalService journalService;
    private final ResponseMapper mapper;
    private final RoleChecker roleChecker;

    @Operation(summary = "Login", description = "No JWT needed. Set X-Tenant-Id header below, then use returned token for other endpoints.")
    @SecurityRequirements
    @PostMapping("/auth/login")
    public Dtos.TokenResponse login(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody Dtos.LoginRequest request) {
        return authService.login(tenantId, request);
    }

    @PostMapping("/invoices")
    public Dtos.InvoiceResponse createInvoice(
            HttpServletRequest request,
            @Valid @RequestBody Dtos.InvoiceCreate body) {
        RequestContext ctx = roleChecker.requireRole(request, UserRole.INVOICE_CREATOR);
        return mapper.toInvoiceResponse(invoiceService.createInvoice(
                ctx.getTenantId(), body, ctx.getUserId().toString(), ctx.getUserEmail()));
    }

    @GetMapping("/invoices/{id}")
    public Dtos.InvoiceResponse getInvoice(
            HttpServletRequest request,
            @PathVariable UUID id) {
        RequestContext ctx = roleChecker.getContext(request);
        return mapper.toInvoiceResponse(invoiceService.getInvoice(ctx.getTenantId(), id));
    }

    @PostMapping("/invoices/{id}/approve")
    public Dtos.InvoiceResponse approveInvoice(
            HttpServletRequest request,
            @PathVariable UUID id) {
        RequestContext ctx = roleChecker.requireRole(request,
                UserRole.INVOICE_APPROVER, UserRole.FINANCE);
        return mapper.toInvoiceResponse(invoiceService.approveInvoice(
                ctx.getTenantId(), id, ctx.getUserId().toString(), ctx.getUserEmail()));
    }

    @PostMapping("/payments")
    public Dtos.PaymentResponse createPayment(
            HttpServletRequest request,
            @Valid @RequestBody Dtos.PaymentCreate body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        RequestContext ctx = roleChecker.requireRole(request, UserRole.FINANCE);
        return mapper.toPaymentResponse(paymentService.createPayment(
                ctx.getTenantId(), body, idempotencyKey,
                ctx.getUserId().toString(), ctx.getUserEmail()));
    }

    @GetMapping("/customers/{id}/aging")
    public Dtos.CustomerAgingResponse getAging(
            HttpServletRequest request,
            @PathVariable UUID id) {
        RequestContext ctx = roleChecker.requireRole(request, UserRole.FINANCE, UserRole.AUDITOR);
        return agingService.getCustomerAging(ctx.getTenantId(), id);
    }

    @GetMapping("/journal-entries")
    public List<Dtos.JournalEntryResponse> getJournalEntries(
            HttpServletRequest request,
            @RequestParam(required = false) UUID invoice) {
        RequestContext ctx = roleChecker.requireRole(request, UserRole.FINANCE, UserRole.AUDITOR);
        if (invoice == null) {
            return List.of();
        }
        return journalService.getEntriesForInvoice(ctx.getTenantId(), invoice).stream()
                .map(mapper::toJournalEntryResponse)
                .toList();
    }
}

@RestController
class HealthController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
