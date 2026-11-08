# finsim/v1 participant conformance suite

Language-neutral HTTP checks that any institution (bank) must pass. They are written for [Hurl](https://hurl.dev), so they work against a bank written in any language. The Java sample bank in `samples/bank-java` is the reference implementation, and CI runs this suite against it.

This suite is the seed of `finsim verify` (milestone 1). Until that command exists, it is how a contributor knows their bank is correct.

## Run it

```bash
# 1. Install Hurl (https://hurl.dev/docs/installation.html), for example:
#    brew install hurl        or        cargo install hurl        or        scoop install hurl
# 2. Start your bank, then:
scripts/conformance.sh http://localhost:9001
```

The script passes a fresh `run` id to every file, so you can re-run it against a bank that is still running. Run a single file with:

```bash
hurl --test --variable base=http://localhost:9001 --variable run=$(date +%s) contract/conformance/03-rejections.hurl
```

## What a bank must provide

**Seed data.** Every bank must start with these synthetic accounts, all in `USD`:

| Account | State |
|---|---|
| `ACC-1` | open, balance at least `100.00` |
| `ACC-2` | open |
| `ACC-CLOSED` | closed |

The suite moves only small amounts and never needs a specific balance, except that `ACC-1` must be able to cover about `20.00` in total and must not be able to cover `999999999.00`.

**Health.** `GET /finsim/v1/institution/health` returns `200` with `{"status": "UP", "contractVersions": ["finsim/v1"]}`.

**Capabilities.** `GET /finsim/v1/institution/capabilities` returns `200` with `institutionType`, `capabilities` (must include `ACCOUNT_DEBIT`, `ACCOUNT_CREDIT`, `STATUS_QUERY`), and `currencies` (must include `USD`).

## Error codes

Every error body is `{"code": "...", "message": "..."}`.

| HTTP | `code` | When |
|---|---|---|
| 400 | `MISSING_IDEMPOTENCY_KEY` | `Idempotency-Key` header missing, or not 8 to 128 characters |
| 400 | `INVALID_REQUEST` | Malformed JSON; missing or invalid fields; `provenance` is not `SYNTHETIC`; `reversalOf` on a `DEBIT`; missing or invalid `leg` query parameter |
| 404 | `LEG_NOT_FOUND` | Status query for a leg this bank never received |
| 409 | `IDEMPOTENCY_CONFLICT` | Same `Idempotency-Key` with a different request body |
| 422 | `AC01` | Unknown account |
| 422 | `AC04` | Closed account |
| 422 | `AM02` | Amount is zero or negative |
| 422 | `AM03` | Unsupported currency |
| 422 | `AM04` | Insufficient funds (debit) |
| 422 | `DUPL` | A reversal for that debit already exists |
| 422 | `MS03` | `reversalOf` does not match a completed debit at this bank, same account, same amount |

## Behavior rules

1. **Idempotency.** First request with a key: `201`. Same key and identical body: `200` with the original body (same `participantReference`). Same key and different body: `409 IDEMPOTENCY_CONFLICT`. Replaying a rejected request returns the same `422` again.
2. **Rejections are remembered.** A `422` leg can later be queried and is reported as `REJECTED` with its `reasonCode`.
3. **Status query.** `GET /finsim/v1/payments/{transactionId}?leg=DEBIT|CREDIT` returns `200` with the leg (`COMPLETED` or `REJECTED`), or `404 LEG_NOT_FOUND` if the bank never received it. FinSim relies on this to resolve `UNKNOWN` transfers (ADR 0001).
4. **Participants never report `UNKNOWN`.** Leg status is `COMPLETED` or `REJECTED` (`ACCEPTED` is allowed only for banks that process asynchronously).
5. **Reversal.** A reversal is a normal `CREDIT` leg with its own `transactionId` and `reversalOf` set to the debit's `transactionId`. It must match a completed debit (same account and amount). A debit can be reversed only once.
6. **Synthetic only.** Requests without `"provenance": "SYNTHETIC"` are rejected with `400 INVALID_REQUEST`.
7. **Money is a decimal string** such as `"100.00"`, with up to 8 decimal places. Never a floating-point number.

## Files

| File | Covers |
|---|---|
| `01-health-capabilities.hurl` | Health and capabilities |
| `02-idempotency.hurl` | Create, replay, conflict, missing key |
| `03-rejections.hurl` | `AC01`, `AC04`, `AM02`, `AM03`, `AM04` |
| `04-status-query.hurl` | Status of applied, rejected, and never-received legs |
| `05-reversal.hurl` | Reversal, double reversal, mismatched reversal |
| `06-validation.hurl` | Malformed bodies, provenance, bad query parameters |

## Adding checks

Add a new numbered `.hurl` file, use `{{run}}` in every `transactionId` and `Idempotency-Key` so reruns don't collide, and document the rule above. Changes to the rules need an ADR (see `CONTRIBUTING.md`).
