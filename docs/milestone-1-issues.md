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
*Status:* Completed. Flyway `V1__baseline.sql`, `V2__registry.sql`, and `V3__widen_transfer_amount_scale.sql` (NUMERIC(22, 8) precision, closed [#46](https://github.com/mhnuk2007/finsim/issues/46) in [#66](https://github.com/mhnuk2007/finsim/pull/66)) verified with Testcontainers and Compose.

**4. Publish the contract** `[milestone-1]` `[done]`
Add `contract/finsim-v1.openapi.yaml`, validate it in CI, generate server stubs or models from it.
*Status:* Completed. OpenAPI 3.1 specification written and verified with Spectral in CI (closed [#14](https://github.com/mhnuk2007/finsim/issues/14) in [#42](https://github.com/mhnuk2007/finsim/pull/42)).

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

**8. Dynamic network directory discovery endpoint** `[milestone-1]` `[help wanted]`
Expose `GET /api/v1/networks/{networkId}/directory` returning registered, active participants and declared capabilities. Decouples sample participants, CLI, and Web Cockpit (#29) from hardcoded URLs.
*Issue:* [#31](https://github.com/mhnuk2007/finsim/issues/31)

---

## Core payments

**9. Transfer aggregate and state machine** `[milestone-1]` `[done]`
Transfer aggregate root with 10 strictly permitted transitions across `CREATED`, `ACCEPTED`, `IN_PROGRESS`, `COMPLETED`, `REJECTED`, and `UNKNOWN`. Append-only stage history (`transfer_stages`), explicit `needsManualReview` support, time determinism (all mutations accept `Instant`/`Clock`), and strict separation between domain rejection `ReasonCode` and HTTP `ApiErrorCode`.
*Status:* Completed and covered by comprehensive unit tests in `finsim-core`.

**10. Idempotency for the network API** `[milestone-1]` `[done]`
`Idempotency-Key` required on network mutating requests; identical key and body returns original response; differing body returns `409 IDEMPOTENCY_CONFLICT`.
*Status:* Completed. Handled in `TransferService` and covered by integration tests in `TransferWorkflowTests`.

**11. Transfer workflow: debit then credit** `[milestone-1]` `[done]`
Orchestrate `DEBIT` at source institution, then `CREDIT` at destination institution over HTTP, using stage-level idempotency keys derived deterministically from the transaction id.
*Status:* Completed. Implemented in `TransferOrchestrator` and `ParticipantClient`, verified across comprehensive scenarios in `TransferWorkflowTests` (including automatic compensating source debit reversal on destination credit rejection, closed [#45](https://github.com/mhnuk2007/finsim/issues/45) in [#50](https://github.com/mhnuk2007/finsim/pull/50)). Hardened against participant 4xx suppression (closed [#52](https://github.com/mhnuk2007/finsim/issues/52)), blank field validation crashes (closed [#80](https://github.com/mhnuk2007/finsim/issues/80)), invalid currency format and scale > 8 (closed [#81](https://github.com/mhnuk2007/finsim/issues/81)), non-existent network 404s (closed [#67](https://github.com/mhnuk2007/finsim/issues/67)), capability mismatch reason codes (closed [#68](https://github.com/mhnuk2007/finsim/issues/68)), and malformed JSON handling (closed [#64](https://github.com/mhnuk2007/finsim/issues/64)) in [#96](https://github.com/mhnuk2007/finsim/pull/96).

**12. Timeout handling and `UNKNOWN`** `[milestone-1]` `[done]`
A timed-out outbound call moves the transfer to `UNKNOWN` (never `REJECTED`). Retrying the transfer while `UNKNOWN` returns `409 RECONCILIATION_REQUIRED`.
*Status:* Completed. Verified state transition to `UNKNOWN`, pendingAction population (aligned with ADR 0001 in [#91](https://github.com/mhnuk2007/finsim/pull/91) to set `NONE` on initial debit timeout, closing [#82](https://github.com/mhnuk2007/finsim/issues/82)), participant 400/409 classification (closed [#83](https://github.com/mhnuk2007/finsim/issues/83)), and retry blocking with HTTP 409.

**13. Resolve endpoint** `[milestone-1]` `[done]`
`POST /api/v1/transfers/{id}/resolve` queries each leg via `GET /finsim/v1/payments/{id}` and applies the decision table in [ADR 0001](adr/0001-resolving-unknown-transfers.md): re-send a lost credit with the same idempotency key, reverse the debit only after a definitive rejection, never guess.
*Status:* Completed. Implemented in `TransferOrchestrator.resolve` and `TransferController`, covering all decision table rows with dedicated scenario tests. Hardened against concurrent resolve race conditions and optimistic lock failure in [#91](https://github.com/mhnuk2007/finsim/pull/91) (closing [#84](https://github.com/mhnuk2007/finsim/issues/84)).

**14. Reason codes** `[milestone-1]` `[done]` `[gfi]`
Standardized reason code registry (ISO 20022 codes like `AC01`, `AC04`, `AM02`, `AM04`, `DUPL`, `MS03`, plus FinSim platform codes). Every rejection must carry a reason code and emitter (`SOURCE`, `DESTINATION`, `FINSIM`).
*Status:* Completed. Enums, ISO code validation, and end-to-end propagation from participant responses verified. Domain rejection reason codes explicitly separated from API error codes in OpenAPI contract and documentation (closed [#59](https://github.com/mhnuk2007/finsim/issues/59) in [#72](https://github.com/mhnuk2007/finsim/pull/72)).

**15. Property-based test: no duplicate effects** `[milestone-1]` `[done]`
Random sequences of duplicate requests, timeouts, and retries never change balances more than once per transfer.
*Status:* Completed in [#94](https://github.com/mhnuk2007/finsim/pull/94) (closed [#24](https://github.com/mhnuk2007/finsim/issues/24)). Implemented `TransferConservationPropertyTest` with `net.jqwik:jqwik` verifying conservation of value ($\text{Balance}_A + \text{Balance}_B + \text{InTransit Funds} \equiv \text{Initial}_A + \text{Initial}_B$), terminal state purity (`COMPLETED`, `REJECTED`, `UNKNOWN`), and idempotent replay invariance across thousands of randomized chaos sequences.

---

## Simulation

**16. Fault rules (DELAY, TIMEOUT, DUPLICATE_DELIVERY)** `[milestone-1]` `[done]`
Implement `/api/v1/faults` and apply rules on outbound calls, with optional probability, count cap, and seed.
*Status:* Completed in [#89](https://github.com/mhnuk2007/finsim/pull/89) (closed [#22](https://github.com/mhnuk2007/finsim/issues/22)). Deterministic rule engine in `finsim-sim`, REST endpoints in `finsim-api`, Flyway migration `V4__add_fault_rule_seed.sql`, and participant outbound HTTP interception in `TransferOrchestrator`.

**17. Trace recording and endpoint** `[milestone-1]`
Record stages, outbound calls (request, response, duration), injected faults, and state changes; expose `GET /api/v1/transfers/{id}/trace`.
*Done when:* a faulted transfer's trace shows the fault and the resulting `UNKNOWN`.
*Issue:* [#23](https://github.com/mhnuk2007/finsim/issues/23)

**18. Scenario DSL and deterministic replay engine** `[milestone-1]`
Declarative scenario format (`scenario.v1.yaml`), seeded faults, isolated execution fixtures, and normalized canonical trace replay verification.
*Done when:* a faulted scenario replayed against an isolated fixture reproduces identical normalized trace events and final balance states.
*Issue:* [#26](https://github.com/mhnuk2007/finsim/issues/26)

---

## Samples and tooling

**19. Sample bank in Java** `[milestone-1]` `[done]`
Minimal Spring Boot bank in `samples/bank-java` implementing the participant contract with in-memory accounts, idempotency store, and seeded balances (`bank-a` on port 9001, `bank-b` on port 9002).
*Status:* Completed. Passed both unit ledger tests (`BankServiceTest`) and HTTP contract compliance tests (`ContractHttpTest`).

**20. Sample bank in Python (FastAPI)** `[milestone-1]` `[done]`
FastAPI participant bank in `samples/bank-python` implementing the `finsim/v1` contract with `decimal.Decimal` arithmetic, in-memory accounts, idempotency store, and pytest verification suite.
*Status:* Completed by @maestro-00 in [#37](https://github.com/mhnuk2007/finsim/pull/37). Closed [#12](https://github.com/mhnuk2007/finsim/issues/12).

**21. Sample bank in Node** `[milestone-1]` `[done]`
Same behavior in Node/TypeScript, in a separate directory, no shared code.
*Status:* Completed by @maestro-00 in [#41](https://github.com/mhnuk2007/finsim/pull/41). Closed [#11](https://github.com/mhnuk2007/finsim/issues/11).

**22. CLI: `register`, `transfer`, `trace`, `fault`** `[milestone-1]` `[partially-done]`
Commands that wrap the network API with human-readable output and `--json` raw machine-readable flag support.
*Status:* Registry commands (`register`, `institutions`, `networks`, `join`) completed with client and `--json` / `-j` flag handling (closed [#17](https://github.com/mhnuk2007/finsim/issues/17) in [#35](https://github.com/mhnuk2007/finsim/pull/35)). Standard `--help` and `-h` support added across all eight subcommands in [#93](https://github.com/mhnuk2007/finsim/pull/93) (closed [#54](https://github.com/mhnuk2007/finsim/issues/54)). Transfer, trace, and fault subcommands pending payment workflow API.

**23. CLI: `finsim verify --url`** `[milestone-1]` `[done]`
Check health, capabilities, debit and credit, idempotent replay, idempotency conflict, status query, and error shape. Actionable messages: which check failed, why, how to fix.
*Status:* Completed by @maestro-00 in [#76](https://github.com/mhnuk2007/finsim/pull/76) (closed [#25](https://github.com/mhnuk2007/finsim/issues/25)). Implemented 7-phase participant conformance test engine with WireMock test suite, `--verbose`, and `--json` support.

**24. Local verification and pre-commit hooks** `[milestone-1]` `[done]`
Cross-platform developer verification scripts (`scripts/verify.sh`, `scripts/verify.ps1`) and optional Spotless pre-commit hook (`.githooks/pre-commit`).
*Status:* Completed by @Tanishsahu13 in [#38](https://github.com/mhnuk2007/finsim/pull/38). Closed [#20](https://github.com/mhnuk2007/finsim/issues/20).

---

## Web & Frontend

**25. Next.js 16 web workspace scaffold, design system and base shell** `[milestone-1]` `[gfi]`
Initialize `web/` with Next.js 16 (App Router), React 19, Tailwind CSS v4, TypeScript, Lucide icons, financial semantic color tokens, marketing layout shell (`/(marketing)`), simulation cockpit shell (`/(cockpit)`), and zero-backend mock fixtures.
*Done when:* `npm run dev` and `npm run build` succeed, `/` renders the marketing shell, and `/app` renders the simulation cockpit shell.
*Issue:* [#29](https://github.com/mhnuk2007/finsim/issues/29)

---

## Release readiness

**26. Docker Compose quickstart** `[milestone-1]` `[partially-done]`
`docker compose up` brings up FinSim, Postgres, both banks, and the seeded network.
*Status:* PostgreSQL 17 compose definition active in `deploy/docker-compose.yml`. Containerization of FinSim API and sample banks pending final assembly.
*Issue:* [#19](https://github.com/mhnuk2007/finsim/issues/19)

**27. Docs pass** `[milestone-1]` `[docs]` `[in-progress]`
Keep `docs/quickstart.md`, `docs/database.md`, `docs/registry.md`, `docs/contract-v1.md`, and `README.md` synchronized with the actual codebase capabilities.

**28. Name and launch checks** `[milestone-1]` `[partially-done]`
Name search, `SECURITY` contact, Code of Conduct file, issue and PR templates, 10 labeled good first issues.
*Status:* 10 curated good first issues published on GitHub (#11 through #20) and documented in `docs/good-first-issues.md`. Issue and PR templates active. GitHub branch ruleset active on `main` enforcing required status checks and blocking force pushes. Maintainer governance checklist in progress.
