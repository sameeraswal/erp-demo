package com.acme.erp.web;

import com.acme.erp.entity.Customer;
import com.acme.erp.entity.LegalEntity;
import com.acme.erp.entity.Tenant;
import com.acme.erp.repository.CustomerRepository;
import com.acme.erp.repository.LegalEntityRepository;
import com.acme.erp.repository.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Demo helper — no auth required. Use this to get IDs for Swagger testing. */
@RestController
@RequestMapping("/api/v1/bootstrap")
@RequiredArgsConstructor
@SecurityRequirements
public class BootstrapController {

    private final TenantRepository tenantRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final CustomerRepository customerRepository;

    @Operation(summary = "Get demo tenant and entity IDs (no auth)",
            description = "Call this first in Swagger. Copy tenant_id into X-Tenant-Id header or tenant_id query param.")
    @GetMapping
    public Map<String, Object> bootstrap() {
        Tenant tenant = tenantRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No tenant seeded — restart app with empty DB"));

        LegalEntity entity = legalEntityRepository.findAll().stream()
                .filter(e -> "ACME-HQ".equals(e.getCode()))
                .findFirst()
                .orElseGet(() -> legalEntityRepository.findAll().get(0));

        Customer customer = customerRepository.findAll().stream()
                .filter(c -> tenant.getId().equals(c.getTenantId()) && "GLOBEX".equals(c.getCode()))
                .findFirst()
                .orElseGet(() -> customerRepository.findAll().get(0));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenant_id", tenant.getId());
        result.put("legal_entity_id", entity.getId());
        result.put("customer_id", customer.getId());
        result.put("swagger_hint", "Set header X-Tenant-Id = tenant_id, OR add query param ?tenant_id=<uuid>");
        result.put("demo_users", Map.of(
                "creator", "creator@acme.com / creator123",
                "approver", "approver@acme.com / approver123",
                "finance", "finance@acme.com / finance123"
        ));
        return result;
    }
}
