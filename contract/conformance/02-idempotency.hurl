# Idempotency: create, replay, conflict, missing key

# 1. First request creates the leg
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-02a
Content-Type: application/json
{
  "transactionId": "{{run}}-02a",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "1.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 201
[Captures]
ref: jsonpath "$.participantReference"
[Asserts]
jsonpath "$.status" == "COMPLETED"
jsonpath "$.direction" == "DEBIT"
jsonpath "$.transactionId" == "{{run}}-02a"
jsonpath "$.provenance" == "SYNTHETIC"

# 2. Identical replay returns the original result with 200
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-02a
Content-Type: application/json
{
  "transactionId": "{{run}}-02a",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "1.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 200
[Asserts]
jsonpath "$.status" == "COMPLETED"
jsonpath "$.participantReference" == "{{ref}}"

# 3. Same key, different body is a conflict
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-02a
Content-Type: application/json
{
  "transactionId": "{{run}}-02a",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "2.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 409
[Asserts]
jsonpath "$.code" == "IDEMPOTENCY_CONFLICT"

# 4. Missing Idempotency-Key
POST {{base}}/finsim/v1/payments
Content-Type: application/json
{
  "transactionId": "{{run}}-02b",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "1.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 400
[Asserts]
jsonpath "$.code" == "MISSING_IDEMPOTENCY_KEY"

# 5. Too-short key
POST {{base}}/finsim/v1/payments
Idempotency-Key: short
Content-Type: application/json
{
  "transactionId": "{{run}}-02c",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "1.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 400
[Asserts]
jsonpath "$.code" == "MISSING_IDEMPOTENCY_KEY"

# 6. A credit leg works too
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-02d
Content-Type: application/json
{
  "transactionId": "{{run}}-02d",
  "direction": "CREDIT",
  "accountReference": "ACC-2",
  "money": { "amount": "1.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 201
[Asserts]
jsonpath "$.status" == "COMPLETED"
jsonpath "$.direction" == "CREDIT"
