# Milestone 1 issue list: "Hello, transaction"

**Goal:** a stranger clones the repo and sees a traced payment between two independent services in under five minutes.

Each item below corresponds to an issue in the Milestone 1 roadmap. Status tags: `[done]`, `[in-progress]`, `[partially-done]`, or `[milestone-1]` (backlog). `gfi` = good first issue.

---

## Foundation

**1. Repo scaffold and build** `[milestone-1]` `[done]`
Gradle multi-module build (`finsim-core`, `finsim-registry`, `finsim-sim`, `finsim-api`, `finsim-cli`, `sample-bank-java`), Java 21, Spotless formatting, and GitHub Actions CI workflow running build, check, and tests.
*Status:* Completed. Clean build and CI pipeline passing.

**2. Module boundary rules** `[milestone-1]` `[done]`
Add Spring Modulith and ArchUnit checks so modules cannot touch each other's internal repositories or leak architecture boundaries.
*Status:* Completed. `ArchitectureRulesTests` and `ModularityTests` actively enforce module isolation.

**3. Database baseline** `[milestone-1]` `[done]`
PostgreSQL 17 + Flyway; base migration with common columns (`id`, `created_at`, `updated_at`, `version`); Testcontainers for automated tests, Docker Compose for local development. Strict PostgreSQL parity (no H2 fallback).
*Status:* Completed. Flyway `V1__baseline.sql` and `V2__registry.sql` verified with Testcontainers and Compose.

**4. Publish the contract** `[milestone-1]` `[in-progress]`
Add `contract/finsim-v1.openapi.yaml`, validate it in CI, generate server stubs or models from it.
*Status:* OpenAPI 3.1 specification written and verified against participant implementations.

---

## Registry

**5. Institution registration** `[milestone-1]` `[done]`
Register an institution (slug id, name, type, base URL, capabilities); persist in `finsim-registry` module with SSRF base-url validation.
*Status:* Completed. Entity model, JPA repository, Flyway `V2__registry.sql`, REST API (`/api/v1/institutions`), and CLI commands tested.

**6. Static network and scheme** `[milestone-1]` `[done]`
One seeded `sandbox` network with a hardcoded scheme (USD only, `wf.transfer.v1` workflow). Seed data labeled `SYNTHETIC`.
*Status:* Completed. FinSim auto-seeds `sandbox` network and joins `bank-a` and `bank-b` upon startup idempotently.

**7. Capability check before routing** `[milestone-1]` `[done]`
FinSim refuses to route a leg to an institution that hasn't declared the required capability (`ACCOUNT_DEBIT`, `ACCOUNT_CREDIT`, `STATUS_QUERY`).
*Status:* Completed. `Registry.requireRoutable(...)` integrated into `TransferOrchestrator` and verified in `TransferWorkflowTests`.

---

## Core payments

**8. Transfer aggregate and state machine** `[milestone-1]` `[done]`
Transfer aggregate root with 10 strictly permitted transitions across `CREATED`, `ACCEPTED`, `IN_PROGRESS`, `COMPLETED`, `REJECTED`, and `UNKNOWN`. Append-only stage history (`transfer_stages`), explicit `needsManualReview` support, time determinism (all mutations accept `Instant`/`Clock`), and strict separation between domain rejection `ReasonCode` and HTTP `ApiErrorCode`.
*Status:* Completed and covered by comprehensive unit tests in `finsim-core`.

**9. Idempotency for the network API** `[milestone-1]` `[done]`
`Idempotency-Key` required on network mutating requests; identical key and body returns original response; differing body returns `409 IDEMPOTENCY_CONFLICT`.
*Status:* Completed. Handled in `TransferService` and covered by integration tests in `TransferWorkflowTests`.

**10. Transfer workflow: debit then credit** `[milestone-1]` `[done]`
Orchestrate `DEBIT` at source institution, then `CREDIT` at destination institution over HTTP, using stage-level idempotency keys derived deterministically from the transaction id.
*Status:* Completed. Implemented in `TransferOrchestrator` and `ParticipantClient`, verified across 16 scenarios in `TransferWorkflowTests`.

**11. Timeout handling and `UNKNOWN`** `[milestone-1]` `[done]`
A timed-out outbound call moves the transfer to `UNKNOWN` (never `REJECTED`). Retrying the transfer while `UNKNOWN` returns `409 RECONCILIATION_REQUIRED`.
*Status:* Completed. Verified state transition to `UNKNOWN`, pendingAction population, and retry blocking with HTTP 409.

**12. Resolve endpoint** `[milestone-1]` `[done]`
`POST /api/v1/transfers/{id}/resolve` queries each leg via `GET /finsim/v1/payments/{id}` and applies the decision table in [ADR 0001](adr/0001-resolving-unknown-transfers.md): re-send a lost credit with the same idempotency key, reverse the debit only after a definitive rejection, never guess.
*Status:* Completed. Implemented in `TransferOrchestrator.resolve` and `TransferController`, covering all decision table rows with dedicated scenario tests.

**13. Reason codes** `[milestone-1]` `[done]` `[gfi]`
Standardized reason code registry (ISO 20022 codes like `AC01`, `AC04`, `AM02`, `AM04`, `DUPL`, `MS03`, plus FinSim platform codes). Every rejection must carry a reason code and emitter (`SOURCE`, `DESTINATION`, `FINSIM`).
*Status:* Completed. Enums, ISO code validation, and end-to-end propagation from participant responses verified.

**14. Property-based test: no duplicate effects** `[milestone-1]`
Random sequences of duplicate requests, timeouts, and retries never change balances more than once per transfer.
*Done when:* the property holds across thousands of generated cases in CI, including repeated `resolve` calls and reversal timeouts (see ADR 0001).

---

## Simulation

**15. Fault rules (DELAY, TIMEOUT, DUPLICATE_DELIVERY)** `[milestone-1]`
Implement `/api/v1/faults` and apply rules on outbound calls, with optional probability, count cap, and seed.
*Done when:* the same seed produces the same fault sequence.

**16. Trace recording and endpoint** `[milestone-1]`
Record stages, outbound calls (request, response, duration), injected faults, and state changes; expose `GET /api/v1/transfers/{id}/trace`.
*Done when:* a faulted transfer's trace shows the fault and the resulting `UNKNOWN`.

---

## Samples and tooling

**17. Sample bank in Java** `[milestone-1]` `[done]`
Minimal Spring Boot bank in `samples/bank-java` implementing the participant contract with in-memory accounts, idempotency store, and seeded balances (`bank-a` on port 9001, `bank-b` on port 9002).
*Status:* Completed. Passed both unit ledger tests (`BankServiceTest`) and HTTP contract compliance tests (`ContractHttpTest`).

**18. Sample bank in Node** `[milestone-1]` `[help wanted]`
Same behavior in Node/TypeScript, in a separate directory, no shared code.
*Done when:* both banks pass the same verification and can transact with each other.

**19. CLI: `register`, `transfer`, `trace`, `fault`** `[milestone-1]` `[partially-done]`
Commands that wrap the network API with human-readable output.
*Status:* Registry commands (`register`, `institutions`, `networks`, `join`) completed with client and JSON handling. Transfer, trace, and fault subcommands pending payment workflow API.

**20. CLI: `finsim verify --url`** `[milestone-1]`
Check health, capabilities, debit and credit, idempotent replay, idempotency conflict, status query, and error shape. Actionable messages: which check failed, why, how to fix.
*Done when:* both sample banks pass; a deliberately broken bank fails with clear messages.

---

## Release readiness

**21. Docker Compose quickstart** `[milestone-1]` `[partially-done]`
`docker compose up` brings up FinSim, Postgres, both banks, and the seeded network.
*Status:* PostgreSQL 17 compose definition active in `deploy/docker-compose.yml`. Containerization of FinSim API and sample banks pending final assembly.

**22. Docs pass** `[milestone-1]` `[docs]` `[in-progress]`
Keep `docs/quickstart.md`, `docs/database.md`, `docs/registry.md`, `docs/contract-v1.md`, and `README.md` synchronized with the actual codebase capabilities.

**23. Name and launch checks** `[milestone-1]` `[partially-done]`
Name search, `SECURITY` contact, Code of Conduct file, issue and PR templates, 10 labeled good first issues.
*Status:* 10 curated good first issues published on GitHub (#11 through #20) and documented in `docs/good-first-issues.md`. Issue and PR templates active. Maintainer governance checklist in progress.
