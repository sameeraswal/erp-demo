#!/usr/bin/env bash
# Complete API flow demonstrating invoice creation, approval, payment, aging, and GL entries.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

if [ -z "${TENANT_ID:-}" ]; then
  echo "Set TENANT_ID environment variable (from docker compose logs)"
  exit 1
fi

echo "=== 1. Login as invoice creator ==="
CREATOR_TOKEN=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d '{"email":"creator@acme.com","password":"creator123"}' | jq -r .access_token)

echo "=== 2. Login as approver ==="
APPROVER_TOKEN=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d '{"email":"approver@acme.com","password":"approver123"}' | jq -r .access_token)

echo "=== 3. Login as finance ==="
FINANCE_TOKEN=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d '{"email":"finance@acme.com","password":"finance123"}' | jq -r .access_token)

echo "=== 4. Get customer and entity IDs ==="
echo "Run: docker compose exec db mysql -uerp -perp_secret erp_ar -e \"SELECT id, code FROM customers LIMIT 1;\""
read -p "Enter customer UUID: " CUSTOMER_ID
read -p "Enter legal entity UUID: " ENTITY_ID

echo "=== 5. Create invoice ==="
INVOICE=$(curl -s -X POST "$BASE_URL/api/v1/invoices" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Authorization: Bearer $CREATOR_TOKEN" \
  -d "{
    \"customer_id\": \"$CUSTOMER_ID\",
    \"legal_entity_id\": \"$ENTITY_ID\",
    \"invoice_date\": \"$(date +%Y-%m-%d)\",
    \"due_date\": \"$(date -v+30d +%Y-%m-%d 2>/dev/null || date -d '+30 days' +%Y-%m-%d)\",
    \"tax_amount\": \"50.00\",
    \"line_items\": [
      {\"description\": \"Professional Services - Q2\", \"quantity\": \"40\", \"unit_price\": \"150.00\"},
      {\"description\": \"Software License\", \"quantity\": \"1\", \"unit_price\": \"500.00\"}
    ]
  }")
echo "$INVOICE" | jq .
INVOICE_ID=$(echo "$INVOICE" | jq -r .id)

echo "=== 6. Approve invoice (generates GL entries) ==="
curl -s -X POST "$BASE_URL/api/v1/invoices/$INVOICE_ID/approve" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Authorization: Bearer $APPROVER_TOKEN" | jq .

echo "=== 7. View GL journal entries ==="
curl -s "$BASE_URL/api/v1/journal-entries?invoice=$INVOICE_ID" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Authorization: Bearer $FINANCE_TOKEN" | jq .

echo "=== 8. Record partial payment (FIFO allocation) ==="
curl -s -X POST "$BASE_URL/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Authorization: Bearer $FINANCE_TOKEN" \
  -H "Idempotency-Key: demo-payment-001" \
  -d "{
    \"customer_id\": \"$CUSTOMER_ID\",
    \"legal_entity_id\": \"$ENTITY_ID\",
    \"payment_date\": \"$(date +%Y-%m-%d)\",
    \"amount\": \"3000.00\",
    \"allocation_method\": \"FIFO\",
    \"reference\": \"WIRE-12345\"
  }" | jq .

echo "=== 9. Get invoice with payment history ==="
curl -s "$BASE_URL/api/v1/invoices/$INVOICE_ID" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Authorization: Bearer $CREATOR_TOKEN" | jq .

echo "=== 10. AR aging report ==="
curl -s "$BASE_URL/api/v1/customers/$CUSTOMER_ID/aging" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Authorization: Bearer $FINANCE_TOKEN" | jq .

echo "=== Done ==="
