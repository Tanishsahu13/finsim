# FinSim Roadmap

> **Everything on this page marked "planned" does not exist yet.** This roadmap is the public form of the original Technical Architecture Specification (TAS 1.0), reorganized into milestones and with known design problems corrected (see section 5). If this page and the README disagree about what works today, the README wins.

## 1. How to read this

- **Committed** milestones (M1 to M3) are what the maintainers are building now, in order.
- **Horizon** items are planned but unscheduled. They are open for contributors; see `help wanted` and `roadmap` labels.
- **Backlog** items are ideas that need a design before anyone starts.
- Fidelity tags describe how closely a domain imitates reality:

| Tag | Meaning |
|---|---|
| Faithful | Implements the specified behavior and lifecycle within a documented model |
| Structural | Represents entities and state transitions; simplifies mechanics |
| Abstracted | Simplified interface only |
| Simulated | Synthetic data, configurable rules, or simulated external actors |
| Not modeled | Explicitly out of scope |

Changing a domain's fidelity tag requires a pull request that updates this page.

## 2. Committed milestones

### M1: Hello, transaction
Two independent sample banks (Java, Node) transact through the `finsim/v1` contract.
Transfer state machine with `UNKNOWN`, idempotency, reason codes, fault injection (delay, timeout, duplicate), trace, `finsim verify`, Docker Compose quickstart.
Issue list: [milestone-1-issues.md](milestone-1-issues.md).

### M2: Cards, time, and money movement
- **Card authorization model** (section 6): authorize, capture, partial capture, void, expiry, refund.
- **Virtual clock** for business time (authorization expiry, due dates) — [#27](https://github.com/mhnuk2007/finsim/issues/27).
- **Simple ledger** with balanced double-entry journals and invariant tests — [#28](https://github.com/mhnuk2007/finsim/issues/28).
- **Simulation state snapshot and reset engine** for CI and deterministic replay — [#32](https://github.com/mhnuk2007/finsim/issues/32).
- Sample merchant, gateway, and ATM institutions (cash dispense fault simulation).
- Fault types extended: failed capture, delayed webhook, hardware dispense failure.

### M3: Disputes, reconciliation, replay
- **Disputes and chargebacks** as a sibling aggregate that never mutates the original payment. Scheme-specific reason codes, deadlines on the virtual clock, representment, arbitration. Scheme rules are synthetic.
- **Reconciliation:** statement upload, matching against FinSim's records only, break classification.
- **Scenario recording and replay:** a recorded scenario reproduces the same financial effects, including injected faults.
- **Demo target:** checkout, settlement, chargeback, reconciliation, all in one trace.

## 3. Horizon (planned, unscheduled)

| Area | Content | Fidelity target |
|---|---|---|
| Clearing and settlement | Bilateral and multilateral netting, settlement instructions, finality events, RTGS queues, liquidity positions and reservations, gridlock resolution, credit exposure ledgers | Faithful |
| Interoperability | ISO 20022 adapter with pinned schemas (pacs.008, pacs.002, camt.056, camt.029, pacs.004, camt.053, acmt.023, acmt.024), MT940, webhooks with signing and replay protection, SDKs | Faithful (base schemas) |
| Compliance and FX | Screening as a configurable policy gate with synthetic lists and maker-checker; FX conversion with two legs and Herstatt-risk recording; intraday credit | Faithful lifecycle, Simulated data |
| Platform hardening | Multi-tenancy, RBAC, row-level security, hosted institution containers with isolation and scanning, message-level signing (JWS) | Faithful |
| Regulatory | Simulated regulatory reports with ACK/NACK lifecycle | Structural, Simulated |

## 4. Backlog (needs design first)

These appeared in early drafts of the README but have no architecture or requirements yet. Each needs a short design proposal (ADR) before work starts.

- **Commerce:** payment intents, subscriptions and dunning, split payments and marketplace payouts.
- **Bill payments:** billers, presentment, validation, partial payments, aggregation.
- **Fees:** declarative fee schedules, computation at trigger points, fee ledgers, disclosure, reversal on refund, tax on fees.
- **Scenario DSL and CI packaging:** a declarative, versionable scenario format and a GitHub Action.
- **Snapshot, restore, and branch** of simulation environments.
- **Compatibility modes:** Stripe- or Plaid-shaped endpoints (needs trademark and terms review).
- **Rails:** ACH/Nacha, SEPA, and card-network message formats.
- Auto-collateralization, cross-currency netting, PvP and DvP settlement, general KYC, real data feeds. These are deliberately deferred and may never be built.

## 5. Corrections to TAS 1.0 (decisions folded into this roadmap)

The original specification contained the following problems. These are the decisions going forward; each will get an ADR when the relevant code is written.

1. **Gridlock cycle feasibility.** A cycle may only be executed atomically if every participant stays non-negative: for each participant *i* in the cycle, `L[i] + incoming(i) − outgoing(i) ≥ 0`. The earlier aggregate test (total deficit at most total incoming) is insufficient.
2. **Intraday credit and the liquidity invariant.** The invariant `available + reserved = total` is unchanged. Credit headroom is tracked in its own ledger, and the amount a participant may actually spend is *effective available = available + unused credit headroom*. Credit never changes `total`.
3. **`FAILED` is not a transfer state.** Transfer states are CREATED, ACCEPTED, IN_PROGRESS, COMPLETED, REJECTED, UNKNOWN. A definitive failure is REJECTED with a reason code. UNKNOWN resolves only to COMPLETED or REJECTED. (`FAILED` remains valid for settlement instructions and clearing cycles.)
4. **Cancellation window.** Cancellation is allowed only before settlement begins (CREATED, QUEUED). Once an instruction is SETTLING or later, undoing it is a new `Adjustment`, never a cancellation.
5. **Two clocks.** Security checks (signature timestamp window, nonce TTL, key validity) use the **wall clock**. Business logic (deadlines, expiry, cut-offs, due dates) uses the **virtual clock**. Replay records both, so the same scenario yields the same outcome.
6. **Data ownership.** `settlement_instructions` belongs to the settlement module only; clearing references it by id. Append-only tables (finality events, audit records) do not carry `updated_at` or an optimistic-lock `version`, because they are never updated.
7. **Reason codes.** The reason-code list must be verified against the current ISO 20022 external code sets before it is frozen. Known items to check: the code for a missed cut-off (believed to be `TM01`, not `FF05`), and `AM04` being used for both insufficient funds and credit limit exceeded. Where no clean ISO code exists, FinSim defines its own code and marks it as FinSim-specific.
8. **Simulation timing.** Determinism is a design rule from day one (seeded randomness, no hidden wall-clock dependence, recorded external calls). The *replay engine* ships in M3. Earlier phase tables that placed simulation in different phases are superseded by this page.
9. **Phase table.** This roadmap replaces the phase tables in the original README, requirements, and TAS. Stub-first still applies: where a later domain needs an early hook (for example ISO 20022), a stub ships first.

## 6. Card authorization model (planned for M2)

Card payments need an authorization step that the original TAS lacked.

**`Authorization` aggregate** (sibling to the transfer aggregate):

```
AUTHORIZED → PARTIALLY_CAPTURED → CAPTURED
          ↘ VOIDED
          ↘ EXPIRED   (virtual clock reaches expiry with uncaptured amount)
```

- **Authorize:** places a hold. Amount held is tracked separately from available balance.
- **Capture:** creates a normal transfer for the captured amount. Capture amount may be less than authorized (partial capture). Over-capture is configurable and off by default.
- **Void:** releases the remaining hold before capture.
- **Expiry:** an uncaptured hold lapses on the virtual clock.
- **Refund:** a new transfer referencing the captured one, with its own idempotency key and lifecycle.
- **Exposure timing for cards:** created at authorization, released at capture and finality.
- Disputes (M3) attach to the captured transfer and never modify it.

## 7. Locked rules

These cannot change without an ADR. They protect the properties that make FinSim trustworthy.

| Rule | How it is enforced |
|---|---|
| FinSim never mutates an external institution's data | Adapter-only communication; no shared database |
| Institutions declare capabilities; the platform routes only to what they declare | Capability check at every routed call |
| Every financial mutation is idempotent | Idempotency keys, tested by property-based tests |
| `UNKNOWN` outcomes are resolved before any retry | Orchestrator blocks retry while UNKNOWN |
| Every posted journal balances | Database constraint and service check |
| Finality is monotonic and append-only | No update or delete; reversal is a new Adjustment |
| Conservation invariants hold (netting sums to zero, `available + reserved = total`) | Runtime check aborts on violation |
| A dispute never mutates the original payment | Invariant test |
| Everything simulated is labeled `SYNTHETIC` | Schema-level check |
| Decision algorithms are deterministic under a scenario seed | Replay tests |
| Fidelity changes are explicit | PR updates this page |
| No uploaded code runs inside the FinSim API process | Hosted code runs only in isolated containers |

## 8. Honest boundaries

FinSim is a synthetic testing environment. It will not hold real funds, connect to real payment networks, sanctions lists, FX feeds, or regulators, or claim conformance to any real scheme's rules. Passing FinSim tests does not replace certification with a real network.
