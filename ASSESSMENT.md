# Principal Engineer ERP Assessment — Submission

**Candidate deliverable for:** Multi-Tenant Invoicing and Accounts Receivable Module  
**Repository:** `/Users/sameer/code/erp_demo`  
**Stack:** Java 17, Spring Boot 3.2, Spring Data JPA, MySQL 8, Docker Compose

---

## Time Tracking

| Section | Estimated Time |
|---------|----------------|
| 1. Data Model & Architecture Design | ~55 min |
| 2. Working Prototype | ~1 hr 45 min |
| 3. Financial Controls & Compliance | ~35 min |
| 4. Enterprise Experience Showcase | ~25 min |
| **Total** | **~3 hr 40 min** |

---

# 1. Data Model and Architecture Design

## 1.1 Entity Relationship Model

```
Tenant (1) ──< LegalEntity (parent/child hierarchy)
  │
  ├──< User (RBAC roles)
  ├──< Customer ──< Invoice ──< InvoiceLineItem
  │                    │
  │                    └──< PaymentAllocation >── Payment
  ├──< GLAccount ──< JournalEntryLine >── JournalEntry
  ├──< AccountingPeriod
  ├──< ExchangeRate
  ├──< CreditMemo
  └──< AuditLog
```

### Core Entities

| Entity | Purpose | Key Fields |
|--------|---------|------------|
| **Customer** | AR counterparty | `tenant_id`, `legal_entity_id`, `code`, `currency` |
| **Invoice** | Receivable document | `status`, `total_amount`, `amount_paid`, `amount_due`, `currency`, `exchange_rate`, `base_total_amount` |
| **InvoiceLineItem** | Revenue detail | `quantity`, `unit_price`, `line_total`, optional `revenue_account_id` |
| **Payment** | Cash receipt | `amount`, `unallocated_amount`, `idempotency_key` |
| **PaymentAllocation** | Links payment to invoice(s) | `amount`, `allocation_method` (FIFO/MANUAL) |
| **CreditMemo** | Reduces AR balance | `amount`, `invoice_id` (optional), `reason` |
| **GLAccount** | Chart of accounts | `code`, `account_type` (ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE) |
| **JournalEntry** | Immutable GL posting | `source_type`, `source_id`, `status` (POSTED/REVERSED) |
| **JournalEntryLine** | Debit/credit lines | `debit`, `credit`, `base_debit`, `base_credit` |

## 1.2 Multi-Tenant and Multi-Entity Design

**Tenant isolation** uses a shared-database, shared-schema model with `tenant_id` on every business table:

- `JwtAuthFilter` requires `X-Tenant-Id` header and validates JWT `tenant_id` matches
- All repository queries include `tenantId` in WHERE clauses
- Unique constraints are tenant-scoped (e.g., `UNIQUE(tenant_id, invoice_number)`)
- Integration tests verify cross-tenant access is blocked

**Multi-entity (subsidiary) structure:**

- `LegalEntity` belongs to a `Tenant` with optional `parent_entity_id` for hierarchies
- Each entity has its own chart of accounts (`GLAccount.legal_entity_id`)
- Invoices and payments are posted to a specific `legal_entity_id`
- Intercompany invoices: subsidiary A invoices subsidiary B by creating a customer record representing the sister entity; elimination entries would be generated at consolidation (out of scope for prototype, but entity hierarchy supports it)

**Trade-off:** Shared schema is cost-effective for mid-market SaaS but requires rigorous query discipline. Enterprise deployments may add MySQL views or application-level row filters as defense-in-depth.

## 1.3 Currency Handling

| Concept | Implementation |
|---------|----------------|
| **Base currency** | `Tenant.baseCurrency` (e.g., USD) — used for consolidated reporting |
| **Transaction currency** | `Invoice.currency`, `Payment.currency` — what the customer sees/pays |
| **Entity currency** | `LegalEntity.currency` — functional currency per subsidiary |
| **Exchange rates** | `ExchangeRate` table: `(tenant_id, from_currency, to_currency, effective_date, rate)` |
| **Conversion** | On invoice creation: `base_total_amount = total_amount × rate` |
| **GL posting** | Journal lines store both transaction and base amounts for multi-currency reconciliation |

Rates are point-in-time (invoice date / payment date). Revaluation of open AR balances at period-end would be a separate batch job (documented, not implemented).

## 1.4 Audit Trail Implementation

The `AuditLog` table captures:

- `entity_type`, `entity_id` — what changed
- `action` — CREATE, APPROVE, POST, VOID, etc.
- `actor_id`, `actor_email` — who performed the action
- `changes` — JSON diff of key fields
- `reason` — required for voids, write-offs, period adjustments
- `created_at` — immutable timestamp

Business entities also carry `created_by`, `updated_by`, `created_at`, `updated_at`, and optimistic locking via `@Version`. Journal entries are **immutable** — corrections create reversal entries, never in-place edits.

## 1.5 Accounting Integration

### Invoice Approval → GL Entry

```
DR  Accounts Receivable (1200)    $1,010.00
    CR  Revenue (4000)                        $1,010.00
```

Triggered by `POST /invoices/{id}/approve`. Entry is linked via `source_type=INVOICE`, `source_id=invoice.id`.

### Payment Receipt → GL Entry

```
DR  Cash (1000)                   $500.00
    CR  Accounts Receivable (1200)            $500.00
```

Full payment amount is posted to GL regardless of allocation; allocations update the AR subledger.

### Partial Payments

- `PaymentAllocation` records how much applies to each invoice
- Invoice `amount_paid` and `amount_due` are updated atomically in a `@Transactional` service method
- Status transitions: APPROVED → PARTIALLY_PAID → PAID

### Overpayments

- Excess amount stored in `Payment.unallocated_amount`
- Can be applied to future invoices or refunded (future enhancement)
- GL posts the full cash receipt; unallocated portion represents a customer credit liability

### Credit Memos

- DR Sales Returns (4100) / CR AR (1200)
- Reduces `invoice.amount_due` without counting as a "payment"
- Preferred over editing approved invoices (see compliance section)

### Write-offs

- DR Bad Debt Expense (5200) / CR AR (1200)
- Transitions invoice to WRITTEN_OFF status
- Requires FINANCE role + audit reason

## 1.6 State Management

### Invoice Lifecycle

```
DRAFT ──approve──▶ APPROVED ──send──▶ SENT
  │                   │                 │
  void                void              ├── partial pay ──▶ PARTIALLY_PAID ──▶ PAID
  ▼                   ▼                 │                      │
 VOID                VOID               └── write-off ──▶ WRITTEN_OFF
```

### Business Rules by State

| State | Edit | Approve | Void | Receive Payment | Write Off |
|-------|------|---------|------|-----------------|-----------|
| DRAFT | Yes | Yes | Yes | No | No |
| APPROVED | No | No | Yes | Yes | No |
| SENT | No | No | Yes | Yes | Yes |
| PARTIALLY_PAID | No | No | No | Yes | Yes |
| PAID | No | No | No | No | No |
| VOID | No | No | No | No | No |
| WRITTEN_OFF | No | No | No | No | No |

Voiding a non-draft invoice generates GL reversal entries.

## 1.7 API Design

### Key Endpoints

**POST /api/v1/invoices**
```json
{
  "customer_id": "uuid",
  "legal_entity_id": "uuid",
  "invoice_date": "2026-06-01",
  "due_date": "2026-07-01",
  "currency": "USD",
  "tax_amount": "50.00",
  "line_items": [
    {"description": "Consulting", "quantity": "40", "unit_price": "150.00"}
  ]
}
```

**POST /api/v1/payments**
```json
{
  "customer_id": "uuid",
  "legal_entity_id": "uuid",
  "payment_date": "2026-06-15",
  "amount": "3000.00",
  "allocation_method": "FIFO",
  "manual_allocations": [{"invoice_id": "uuid", "amount": "1500.00"}]
}
```

**GET /api/v1/customers/{id}/aging**
```json
{
  "customer_id": "uuid",
  "buckets": {
    "current": "0.00", "days_1_30": "500.00",
    "days_31_60": "0.00", "days_61_90": "0.00",
    "days_over_90": "0.00", "total": "500.00"
  },
  "invoices": []
}
```

### Idempotency

Payment endpoint accepts `Idempotency-Key` header:
- Stored in `IdempotencyRecord` with request hash and response body
- 24-hour TTL; same key + same body returns cached response
- Same key + different body returns 409 Conflict
- Prevents duplicate payments from network retries

### Bulk Operations (Design)

| Operation | Approach |
|-----------|----------|
| Batch invoicing | `POST /invoices/batch` with array of invoices; processed in single transaction with per-item error reporting |
| Bulk payment import | CSV upload → staging table → validation → `POST /payments/batch` with idempotency per row |
| Async processing | Large batches queued to worker; status polled via `GET /jobs/{id}` |

Not implemented in prototype; idempotency infrastructure supports safe retry.

---

# 2. Working Prototype

Implemented in this repository. See [README.md](README.md) for setup and [samples/api-flow.sh](samples/api-flow.sh) for API walkthrough.

### Implemented Features

- [x] All 6 required API endpoints
- [x] Multi-tenant isolation (header + JWT validation)
- [x] Invoice state transitions with validation
- [x] Automatic GL entry generation on approval and payment
- [x] FIFO and manual payment allocation
- [x] Partial payments and overpayment tracking
- [x] Audit logging on create/approve/payment
- [x] Payment idempotency
- [x] AR aging report (current, 1-30, 31-60, 61-90, 90+)
- [x] Multi-currency with exchange rate conversion (bonus)
- [x] Multi-entity structure with parent/subsidiary (bonus)
- [x] RBAC with 5 roles (bonus)
- [x] Closed accounting period blocking (bonus)
- [x] Docker Compose setup with MySQL 8
- [x] Integration tests

### Assumptions

- **Accounting standard:** US GAAP
- Revenue recognized at invoice approval (point-in-time); deferred revenue schedules documented but not implemented
- Tax is invoice-level flat amount; per-line tax is a future enhancement
- Single revenue account (4000) for all line items

---

# 3. Financial Controls and Compliance Analysis

## 3.1 Data Integrity

### AR Subledger ↔ GL Reconciliation

The AR subledger balance for a customer equals the sum of `invoice.amount_due` across open invoices. This must reconcile to the GL AR account (1200) balance:

```
GL AR Balance = Σ(Invoice AR postings) − Σ(Payment AR credits) − Σ(Credit Memos) − Σ(Write-offs)
```

A nightly reconciliation job compares:
1. `SUM(amount_due) GROUP BY tenant_id, legal_entity_id` from invoices
2. `SUM(debit − credit) WHERE account_code = '1200'` from journal entry lines

Discrepancies halt period close and alert finance ops.

### Preventing Duplicate Payments

| Layer | Control |
|-------|---------|
| Database | `UNIQUE(tenant_id, idempotency_key)` on payments |
| Application | Idempotency service returns cached response for retries |
| Business | Payment number auto-generated sequentially; duplicate reference warnings |
| External | Bank reconciliation matches payment reference to bank statement |

### Critical Constraints & Validations

**Database constraints:**
- Application-level validation ensures `amount_due >= 0`
- Journal entries validated to balance before commit
- `UNIQUE(tenant_id, invoice_number)`, `UNIQUE(tenant_id, payment_number)`
- Foreign keys with no cascading deletes on financial data
- `NOT NULL` on all monetary amounts via JPA annotations

**Application validations:**
- Journal entries must balance (debits = credits) before commit
- Payment allocations cannot exceed invoice `amount_due`
- Cannot post to closed accounting periods
- State machine guards prevent invalid transitions
- Optimistic locking (`@Version`) prevents lost updates

## 3.2 Audit and Compliance (SOX)

### SOX Audit Trail Requirements

| Requirement | Implementation |
|-------------|----------------|
| Who | `actor_id`, `actor_email` on every audit log entry |
| What | `entity_type`, `action`, `changes` JSON diff |
| When | Immutable `created_at` timestamps (server-generated) |
| Why | `reason` field required for voids, write-offs, adjustments |
| Completeness | Audit logs are append-only; no UPDATE/DELETE |
| Retention | 7-year minimum; archived to WORM storage |

### Invoice Amendments After Approval

**Never edit an approved invoice.** Two approved approaches:

1. **Void and reissue:** Void the original (GL reversal), create new invoice. Full audit trail. Used for material errors.
2. **Credit memo:** Issue credit memo against original invoice. Partial correction. Preferred for price adjustments, returns.

The prototype enforces `InvoiceStateMachine.assertEditable()` — only DRAFT invoices can be modified.

### Segregation of Duties

| Role | Permissions |
|------|-------------|
| INVOICE_CREATOR | Create/edit draft invoices |
| INVOICE_APPROVER | Approve invoices (triggers GL posting) |
| FINANCE | Record payments, credit memos, write-offs |
| AUDITOR | Read-only access to all data, aging, journal entries |
| ADMIN | All permissions (break-glass; itself audited) |

SOX requires that no single person can create, approve, and record payment for the same invoice. The role matrix enforces this.

## 3.3 Period Close

### Month/Year-End Process

1. **Freeze subledgers:** AR stops accepting back-dated entries past close date
2. **Run reconciliation:** AR subledger vs GL, aging vs detail
3. **Revenue recognition:** Any deferred revenue schedules post recognition entries
4. **Close period:** `AccountingPeriod.closed = true`
5. **Lock:** All posting to closed periods rejected with clear error message

### Posting to Closed Periods

```java
// JournalService.assertPeriodOpen()
accountingPeriodRepository
    .findByLegalEntityIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(...)
    .filter(AccountingPeriod::isClosed)
    .ifPresent(period -> throw new ValidationException("Period is closed"));
```

### Prior-Period Adjustments

- Posted to current period with `source_type = PRIOR_PERIOD_ADJUSTMENT`
- Reference original period in description and audit `reason`
- Disclosure in financial statements per ASC 250
- Requires FINANCE + ADMIN approval workflow

## 3.4 Operational Concerns

### Deployment Strategy

| Phase | Approach |
|-------|----------|
| Pre-deploy | Database backup, migration dry-run on staging |
| Deploy | Blue-green deployment; new version on green, health check, switch traffic |
| Migration | Forward-only Flyway migrations; backward-compatible schema changes |
| Rollback | Switch traffic back to blue; never roll back migrations in production |
| Validation | Post-deploy smoke tests: create invoice, approve, record payment |

### Backup and Recovery

- **RPO:** 1 hour (MySQL binlog replication to S3)
- **RTO:** 4 hours (automated restore + validation)
- **Testing:** Quarterly restore drills to staging environment
- **Encryption:** AES-256 at rest, TLS in transit
- Financial data retained 7+ years per regulatory requirements

### Payment Transaction Failures

All payment operations run in a single `@Transactional` service method:

```
BEGIN
  → Create Payment record
  → Create PaymentAllocations
  → Update Invoice balances
  → Post GL journal entry
  → Write audit log
COMMIT (or ROLLBACK on any failure)
```

If the process crashes mid-transaction, MySQL/InnoDB rolls back atomically — no partial payments. Idempotency keys allow safe client retries after timeout.

---

# 4. Enterprise Experience Showcase

## 4.1 Financial/ERP System Built

### Times Internet – Gaana+ Subscription Platform
**Project:** Premium Subscription & Billing Platform

**Responsibilities**
- Built the subscription platform powering **Gaana+** premium memberships.
- Developed features for subscription lifecycle management, including activation, renewal, and cancellation.
- Integrated payment gateways to support seamless premium purchases.
- Enabled premium user acquisition through subscription-based monetization.

**Business Impact**
- Supported a scalable subscription business model for millions of users.
- Improved payment reliability and subscription management.

---

### Snapdeal – Seller Platform

**Project:** Seller Management & Settlement Platform

**Responsibilities**
- Developed and managed the seller platform for product listing and catalog management.
- Built payment settlement workflows for marketplace sellers.
- Automated settlement calculations and payout processing.
- Improved operational efficiency for seller onboarding and financial operations.

**Business Impact**
- Enabled reliable seller payment settlements at marketplace scale.
- Reduced manual intervention in seller financial operations.

---

### Paisabazaar – Credit Report Payment Platform

**Project:** Payment & Financial Reconciliation System

**Responsibilities**
- Built payment products for the Paisabazaar Credit Report platform.
- Developed payment processing and transaction management services.
- Implemented payment reconciliation and financial reporting workflows.
- Designed revenue calculation and bookkeeping systems for accurate financial accounting.
- Ensured end-to-end financial data integrity across payment and accounting processes.

**Business Impact**
- Automated reconciliation and revenue accounting.
- Improved financial reporting accuracy and operational efficiency.
- Enabled reliable financial operations for high-volume payment transactions.

## 4.2 Data Integrity Issue Resolved

### Problem
In the **Gaana+** payment platform, a concurrency issue allowed the same coupon code to be redeemed by multiple users simultaneously. A similar challenge existed in the **Spin the Wheel** reward system, where the same voucher code could be allocated to multiple users under high traffic conditions.

### Solution
- Designed and implemented a **distributed locking mechanism using Redis** to enforce exclusive access during coupon and voucher allocation.
- Ensured that only one transaction could reserve and consume a coupon or voucher code at any given time.
- Integrated the locking mechanism into the payment and reward redemption workflows without impacting system performance.

### Result
- Eliminated duplicate coupon and voucher allocations caused by race conditions.
- Maintained **data integrity** and **transaction consistency** even during periods of high concurrency.
- Improved customer trust by ensuring each promotional code was redeemed only once.
- Delivered a scalable solution capable of handling enterprise-scale traffic.

## 4.3 Period-End Close Experience

## 4.3 Period-End Close Experience

### Revenue Reporting & Financial Close Automation

**Project:** Revenue Data Processing and Financial Close Systems

### Responsibilities
- Designed and managed batch jobs that pushed revenue data to centralized financial reporting systems.
- Built data pipelines to process revenue generated from subscriptions, leads, and appointments.
- Ensured accurate aggregation and synchronization of financial data across multiple downstream systems.
- Developed scalable and fault-tolerant processing frameworks capable of handling high transaction volumes.
- Implemented monitoring, retry mechanisms, and validation checks to ensure reliable data delivery.
- Optimized processing jobs for performance, consistency, and operational resilience.

### Enterprise Experience
- **Times Internet:** Managed revenue processing pipelines for subscription-based products, ensuring timely and accurate financial reporting.
- **Paisabazaar:** Built scalable systems to process and reconcile revenue generated from leads, appointments, and payment transactions.

### Business Impact
- Improved the reliability and accuracy of period-end financial reporting.
- Reduced manual intervention through automated revenue processing pipelines.
- Enabled timely financial close by ensuring complete and consistent revenue data.
- Delivered a robust, scalable solution capable of processing enterprise-scale transaction volumes

## 4.4 Multi-Tenant Data Modeling Challenge

### Multi-Tenant Authentication & SaaS Platform

**Project:** Multi-Tenant Authentication Framework and Communication Platform

### Responsibilities
- Designed and implemented a **multi-tenant authentication framework** supporting multiple organizations on a single platform.
- Built a flexible authentication system allowing each tenant to configure its own authentication strategy, authorization rules, and API security policies.
- Enabled tenant-specific authentication mechanisms, credentials, and API route protection while maintaining complete isolation between organizations.
- Designed the data model to support tenant isolation, extensibility, and future onboarding of new organizations with minimal configuration.

### SaaS Communication Platform

**Project:** Multi-Tenant Communication Service

### Responsibilities
- Built a communication platform that operates as a **Software-as-a-Service (SaaS)** solution.
- Designed the service to be consumed by multiple external organizations from a shared infrastructure.
- Implemented tenant-aware routing, configuration management, and secure data isolation.
- Supported multiple communication channels and customizable workflows for different enterprise customers.
- Developed the platform with scalability, high availability, and extensibility as core design principles.

### Business Impact
- Enabled rapid onboarding of new enterprise customers without requiring separate deployments.
- Reduced operational overhead through a shared, configurable multi-tenant architecture.
- Improved platform scalability while maintaining strong tenant isolation and security.
- Created a reusable SaaS platform capable of serving multiple organizations with customized authentication and communication workflows.

---

# AI Tool Usage

| Tool | How Used |
|------|----------|
| **Cursor AI (Claude)** | Scaffolding Spring Boot project structure, generating JPA entities and repositories, writing integration tests, drafting documentation, and reviewing financial domain logic for GAAP correctness |
| **Human oversight** | All architectural decisions (state machine, GL posting rules, RBAC matrix, reconciliation approach) were made deliberately; AI accelerated implementation of well-understood patterns |

The AI was most valuable for boilerplate (entities, DTOs, test setup) and documentation formatting. Financial business rules were specified first, then validated against standard accounting practice.

---

# Appendix: Database Schema

See `src/main/java/com/acme/erp/entity/` for complete JPA entity definitions. Key tables:

- `tenants`, `legal_entities`, `users`
- `customers`, `invoices`, `invoice_line_items`
- `payments`, `payment_allocations`, `credit_memos`
- `gl_accounts`, `journal_entries`, `journal_entry_lines`
- `accounting_periods`, `exchange_rates`
- `audit_logs`, `idempotency_records`

Run `docker compose up` to create tables (via Hibernate DDL) and load demo data via `DataSeeder`.
