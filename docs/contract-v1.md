# The `finsim/v1` contract

> **Status: draft (milestone-1 sketch).** The machine-readable spec is [`contract/finsim-v1.openapi.yaml`](../contract/finsim-v1.openapi.yaml). If this page and the spec disagree, the spec wins. Changes to the contract require an ADR (see [CONTRIBUTING.md](../CONTRIBUTING.md)).

## The idea

An **institution** is any service you build (a bank, merchant, or gateway) in any language. To join a FinSim network it implements a small HTTP/JSON API, the **participant contract**. FinSim calls that API to move money legs, and exposes its own **network API** for developers and the CLI.

```
developer / CLI ──► FinSim network API ──► participant contract ──► your institution
                        (orchestrates, records, injects faults)
```

FinSim never reads or writes your database. It only knows what your institution tells it through the contract.

## Participant contract (you implement)

| Endpoint | Purpose |
|---|---|
| `GET /finsim/v1/institution/health` | Liveness and supported contract versions |
| `GET /finsim/v1/institution/capabilities` | What you support (`ACCOUNT_DEBIT`, `ACCOUNT_CREDIT`, `STATUS_QUERY`) and currencies |
| `POST /finsim/v1/payments` | Apply one leg, `DEBIT` or `CREDIT`, of a transfer |
| `GET /finsim/v1/payments/{transactionId}?leg=...` | Report the status of a leg (used to resolve `UNKNOWN`) |

## Network API (FinSim implements)

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/networks/{networkId}/transfers` | Start a transfer |
| `GET /api/v1/transfers/{transactionId}` | State and stage history |
| `GET /api/v1/transfers/{transactionId}/trace` | Every stage, call, and injected fault |
| `POST /api/v1/transfers/{transactionId}/resolve` | Resolve an `UNKNOWN` transfer |
| `POST/GET /api/v1/faults`, `DELETE /api/v1/faults/{faultId}` | Manage fault rules |

## Rules that always apply

1. **Idempotency.** Every mutating call carries an `Idempotency-Key` header. Same key and same body returns the original response (200). Same key and a different body returns `409 IDEMPOTENCY_CONFLICT`. A missing key returns `400`.
2. **`transactionId` is not a credential.** It identifies a transfer; it never authorizes anything.
3. **Money is a decimal string plus a currency** (`"100.00"`, `"USD"`), never a floating-point number.
4. **Participants report only what they know.** A leg status is `ACCEPTED`, `COMPLETED`, or `REJECTED`. A participant never reports `UNKNOWN`.
5. **`UNKNOWN` belongs to FinSim.** When a call to a participant times out, FinSim marks the transfer `UNKNOWN` (never `REJECTED`). It then blocks retries (`409 RECONCILIATION_REQUIRED`) until `resolve` has asked each institution what actually happened.
6. **Every rejection carries a reason code** and says which side emitted it (`FINSIM`, `SOURCE`, or `DESTINATION`).
7. **Everything is `SYNTHETIC`.** Requests and responses carry `"provenance": "SYNTHETIC"`.

## Transfer states

```
CREATED → ACCEPTED → IN_PROGRESS → COMPLETED   (terminal)
                                 → REJECTED    (terminal, reason code required)
                                 → UNKNOWN     (not terminal; resolves to COMPLETED or REJECTED)
```

## Example: a transfer where the destination times out

1. `POST /api/v1/networks/sandbox/transfers` with `Idempotency-Key: t-001`.
2. FinSim sends `DEBIT` to `bank-a`; it answers `COMPLETED`.
3. FinSim sends `CREDIT` to `bank-b`; the call times out (a fault rule, or a real outage).
4. The transfer becomes `UNKNOWN`. Retrying returns `409 RECONCILIATION_REQUIRED`.
5. `POST /api/v1/transfers/{id}/resolve` asks `bank-b` for the leg's status. If it applied the credit, the transfer becomes `COMPLETED`; if it never arrived, FinSim reverses the debit through the contract (or marks the transfer `REJECTED`, depending on the scheme rules) and records both steps in the trace.

> The exact reversal behavior in step 5 is an open design point for milestone 1. Whichever way it is decided, it must be written up as an ADR.

## Versioning

`finsim/v1` is versioned independently from the platform. Until `1.0`, breaking changes are allowed but need an ADR and a changelog entry. After `1.0`, a deprecation policy with an overlap period for the previous version applies.
