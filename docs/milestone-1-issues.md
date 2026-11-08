# Milestone 1 issue list: "Hello, transaction"

**Goal:** a stranger clones the repo and sees a traced payment between two independent services in under five minutes.

A starter scaffold for issues 1 and 2 (Gradle modules, CI, Modulith and ArchUnit checks, CLI skeleton) is included in the initial commit; first confirm it builds with `./gradlew spotlessApply build`, then close those issues.

Each item below can be pasted into GitHub as an issue. Labels in brackets. `gfi` = good first issue (suitable once the scaffold exists).

## Foundation

**1. Repo scaffold and build** `[milestone-1]`
Gradle multi-module build (`finsim-core`, `finsim-registry`, `finsim-sim`, `finsim-api`, `finsim-cli`), Java 21, formatter, CI workflow running build and tests.
*Done when:* `./gradlew build` passes on a clean checkout in CI.

**2. Module boundary rules** `[milestone-1]`
Add Spring Modulith and ArchUnit checks so modules can't touch each other's repositories.
*Done when:* a deliberate violation fails the build.

**3. Database baseline** `[milestone-1]`
PostgreSQL + Flyway; base migration with common columns (`id`, `created_at`, `updated_at`, `version`); Testcontainers for tests.
*Done when:* migrations run in tests and in compose.

**4. Publish the contract** `[milestone-1]`
Add `contract/finsim-v1.openapi.yaml`, validate it in CI, generate server stubs or models from it.
*Done when:* CI fails if the spec is invalid or drifts from the implementation.

## Registry

**5. Institution registration** `[milestone-1]`
Register an institution (id, type, base URL, capabilities); store in registry module.
*Done when:* register and list work via API and CLI.

**6. Static network and scheme** `[milestone-1]`
One seeded `sandbox` network with a hardcoded scheme (USD only, one workflow). Seed data labeled `SYNTHETIC`.
*Done when:* both sample banks are members of `sandbox` after startup.

**7. Capability check before routing** `[milestone-1]`
FinSim refuses to route a leg to an institution that hasn't declared the capability.
*Done when:* a transfer to an institution lacking `ACCOUNT_CREDIT` is rejected with a clear code.

## Core payments

**8. Transfer aggregate and state machine** `[milestone-1]`
States: CREATED, ACCEPTED, IN_PROGRESS, COMPLETED, REJECTED, UNKNOWN. Invalid transitions rejected. Append-only stage history.
*Done when:* unit tests cover every allowed and forbidden transition.

**9. Idempotency for the network API** `[milestone-1]`
`Idempotency-Key` required; same key and body returns the original response; different body returns 409.
*Done when:* tests cover missing key, replay, and conflict.

**10. Transfer workflow: debit then credit** `[milestone-1]`
Orchestrate DEBIT at source, CREDIT at destination, with stage-level idempotency keys derived from the transaction id.
*Done when:* happy-path transfer reaches COMPLETED across two institutions.

**11. Timeout handling and `UNKNOWN`** `[milestone-1]`
A timed-out outbound call moves the transfer to UNKNOWN (never REJECTED). Retrying the transfer while UNKNOWN returns 409 `RECONCILIATION_REQUIRED`.
*Done when:* scenario test shows the block.

**12. Resolve endpoint** `[milestone-1]`
`POST /transfers/{id}/resolve` queries each leg via `GET /finsim/v1/payments/{id}` and applies the decision table in [ADR 0001](adr/0001-resolving-unknown-transfers.md): re-send a lost credit with the same idempotency key, reverse the debit only after a definitive rejection, never guess. Requires the ADR to be accepted first.
*Done when:* every row of the ADR's decision table has a scenario test, and `resolve` is idempotent.

**13. Reason codes** `[milestone-1]` `[gfi]`
Small registry (AC01, AC04, AM02, AM04, DUPL, MS03, plus FinSim-specific codes). Every rejection must carry one; mark which side emitted it.
*Done when:* a test fails if any rejection path lacks a code.

**14. Property-based test: no duplicate effects** `[milestone-1]`
Random sequences of duplicate requests, timeouts, and retries never change balances more than once per transfer.
*Done when:* the property holds across thousands of generated cases in CI, including repeated `resolve` calls and reversal timeouts (see ADR 0001).

## Simulation

**15. Fault rules (DELAY, TIMEOUT, DUPLICATE_DELIVERY)** `[milestone-1]`
Implement `/api/v1/faults` and apply rules on outbound calls, with optional probability, count cap, and seed.
*Done when:* the same seed produces the same fault sequence.

**16. Trace recording and endpoint** `[milestone-1]`
Record stages, outbound calls (request, response, duration), injected faults, and state changes; expose `GET /transfers/{id}/trace`.
*Done when:* a faulted transfer's trace shows the fault and the resulting UNKNOWN.

## Samples and tooling

**17. Sample bank in Java** `[milestone-1]`
Minimal Spring Boot bank implementing the participant contract with in-memory accounts and seeded balances.
*Done when:* it passes `finsim verify`.

**18. Sample bank in Node** `[milestone-1]` `[help wanted]`
Same behavior in Node/TypeScript, in a separate directory, no shared code.
*Done when:* both banks pass the same verification and can transact with each other.

**19. CLI: `register`, `transfer`, `trace`, `fault`** `[milestone-1]`
Commands that wrap the network API with readable output (stage, emitter, reason code).
*Done when:* the quickstart in the README runs end to end.

**20. CLI: `finsim verify --url`** `[milestone-1]`
Check health, capabilities, a debit and credit, idempotent replay, idempotency conflict, status query, and error shape. Actionable messages: which check failed, why, how to fix.
*Done when:* both sample banks pass; a deliberately broken bank fails with clear messages.

## Release readiness (do after the above)

**21. Docker Compose quickstart** `[milestone-1]`
`docker compose up` brings up FinSim, Postgres, both banks, and the seeded network.
*Done when:* a person who has never seen the project completes the quickstart in under five minutes.

**22. Docs pass** `[docs]`
`docs/quickstart.md`, `docs/contract-v1.md`, `docs/roadmap.md` (the full TAS, marked planned), ADR template.
*Done when:* README status table matches reality.

**23. Name and launch checks** `[milestone-1]`
Name search, `SECURITY` contact, Code of Conduct file, issue and PR templates, 10 labeled `good first issue`s.
*Done when:* the maintainer checklist in GOVERNANCE.md is satisfied.

Items 1 to 20 are the core build. 21 to 23 gate the public announcement.
