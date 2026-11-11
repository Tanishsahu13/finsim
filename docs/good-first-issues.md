# Contributor Issues Guide

Welcome! FinSim is an open-source financial network simulator designed to connect independent services, run realistic payment flows, and inject distributed systems failures.

To keep documentation from drifting, **the active [GitHub Issues](https://github.com/mhnuk2007/finsim/issues) are the single source of truth**. Each issue contains detailed acceptance criteria, technical hints, and files to touch.

---

## Filter by Label on GitHub

- **[Browse all Good First Issues](https://github.com/mhnuk2007/finsim/labels/good%20first%20issue)** (beginner-friendly, single-module scope)
- **[Browse all Help Wanted Issues](https://github.com/mhnuk2007/finsim/labels/help%20wanted)** (intermediate, multi-component, or cross-language)

---

## Active Issues Index

### Beginner-Friendly ("Good First Issue")
*Small, self-contained tasks focused on a single module, tooling, or tests.*

| Issue | Area | Description |
|---|---|---|
| **[#14](https://github.com/mhnuk2007/finsim/issues/14)** | CI / Tooling | **Lint the OpenAPI contract in CI**: Add Spectral linter to validate `contract/finsim-v1.openapi.yaml` in GitHub Actions. |
| **[#15](https://github.com/mhnuk2007/finsim/issues/15)** *(Closed)* | `finsim-core` | ~~**Expand ISO 20022 return reason codes**~~ *(Completed by @Tanishsahu13 in [#21](https://github.com/mhnuk2007/finsim/pull/21))*: Added standard codes (`AC06`, `AG01`, `BE04`, `RC01`, `RR01`, `RR04`). |
| **[#17](https://github.com/mhnuk2007/finsim/issues/17)** | `finsim-cli` | **Readable CLI output with a `--json` flag**: Provide human-friendly formatted/tabular output by default, with `--json` preserving raw JSON output. Note: CLI currently has no JSON library; add a lightweight dependency like Jackson. |
| **[#18](https://github.com/mhnuk2007/finsim/issues/18)** | `finsim-core` | **Property-based tests for `Money`**: Use `jqwik` to verify mathematical invariants (`plus`, `minus`, scale up to 8 decimal places, currency isolation) across thousands of random amounts. |
| **[#20](https://github.com/mhnuk2007/finsim/issues/20)** | Tooling / Git | **Pre-commit and commit-msg hooks with verify script**: Add POSIX bash hooks in `.githooks/` for Spotless formatting and DCO sign-offs (`git commit -s`), plus `scripts/verify.sh`. |
| **[#29](https://github.com/mhnuk2007/finsim/issues/29)** | Next.js 16 / Web | **Next.js 16 web workspace scaffold, design system and base shell**: Initialize `/web` with Next.js 16, React 19, Tailwind CSS v4, Lucide, semantic color tokens, and decoupled mock fixtures. |

---

### Intermediate Community Issues ("Help Wanted")
*Independent implementations proving the contract is language-agnostic. All participants must pass the [conformance test suite](https://github.com/mhnuk2007/finsim/blob/main/contract/conformance/README.md).*

| Issue | Area | Description |
|---|---|---|
| **[#11](https://github.com/mhnuk2007/finsim/issues/11)** | TypeScript / Node.js | **Sample bank in Node.js / TypeScript**: In-memory participant bank under `samples/bank-node/` using Express or Fastify. Default port `9012` (or configurable via `PORT`) to avoid colliding with Java `bank-b` on `9002`. |
| **[#12](https://github.com/mhnuk2007/finsim/issues/12)** | Python / FastAPI | **Sample bank in Python (FastAPI)**: In-memory participant bank under `samples/bank-python/` using `decimal.Decimal` and `pydantic`. Default port `9003`. |
| **[#13](https://github.com/mhnuk2007/finsim/issues/13)** | Go | **Sample bank in Go**: In-memory participant bank under `samples/bank-go/` using `net/http` or `chi`. Default port `9004`. |
| **[#16](https://github.com/mhnuk2007/finsim/issues/16)** *(Closed)* | Spring Boot / Docs | ~~**Dev-only Swagger UI for contract and admin API**~~ *(Completed by @wood0v0 in [#30](https://github.com/mhnuk2007/finsim/pull/30))*: Added `springdoc-openapi-starter-webmvc-ui` to expose Swagger UI at `/swagger-ui.html` and metadata at `/v3/api-docs`. |
| **[#31](https://github.com/mhnuk2007/finsim/issues/31)** | `finsim-registry` / `finsim-api` | **Dynamic network directory discovery endpoint**: Expose `GET /api/v1/networks/{networkId}/directory` to resolve participant endpoints and declared capabilities without hardcoded URLs. |
| **[#33](https://github.com/mhnuk2007/finsim/issues/33)** | Samples / `finsim-sim` | **Sample ATM participant & partial-dispense simulation**: Model physical cash delivery states (`DISPENSED`, `PARTIALLY_DISPENSED`, `NOT_DISPENSED`) and automatic compensating credit adjustments. |

---

### Advanced Systems & Verification ("Help Wanted")
*Core distributed systems simulation, tracing, chaos invariant testing, time-travel, and double-entry accounting.*

| Issue | Area | Description |
|---|---|---|
| **[#22](https://github.com/mhnuk2007/finsim/issues/22)** | `finsim-sim` / `finsim-api` | **Fault injection rule engine**: Configurable rules (`DELAY`, `TIMEOUT`, `DUPLICATE_DELIVERY`), seeded deterministic PRNG, count limits, and HTTP client interception. |
| **[#23](https://github.com/mhnuk2007/finsim/issues/23)** | `finsim-api` | **Transfer trace recording and endpoint**: Record stages, outbound calls, injected faults, and recovery steps chronologically; expose `GET /api/v1/transfers/{id}/trace`. |
| **[#24](https://github.com/mhnuk2007/finsim/issues/24)** | `finsim-core` / `finsim-api` | **Property-based tests for zero duplicate financial effects**: Prove conservation of value and idempotency invariants under random chaos sequences with `jqwik`. |
| **[#25](https://github.com/mhnuk2007/finsim/issues/25)** | `finsim-cli` | **Participant conformance test engine (`finsim verify --url`)**: Native Java/Picocli 7-phase conformance runner with detailed diagnostic reporting. |
| **[#26](https://github.com/mhnuk2007/finsim/issues/26)** | `finsim-sim` | **Declarative scenario DSL & deterministic replay**: Versionable scenarios (`scenario.v1.yaml`), isolated test fixtures, and canonical trace normalization for byte-level deterministic replay. |
| **[#27](https://github.com/mhnuk2007/finsim/issues/27)** | `finsim-sim` | **Deterministic virtual clock & time-travel engine**: Strict separation of wall-clock security checks vs. discrete virtual business time, priority collision ordering, and hold expiry hooks. |
| **[#28](https://github.com/mhnuk2007/finsim/issues/28)** | `finsim-core` | **Immutable double-entry ledger & balanced journal invariants**: Multi-currency balanced journal rule ($\sum \text{Debits} \equiv \sum \text{Credits}$), database-level immutability, and materialized balance projections. |
| **[#32](https://github.com/mhnuk2007/finsim/issues/32)** | `finsim-sim` / `finsim-api` | **Simulation state snapshot, reset and fixture restore engine**: Sub-50ms target reset of ledgers, clocks, PRNG seeds, and fault rules for zero-leakage CI chaos testing. |

---

### Core Team Work
*On the critical path for the Milestone 1 launch; maintained directly by core team.*

| Issue | Area | Description |
|---|---|---|
| **[#19](https://github.com/mhnuk2007/finsim/issues/19)** | DevOps / Compose | **Dockerfiles and full Docker Compose stack**: Multi-stage Docker builds for `finsim-api` and `samples/bank-java` (`bank-a` and `bank-b`), enabling `docker compose up --build` with no local Java installation needed. |

---

## How to Contribute

1. Read [CONTRIBUTING.md](https://github.com/mhnuk2007/finsim/blob/main/CONTRIBUTING.md) for branch and PR guidelines.
2. Comment on the respective GitHub issue to claim it before opening a PR.
3. Ensure every commit carries a Developer Certificate of Origin sign-off (`git commit -s`).
4. Run `./gradlew spotlessCheck` and `./gradlew test` (or `scripts/conformance.sh <url>` for sample banks) before opening a PR.
