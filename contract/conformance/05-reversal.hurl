# Reversal: a normal CREDIT with its own transactionId and reversalOf

# The original debit
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-05a
Content-Type: application/json
{
  "transactionId": "{{run}}-05a",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "5.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 201

# Reversing it succeeds
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-05b
Content-Type: application/json
{
  "transactionId": "{{run}}-05a-rev",
  "direction": "CREDIT",
  "accountReference": "ACC-1",
  "money": { "amount": "5.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "reversalOf": "{{run}}-05a",
  "provenance": "SYNTHETIC"
}
HTTP 201
[Asserts]
jsonpath "$.status" == "COMPLETED"
jsonpath "$.direction" == "CREDIT"

# The reversal can be queried like any other leg
GET {{base}}/finsim/v1/payments/{{run}}-05a-rev?leg=CREDIT
HTTP 200
[Asserts]
jsonpath "$.status" == "COMPLETED"

# A second reversal of the same debit is refused
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-05c
Content-Type: application/json
{
  "transactionId": "{{run}}-05a-rev2",
  "direction": "CREDIT",
  "accountReference": "ACC-1",
  "money": { "amount": "5.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "reversalOf": "{{run}}-05a",
  "provenance": "SYNTHETIC"
}
HTTP 422
[Asserts]
jsonpath "$.code" == "DUPL"

# Reversing a debit this bank never saw
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-05d
Content-Type: application/json
{
  "transactionId": "{{run}}-05x-rev",
  "direction": "CREDIT",
  "accountReference": "ACC-1",
  "money": { "amount": "5.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "reversalOf": "{{run}}-no-such-debit",
  "provenance": "SYNTHETIC"
}
HTTP 422
[Asserts]
jsonpath "$.code" == "MS03"

# A reversal whose amount differs from the debit
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-05e
Content-Type: application/json
{
  "transactionId": "{{run}}-05e",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "3.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 201

POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-05f
Content-Type: application/json
{
  "transactionId": "{{run}}-05e-rev",
  "direction": "CREDIT",
  "accountReference": "ACC-1",
  "money": { "amount": "4.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "reversalOf": "{{run}}-05e",
  "provenance": "SYNTHETIC"
}
HTTP 422
[Asserts]
jsonpath "$.code" == "MS03"
