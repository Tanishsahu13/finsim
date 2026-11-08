# Malformed input gets the contract's error shape

# Not JSON
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-06a
Content-Type: application/json
```
{ this is not json
```
HTTP 400
[Asserts]
jsonpath "$.code" == "INVALID_REQUEST"

# provenance must be SYNTHETIC
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-06b
Content-Type: application/json
{
  "transactionId": "{{run}}-06b",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "1.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "REAL"
}
HTTP 400
[Asserts]
jsonpath "$.code" == "INVALID_REQUEST"

# Amount must be a decimal string
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-06c
Content-Type: application/json
{
  "transactionId": "{{run}}-06c",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "ten dollars", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 400
[Asserts]
jsonpath "$.code" == "INVALID_REQUEST"

# Unknown direction
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-06d
Content-Type: application/json
{
  "transactionId": "{{run}}-06d",
  "direction": "SIDEWAYS",
  "accountReference": "ACC-1",
  "money": { "amount": "1.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "provenance": "SYNTHETIC"
}
HTTP 400
[Asserts]
jsonpath "$.code" == "INVALID_REQUEST"

# reversalOf is only valid on a CREDIT
POST {{base}}/finsim/v1/payments
Idempotency-Key: {{run}}-key-06e
Content-Type: application/json
{
  "transactionId": "{{run}}-06e",
  "direction": "DEBIT",
  "accountReference": "ACC-1",
  "money": { "amount": "1.00", "currency": "USD" },
  "reference": "CONFORMANCE",
  "reversalOf": "{{run}}-02a",
  "provenance": "SYNTHETIC"
}
HTTP 400
[Asserts]
jsonpath "$.code" == "INVALID_REQUEST"

# Status query needs a valid leg parameter
GET {{base}}/finsim/v1/payments/{{run}}-06f
HTTP 400
[Asserts]
jsonpath "$.code" == "INVALID_REQUEST"

GET {{base}}/finsim/v1/payments/{{run}}-06f?leg=SIDEWAYS
HTTP 400
[Asserts]
jsonpath "$.code" == "INVALID_REQUEST"
