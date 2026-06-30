# ERP Demo — Multi-Tenant Invoicing & Accounts Receivable Module

A prototype AR module demonstrating enterprise financial system design: multi-tenant isolation, GL integration, invoice lifecycle management, payment allocation, and AR aging.

**Stack:** Java 17 · Spring Boot 3.2 · Spring Data JPA · MySQL 8 · Docker Compose

## Quick Start

```bash
docker compose up --build
```

API available at `http://localhost:8080`  
Health check at `http://localhost:8080/health`  
**Swagger UI:** `http://localhost:8080/swagger-ui/index.html`

On startup, the database is seeded with a demo tenant, users, customers, GL accounts, and exchange rates. The tenant ID is printed to the container logs.

### Local Development (without Docker)

```bash
# Start MySQL on port 3307
docker compose up db -d

mvn spring-boot:run
```

### Run Tests

```bash
mvn test
```

## Authentication

All endpoints (except `/health` and `/api/v1/auth/login`) require:

| Header | Description |
|--------|-------------|
| `X-Tenant-Id` | UUID of the tenant |
| `Authorization` | `Bearer <jwt_token>` |

### Demo Users

| Email | Password | Role |
|-------|----------|------|
| creator@acme.com | creator123 | INVOICE_CREATOR |
| approver@acme.com | approver123 | INVOICE_APPROVER |
| finance@acme.com | finance123 | FINANCE |
| auditor@acme.com | auditor123 | AUDITOR |
| admin@acme.com | admin123 | ADMIN |

After `docker compose up`, get the tenant ID from logs, then login:

```bash
export TENANT_ID="<tenant-uuid-from-logs>"

curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d '{"email":"finance@acme.com","password":"finance123"}' | jq .
```

## API Endpoints

| Method | Path | Role | Description |
|--------|------|------|-------------|
| POST | `/api/v1/invoices` | CREATOR | Create draft invoice |
| GET | `/api/v1/invoices/{id}` | Any | Get invoice with balance & payments |
| POST | `/api/v1/invoices/{id}/approve` | APPROVER | Approve & post GL entries |
| POST | `/api/v1/payments` | FINANCE | Record payment with allocation |
| GET | `/api/v1/customers/{id}/aging` | FINANCE/AUDITOR | AR aging report |
| GET | `/api/v1/journal-entries?invoice={id}` | FINANCE/AUDITOR | GL entries for invoice |

See [`samples/api-flow.sh`](samples/api-flow.sh) for a complete walkthrough.

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│ Spring Boot │────▶│   Services   │────▶│     MySQL 8     │
│ Controllers │     │ Invoice/Pay  │     │  Multi-tenant   │
│ JWT Filter  │     │ Journal/Audit│     │  GL + AR tables │
└─────────────┘     └──────────────┘     └─────────────────┘
```

- **Tenant isolation:** Every table has `tenant_id`; JWT filter validates tenant matches `X-Tenant-Id`
- **GL integration:** Double-entry journal entries on invoice approval (DR AR / CR Revenue) and payment (DR Cash / CR AR)
- **State machine:** Draft → Approved → Sent → Partially Paid → Paid (with Void/Written Off)
- **Idempotency:** Payment endpoint supports `Idempotency-Key` header (24h TTL)
- **Multi-currency:** Transaction currency + base currency conversion via exchange rate table

## Documentation

Full assessment deliverables are in [`ASSESSMENT.md`](ASSESSMENT.md):

1. Data Model & Architecture Design
2. Working Prototype (this repo)
3. Financial Controls & Compliance Analysis
4. Enterprise Experience Showcase

## Deploy (share a live URL)

See **[`DEPLOY.md`](DEPLOY.md)** for step-by-step instructions.

**Fastest path:** Push to GitHub → deploy on [Railway](https://railway.app) with MySQL → share:
- Swagger: `https://YOUR-URL/swagger-ui/index.html`
- Bootstrap: `https://YOUR-URL/api/v1/bootstrap`

## Project Structure

```
erp_demo/
├── src/main/java/com/acme/erp/
│   ├── ErpApplication.java
│   ├── config/              # Security, seed data, exception handling
│   ├── entity/              # JPA entities
│   ├── repository/          # Spring Data repositories
│   ├── service/             # Business logic layer
│   ├── security/            # JWT auth & RBAC
│   ├── web/                 # REST controllers
│   └── dto/                 # Request/response DTOs
├── src/test/                # Integration tests
├── samples/                 # Sample API requests
├── docker-compose.yml
├── ASSESSMENT.md            # Full design document
└── README.md
```

## AI Tool Usage

This project was built with **Cursor AI (Claude)** assistance for scaffolding, financial domain modeling, and documentation. The architectural decisions and business rules reflect standard ERP/AR practices under GAAP assumptions.

## License

Assessment prototype — not for production use.
