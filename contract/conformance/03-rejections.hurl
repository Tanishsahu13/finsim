# Business rejections: 422 with a reason code

# Insufficient funds
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-03a
Content-Type: application/json
{
  "transactionId": "{{run}}-03a",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "999999999.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 422
[Asserts]
jsonpath "$.code" == "AM04"

# Replaying a rejection returns the same rejection
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-03a
Content-Type: application/json
{
  "transactionId": "{{run}}-03a",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "999999999.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 422
[Asserts]
jsonpath "$.code" == "AM04"

# Unknown account
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-03b
Content-Type: application/json
{
  "transactionId": "{{run}}-03b",
  "direction": "CREDIT",
  "accountReference": "ACC-DOES-NOT-EXIST",
  "money": { "amount": "1.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 422
[Asserts]
jsonpath "$.code" == "AC01"

# Closed account
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-03c
Content-Type: application/json
{
  "transactionId": "{{run}}-03c",
  "direction": "CREDIT",
  "accountReference": "ACC-CLOSED",
  "money": { "amount": "1.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 422
[Asserts]
jsonpath "$.code" == "AC04"

# Zero amount
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-03d
Content-Type: application/json
{
  "transactionId": "{{run}}-03d",
  "direction": "CREDIT",
  "accountReference": "ACC-2",
  "money": { "amount": "0.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 422
[Asserts]
jsonpath "$.code" == "AM02"

# Unsupported currency
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-03e
Content-Type: application/json
{
  "transactionId": "{{run}}-03e",
  "direction": "CREDIT",
  "accountReference": "ACC-2",
  "money": { "amount": "1.00", "currency": "XXX" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 422
[Asserts]
jsonpath "$.code" == "AM03"
