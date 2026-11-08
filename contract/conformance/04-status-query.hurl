# Status queries: applied, rejected, and never-received legs

# Apply a debit
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-04a
Content-Type: application/json
{
  "transactionId": "{{run}}-04a",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "1.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 201

# The applied leg is reported as COMPLETED
GET {{base}}/finsim/v1/payments/{{run}}-04a?leg=DEBIT
HTTP 200
[Asserts]
jsonpath "$.transactionId" == "{{run}}-04a"
jsonpath "$.direction" == "DEBIT"
jsonpath "$.status" == "COMPLETED"
jsonpath "$.provenance" == "SYNTHETIC"

# The other direction was never received
GET {{base}}/finsim/v1/payments/{{run}}-04a?leg=CREDIT
HTTP 404
[Asserts]
jsonpath "$.code" == "LEG_NOT_FOUND"

# A transaction the bank never saw
GET {{base}}/finsim/v1/payments/{{run}}-never-sent?leg=CREDIT
HTTP 404
[Asserts]
jsonpath "$.code" == "LEG_NOT_FOUND"

# A rejected leg is remembered and reported as REJECTED with its reason code
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-04b
Content-Type: application/json
{
  "transactionId": "{{run}}-04b",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "999999999.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 422

GET {{base}}/finsim/v1/payments/{{run}}-04b?leg=DEBIT
HTTP 200
[Asserts]
jsonpath "$.status" == "REJECTED"
jsonpath "$.reasonCode" == "AM04"
