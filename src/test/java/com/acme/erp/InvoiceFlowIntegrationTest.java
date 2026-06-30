package com.acme.erp;

import com.acme.erp.dto.Dtos;
import com.acme.erp.entity.*;
import com.acme.erp.entity.enums.UserRole;
import com.acme.erp.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantRepository tenantRepository;
    @Autowired LegalEntityRepository legalEntityRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired GLAccountRepository glAccountRepository;
    @Autowired AccountingPeriodRepository accountingPeriodRepository;
    @Autowired ExchangeRateRepository exchangeRateRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private UUID tenantId;
    private UUID entityId;
    private UUID customerId;
    private String creatorToken;
    private String approverToken;
    private String financeToken;

    @BeforeEach
    void setUp() throws Exception {
        tenantRepository.deleteAll();

        Tenant tenant = tenantRepository.save(Tenant.builder().name("Test").baseCurrency("USD").build());
        tenantId = tenant.getId();

        LegalEntity entity = legalEntityRepository.save(LegalEntity.builder()
                .tenant(tenant).name("HQ").code("HQ").currency("USD").build());
        entityId = entity.getId();

        glAccountRepository.save(GLAccount.builder().tenantId(tenantId).legalEntityId(entityId)
                .code("1000").name("Cash").accountType(com.acme.erp.entity.enums.GLAccountType.ASSET).build());
        glAccountRepository.save(GLAccount.builder().tenantId(tenantId).legalEntityId(entityId)
                .code("1200").name("AR").accountType(com.acme.erp.entity.enums.GLAccountType.ASSET).build());
        glAccountRepository.save(GLAccount.builder().tenantId(tenantId).legalEntityId(entityId)
                .code("4000").name("Revenue").accountType(com.acme.erp.entity.enums.GLAccountType.REVENUE).build());

        LocalDate today = LocalDate.now();
        accountingPeriodRepository.save(AccountingPeriod.builder()
                .tenantId(tenantId).legalEntityId(entityId).name("Current")
                .startDate(today.withDayOfMonth(1)).endDate(today.withDayOfMonth(today.lengthOfMonth()))
                .closed(false).build());

        exchangeRateRepository.save(ExchangeRate.builder()
                .tenantId(tenantId).fromCurrency("USD").toCurrency("USD")
                .rate(BigDecimal.ONE).effectiveDate(today.minusDays(1)).build());

        userRepository.save(User.builder().tenant(tenant).email("creator@test.com")
                .passwordHash(passwordEncoder.encode("pass")).role(UserRole.INVOICE_CREATOR).build());
        userRepository.save(User.builder().tenant(tenant).email("approver@test.com")
                .passwordHash(passwordEncoder.encode("pass")).role(UserRole.INVOICE_APPROVER).build());
        userRepository.save(User.builder().tenant(tenant).email("finance@test.com")
                .passwordHash(passwordEncoder.encode("pass")).role(UserRole.FINANCE).build());

        customerId = customerRepository.save(Customer.builder()
                .tenantId(tenantId).legalEntityId(entityId).name("Cust").code("C1").currency("USD").build()).getId();

        creatorToken = login("creator@test.com");
        approverToken = login("approver@test.com");
        financeToken = login("finance@test.com");
    }

    private String login(String email) throws Exception {
        Dtos.LoginRequest req = new Dtos.LoginRequest();
        req.setEmail(email);
        req.setPassword("pass");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("access_token").asText();
    }

    @Test
    void fullInvoicePaymentFlow() throws Exception {
        String invoiceJson = """
                {
                  "customer_id": "%s",
                  "legal_entity_id": "%s",
                  "invoice_date": "%s",
                  "due_date": "%s",
                  "tax_amount": 50.00,
                  "line_items": [
                    {"description": "Services", "quantity": 10, "unit_price": 100.00}
                  ]
                }
                """.formatted(customerId, entityId, LocalDate.now(), LocalDate.now().plusDays(30));

        MvcResult createResult = mockMvc.perform(post("/api/v1/invoices")
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.total_amount", is(1050.0)))
                .andReturn();

        String invoiceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/approve")
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));

        mockMvc.perform(get("/api/v1/journal-entries?invoice=" + invoiceId)
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        String paymentJson = """
                {
                  "customer_id": "%s",
                  "legal_entity_id": "%s",
                  "payment_date": "%s",
                  "amount": 500.00,
                  "allocation_method": "FIFO"
                }
                """.formatted(customerId, entityId, LocalDate.now());

        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("Authorization", "Bearer " + financeToken)
                        .header("Idempotency-Key", "test-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unallocated_amount", is(0.0)));

        mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PARTIALLY_PAID")))
                .andExpect(jsonPath("$.amount_due", is(550.0)));

        mockMvc.perform(get("/api/v1/customers/" + customerId + "/aging")
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets.total", is(550.0)));
    }
}
