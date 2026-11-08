# Sample bank (Java)

A tiny in-memory bank that implements the `finsim/v1` **participant contract**. It exists to show institution authors what a correct participant looks like, and to give FinSim something real to call during development.

It depends on nothing from FinSim. Everything it needs is the HTTP contract in [`contract/finsim-v1.openapi.yaml`](../../contract/finsim-v1.openapi.yaml). All data is synthetic and lives in memory, so it resets on restart.

## Run it

```bash
./gradlew :sample-bank-java:bootRun                                       # bank-a on :9001
./gradlew :sample-bank-java:bootRun --args="--spring.profiles.active=bank-b"   # bank-b on :9002
```

Seeded accounts (see `src/main/resources/application*.yml`):

| Institution | Port | Accounts |
|---|---|---|
| `bank-a` | 9001 | `ACC-1` 1000.00, `ACC-2` 250.00, `ACC-CLOSED` (closed) |
| `bank-b` | 9002 | `ACC-1` 500.00, `ACC-2` 100.00, `ACC-CLOSED` (closed) |

## Try it

```bash
curl http://localhost:9001/finsim/v1/institution/health
curl http://localhost:9001/finsim/v1/institution/capabilities

# Debit 100.00 from ACC-1 (201 Created)
curl -i -X POST http://localhost:9001/finsim/v1/payments \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-key-0001' \
  -d '{"transactionId":"tx-1","direction":"DEBIT","accountReference":"ACC-1","money":{"amount":"100.00","currency":"USD"},"reference":"DEMO-001","provenance":"SYNTHETIC"}'

# Send the exact same request again: 200 OK, same body, money moved only once
# Change the amount but keep the key: 409 IDEMPOTENCY_CONFLICT

curl "http://localhost:9001/finsim/v1/payments/tx-1?leg=DEBIT"   # status of the leg
curl http://localhost:9001/sample/accounts                        # demo helper: balances
```

## What it demonstrates

| Contract rule | Behavior here |
|---|---|
| Idempotency | Same key and body: original result with `200`. Same key, different body: `409 IDEMPOTENCY_CONFLICT`. Missing key: `400`. |
| Business rejection | `422` with a reason code, remembered so the leg can be queried as `REJECTED`. |
| Status query | `GET /finsim/v1/payments/{transactionId}?leg=...` returns the leg, or `404` if the bank never received it. FinSim uses this to resolve `UNKNOWN` transfers (ADR 0001). |
| Reversal | A normal `CREDIT` with its own `transactionId` and `reversalOf` set. It must match a completed debit, and a debit can be reversed only once. |
| Participants report only what they know | Statuses are `COMPLETED` or `REJECTED`; the bank never reports `UNKNOWN`. |
| Synthetic only | Requests without `"provenance": "SYNTHETIC"` are rejected. |

Reason codes used: `AC01` unknown account, `AC04` closed account, `AM02` invalid amount, `AM03` unsupported currency, `AM04` insufficient funds, `DUPL` duplicate, `MS03` not specified.

`GET /sample/accounts` is a demo helper and is **not** part of the contract. Real institutions don't need it.

## Tests

```bash
./gradlew :sample-bank-java:test
```

`BankServiceTest` covers the ledger rules directly. `ContractHttpTest` calls the running bank over HTTP the way FinSim will; those checks are the starting point for `finsim verify` (issue 20).

## Limits (on purpose)

In-memory state, one currency by default, no authentication, no persistence. It's a teaching sample, not a template for a production bank.
