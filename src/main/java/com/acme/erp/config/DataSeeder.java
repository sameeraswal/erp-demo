package com.acme.erp.config;

import com.acme.erp.entity.*;
import com.acme.erp.entity.enums.GLAccountType;
import com.acme.erp.entity.enums.UserRole;
import com.acme.erp.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final GLAccountRepository glAccountRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (tenantRepository.count() > 0) {
            log.info("Database already seeded, skipping.");
            return;
        }

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Acme Corp")
                .baseCurrency("USD")
                .build());

        LegalEntity parent = legalEntityRepository.save(LegalEntity.builder()
                .tenant(tenant)
                .name("Acme Holdings Inc")
                .code("ACME-HQ")
                .currency("USD")
                .build());

        LegalEntity subsidiary = legalEntityRepository.save(LegalEntity.builder()
                .tenant(tenant)
                .parent(parent)
                .name("Acme Europe GmbH")
                .code("ACME-EU")
                .currency("EUR")
                .build());

        for (LegalEntity entity : new LegalEntity[]{parent, subsidiary}) {
            for (String[] acct : new String[][]{
                    {"1000", "Cash", "ASSET"},
                    {"1200", "Accounts Receivable", "ASSET"},
                    {"4000", "Revenue", "REVENUE"},
                    {"4100", "Sales Returns", "REVENUE"},
                    {"5200", "Bad Debt Expense", "EXPENSE"}
            }) {
                glAccountRepository.save(GLAccount.builder()
                        .tenantId(tenant.getId())
                        .legalEntityId(entity.getId())
                        .code(acct[0])
                        .name(acct[1])
                        .accountType(GLAccountType.valueOf(acct[2]))
                        .build());
            }

            LocalDate today = LocalDate.now();
            YearMonth ym = YearMonth.from(today);
            accountingPeriodRepository.save(AccountingPeriod.builder()
                    .tenantId(tenant.getId())
                    .legalEntityId(entity.getId())
                    .name(ym.getMonth() + " " + ym.getYear())
                    .startDate(ym.atDay(1))
                    .endDate(ym.atEndOfMonth())
                    .closed(false)
                    .build());
        }

        LocalDate rateDate = LocalDate.now().minusDays(30);
        exchangeRateRepository.save(ExchangeRate.builder()
                .tenantId(tenant.getId())
                .fromCurrency("EUR")
                .toCurrency("USD")
                .rate(new BigDecimal("1.08"))
                .effectiveDate(rateDate)
                .build());
        exchangeRateRepository.save(ExchangeRate.builder()
                .tenantId(tenant.getId())
                .fromCurrency("USD")
                .toCurrency("USD")
                .rate(BigDecimal.ONE)
                .effectiveDate(rateDate)
                .build());

        for (String[] user : new String[][]{
                {"creator@acme.com", "INVOICE_CREATOR", "creator123"},
                {"approver@acme.com", "INVOICE_APPROVER", "approver123"},
                {"finance@acme.com", "FINANCE", "finance123"},
                {"auditor@acme.com", "AUDITOR", "auditor123"},
                {"admin@acme.com", "ADMIN", "admin123"}
        }) {
            userRepository.save(User.builder()
                    .tenant(tenant)
                    .email(user[0])
                    .passwordHash(passwordEncoder.encode(user[2]))
                    .role(UserRole.valueOf(user[1]))
                    .build());
        }

        customerRepository.save(Customer.builder()
                .tenantId(tenant.getId())
                .legalEntityId(parent.getId())
                .name("Globex Corporation")
                .code("GLOBEX")
                .email("ap@globex.com")
                .currency("USD")
                .build());
        customerRepository.save(Customer.builder()
                .tenantId(tenant.getId())
                .legalEntityId(parent.getId())
                .name("Initech LLC")
                .code("INITECH")
                .email("billing@initech.com")
                .currency("USD")
                .build());
        customerRepository.save(Customer.builder()
                .tenantId(tenant.getId())
                .legalEntityId(subsidiary.getId())
                .name("Euro Customer AG")
                .code("EUROCUST")
                .email("finance@eurocust.de")
                .currency("EUR")
                .build());

        log.info("Seeded tenant: {}", tenant.getId());
        log.info("Users: creator@acme.com / creator123, finance@acme.com / finance123, etc.");
    }
}
