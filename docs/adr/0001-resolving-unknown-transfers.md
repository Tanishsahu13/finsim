# ADR 0001: Resolving `UNKNOWN` transfers (lost credit leg)

- **Status:** Proposed
- **Date:** 2026-11-08
- **Deciders:** @mhnuk2007
- **Comment period ends:** 2026-11-15

## Context

A transfer moves money in two legs: `DEBIT` at the source institution, then `CREDIT` at the destination. Each leg is an HTTP call to an independent institution, and any call can time out.

A timeout is ambiguous. The institution may or may not have applied the leg. FinSim therefore marks the transfer `UNKNOWN` (never `REJECTED`) and blocks retries (`409 RECONCILIATION_REQUIRED`) until `POST /api/v1/transfers/{id}/resolve` has asked the institutions what happened.

The risky case is a transfer where the debit was applied and the credit leg's outcome is unknown. Depending on what the destination reports, FinSim must either finish the transfer or undo the debit, without ever creating or destroying money. The first draft of `docs/contract-v1.md` left this undecided, and issue 12 (the resolve endpoint) depends on it.

Constraints from the locked rules:

- FinSim never mutates an institution's data directly. Any correction is a call through the contract.
- Every financial mutation is idempotent.
- `UNKNOWN` is resolved before any retry. This applies to retries by the caller; recovery steps taken by `resolve` itself are part of reconciliation.
- Participants report only what they know. `UNKNOWN` is never a participant status.

## Decision

`resolve` uses **forward recovery first, backward recovery only on a definitive rejection.** It never guesses.

For each `resolve` call, FinSim queries the status of each leg with `GET /finsim/v1/payments/{transactionId}?leg=...` and applies this table:

| Debit leg at source | Credit leg at destination | Action | Result |
|---|---|---|---|
| not found (`404`) | not sent | Nothing was applied. | `REJECTED`, code `FINSIM_TIMEOUT`, emitted by `FINSIM` |
| `REJECTED` | not sent | Nothing was applied. | `REJECTED`, with the source's reason code, emitted by `SOURCE` |
| `COMPLETED` | `COMPLETED` | Both legs applied; the original response was lost. | `COMPLETED` |
| `COMPLETED` | not found (`404`) | The credit never arrived. Re-send the same `CREDIT` once with the **same** `Idempotency-Key`. | Re-evaluate from the response: `COMPLETED`, `REJECTED` (see next row), or still `UNKNOWN` |
| `COMPLETED` | `REJECTED` | Destination definitively refused. Reverse the debit (see below). | `REJECTED` with the destination's reason code, emitted by `DESTINATION`, after the reversal completes |
| `COMPLETED` | `ACCEPTED` (not yet `COMPLETED`) | Destination is still processing. | stays `UNKNOWN` |
| any leg query times out or errors | | Cannot decide. | stays `UNKNOWN`; `resolve` can be called again |

### The reversal

- A reversal is a **new `CREDIT` leg to the source institution** with its own transaction id `<transactionId>-rev`, its own idempotency key derived from the original (`<Idempotency-Key>:rev`), and `reversalOf: <transactionId>` in the request.
- The original debit is never edited or deleted. The reversal is a separate, traceable transaction. (When the ledger arrives in M2, this becomes an `Adjustment` of type `REVERSAL`.)
- Until the reversal reports `COMPLETED`, the transfer stays `UNKNOWN` with `pendingAction: REVERSE_DEBIT`. If the reversal call times out, `resolve` can be called again; the stable key makes re-sending safe.
- If the reversal is itself `REJECTED` by the source, the transfer stays `UNKNOWN` and is flagged `needsManualReview`. FinSim does not silently accept an inconsistent state. A manual-resolution path is out of scope for M1.

### Properties that always hold

1. **Conservation:** across source and destination, total balance never changes except by completed transfers. After `resolve` reaches a terminal state, either both legs are applied (`COMPLETED`) or the net effect is zero (`REJECTED`).
2. **`resolve` is idempotent:** calling it repeatedly, or concurrently, never applies a leg twice.
3. **No terminal state without evidence:** `COMPLETED` and `REJECTED` after a timeout are reached only from a participant's reported status, never from FinSim's assumption.
4. **Every step is traced:** each status query, re-send, and reversal appears as an event in the transfer's trace.

### Contract changes

- `PaymentLegRequest` gains an optional field `reversalOf` (transaction id being reversed). Institutions may use it for display or reconciliation; it does not change idempotency rules.
- `Transfer` gains `pendingAction` (`NONE`, `RETRY_CREDIT`, `REVERSE_DEBIT`) and `needsManualReview` (boolean), populated while the state is `UNKNOWN`.
- `FINSIM_TIMEOUT` is added as a FinSim-specific reason code (not an ISO 20022 code), documented as such.
- Participants **must** honor idempotency: the same key with the same body returns the original result. `finsim verify` checks this.

## Alternatives considered

| Option | Why not |
|---|---|
| Reverse the debit whenever the credit's outcome is unknown | Can undo money the destination actually applied, creating a duplicate loss. Reversal must wait for a definitive rejection. |
| Mark the transfer `REJECTED` on timeout and let humans reconcile | Violates the rule that a timeout never collapses into failure, and leaves the debit applied with no credit. |
| Keep retrying the credit forever in the background | Hides the failure, makes outcomes timing-dependent, and breaks deterministic replay. `resolve` stays an explicit, traced action. |
| Two-phase commit across institutions | Requires participants to implement prepare/commit, which raises the bar for joining a network and doesn't match how most real payment rails work. |
| Time-based automatic give-up | Needs the virtual clock (M2). Deferred; until then a transfer may remain `UNKNOWN` until someone calls `resolve`. |

## Consequences

- **Easier:** the behavior is deterministic and explainable from the trace. Every outcome follows from a participant's reported status.
- **Harder:** institutions must implement the status query and idempotency correctly. A transfer can remain `UNKNOWN` for a long time if an institution stays unreachable, and a failed reversal needs a manual path that is not built yet.
- **Migration:** update the OpenAPI spec and `docs/contract-v1.md` (done alongside this ADR); update issue 12 and the property-based test in issue 14.
- **Locked rules touched:** idempotency and `UNKNOWN`-before-retry. This ADR clarifies, but does not weaken, either rule.

## Verification

- Scenario tests for each row of the decision table, using fault rules to force timeouts at each leg.
- Property-based test: random sequences of timeouts, duplicate deliveries, and repeated `resolve` calls always satisfy conservation and idempotency.
- Test that a reversal is applied exactly once, even when its first attempt times out.
- `finsim verify` check: duplicate key returns the original result; unknown leg returns `404`.
