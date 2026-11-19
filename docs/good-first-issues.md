# Contributor Issues Guide

Welcome! FinSim is an open-source financial network simulator designed to connect independent services, run realistic payment flows, and inject distributed systems failures.

To keep documentation from drifting, **the active [GitHub Issues](https://github.com/mhnuk2007/finsim/issues) are the single source of truth**. Each issue contains detailed acceptance criteria, technical hints, and files to touch.
 
> [!NOTE]
> **Community First & 10-Day Policy:** Open issues are reserved for community contributors. If you'd like to work on an issue, please leave a comment on the issue thread to be assigned. If an issue remains unclaimed after **10 days**, maintainers will step in to resolve it to keep the project roadmap moving forward.

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
| **[#14](https://github.com/mhnuk2007/finsim/issues/14)** *(Closed)* | CI / Tooling | ~~**Lint the OpenAPI contract in CI**~~ *(Completed by @Tanishsahu13 in [#42](https://github.com/mhnuk2007/finsim/pull/42))*: Added Spectral ruleset (`.spectral.yaml`) and dedicated `lint-contract` CI job. |
| **[#15](https://github.com/mhnuk2007/finsim/issues/15)** *(Closed)* | `finsim-core` | ~~**Expand ISO 20022 return reason codes**~~ *(Completed by @Tanishsahu13 in [#21](https://github.com/mhnuk2007/finsim/pull/21))*: Added standard codes (`AC06`, `AG01`, `BE04`, `RC01`, `RR01`, `RR04`). |
| **[#17](https://github.com/mhnuk2007/finsim/issues/17)** *(Closed)* | `finsim-cli` | ~~**Readable CLI output with a `--json` flag**~~ *(Completed by @Tanishsahu13 in [#35](https://github.com/mhnuk2007/finsim/pull/35))*: Added `--json` / `-j` options to all commands pinning raw JSON machine-readable output contract. |
| **[#18](https://github.com/mhnuk2007/finsim/issues/18)** *(Closed)* | `finsim-core` | ~~**Property-based tests for `Money`**~~ *(Completed by @AjayVelanchery in [#47](https://github.com/mhnuk2007/finsim/pull/47))*: Added `jqwik` property-based tests verifying monetary arithmetic invariants, addition commutativity, identity, currency isolation, and scale preservation across 1,000 tries per property. |
| **[#29](https://github.com/mhnuk2007/finsim/issues/29)** | Next.js 16 / Web | **Next.js 16 web workspace scaffold, design system and base shell**: Initialize `/web` with Next.js 16, React 19, Tailwind CSS v4, Lucide, semantic color tokens, and decoupled mock fixtures. |
| **[#53](https://github.com/mhnuk2007/finsim/issues/53)** | `finsim-core` | **Domain ReasonCode missing ISO 20022 code AM03**: Add `AM03` ("Unsupported Currency") to `ReasonCode` enum with test coverage. |
| **[#54](https://github.com/mhnuk2007/finsim/issues/54)** | `finsim-cli` | **CLI subcommands reject --help flag**: Enable standard Picocli help options (`mixinStandardHelpOptions = true`) across subcommands. |
| **[#74](https://github.com/mhnuk2007/finsim/issues/74)** | Brand / Design | **Design official logo and visual identity for FinSim**: Create scalable vector (SVG) wordmark, icon-only mark, and dark/light variations. |

---

### Intermediate Community Issues ("Help Wanted")
*Independent implementations proving the contract is language-agnostic. All participants must pass the [conformance test suite](https://github.com/mhnuk2007/finsim/blob/main/contract/conformance/README.md).*

| Issue | Area | Description |
|---|---|---|
| **[#11](https://github.com/mhnuk2007/finsim/issues/11)** *(Closed)* | TypeScript / Node.js | ~~**Sample bank in Node.js / TypeScript**~~ *(Completed by @maestro-00 in [#41](https://github.com/mhnuk2007/finsim/pull/41))*: In-memory participant bank under `samples/bank-node/` using Express and TypeScript. Configurable port via `PORT`. |
| **[#12](https://github.com/mhnuk2007/finsim/issues/12)** *(Closed)* | Python / FastAPI | ~~**Sample bank in Python (FastAPI)**~~ *(Completed by @maestro-00 in [#37](https://github.com/mhnuk2007/finsim/pull/37))*: In-memory participant bank under `samples/bank-python/` using `decimal.Decimal` and `pydantic`. Default port `9003`. |
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
| **[#20](https://github.com/mhnuk2007/finsim/issues/20)** *(Closed)* | Tooling / Git | ~~**Pre-commit and commit-msg hooks with verify script**~~ *(Completed by @Tanishsahu13 in [#38](https://github.com/mhnuk2007/finsim/pull/38))*: Added cross-platform `scripts/verify.sh`, `scripts/verify.ps1`, and optional `.githooks/pre-commit`. |

---

### Community Bug Triage & Audit Issues
*Recently reported by contributors and open for community contribution under the 10-day reservation window.*

| Issue | Area | Description |
|---|---|---|
| **[#46](https://github.com/mhnuk2007/finsim/issues/46)** *(Closed)* | Database / Precision | ~~**Database monetary rounding**~~ *(Completed by @Tanishsahu13 in [#66](https://github.com/mhnuk2007/finsim/pull/66))*: Widened `transfers.amount` to `NUMERIC(22, 8)` via Flyway migration `V3` and verified round-trip precision. |
| **[#48](https://github.com/mhnuk2007/finsim/issues/48)** | `finsim-api` | **`@Transactional` proxy self-invocation**: Separate `persistTransfer` onto a distinct transactional helper bean. |
| **[#52](https://github.com/mhnuk2007/finsim/issues/52)** | `finsim-api` | **ParticipantClient HTTP 4xx error parsing**: Ensure HTTP 422 leg rejections parse into `PaymentCallOutcome.rejected` instead of suppressing as completed. |
| **[#55](https://github.com/mhnuk2007/finsim/issues/55)** | `finsim-cli` | **CLI URI encoding**: Ensure URL path variables in CLI `FinSimClient` are properly encoded. |
| **[#56](https://github.com/mhnuk2007/finsim/issues/56)** | CI / Tooling | **CI DCO sign-off fetch depth**: Increase `fetch-depth` in `actions/checkout` for pull requests to validate all commit authors. |
| **[#57](https://github.com/mhnuk2007/finsim/issues/57)** *(Closed)* | Samples | ~~**Sample banks generic 404 vs leg error**~~ *(Completed by @dansonofbass in [#70](https://github.com/mhnuk2007/finsim/pull/70))*: Distinguish generic route 404 from specific `LEG_NOT_FOUND` in Python & Node banks. |
| **[#58](https://github.com/mhnuk2007/finsim/issues/58)** | Conformance CI | **Matrix conformance workflow**: Expand `conformance.yml` matrix to execute tests across Java, Python, and Node sample banks. |
| **[#59](https://github.com/mhnuk2007/finsim/issues/59)** *(Closed)* | Contract / Docs | ~~**OpenAPI Error schema separation**~~ *(Completed by @emreocell in [#72](https://github.com/mhnuk2007/finsim/pull/72))*: Disentangled `ApiErrorCode` from domain `ReasonCode` in OpenAPI spec. |
| **[#60](https://github.com/mhnuk2007/finsim/issues/60)** | `finsim-registry` | **Network member validation**: Add validation for missing `institutionId` on network join returning HTTP 400 instead of 500. |
| **[#61](https://github.com/mhnuk2007/finsim/issues/61)** | `finsim-registry` | **Base URL trailing-slash normalization**: Normalize base URLs during institution registration idempotency check. |
| **[#62](https://github.com/mhnuk2007/finsim/issues/62)** | Database | **Database schema migration cleanup**: Clean unmapped legacy table in Flyway baseline migration. |
| **[#63](https://github.com/mhnuk2007/finsim/issues/63)** | Contract / OpenAPI | **Registry endpoints in OpenAPI**: Document administrative registry REST endpoints in `contract/finsim-v1.openapi.yaml`. |
| **[#64](https://github.com/mhnuk2007/finsim/issues/64)** | `finsim-api` | **Malformed JSON exception handling**: Add `HttpMessageNotReadableException` handler in `TransferExceptionHandler` returning standard `ApiError`. |
| **[#67](https://github.com/mhnuk2007/finsim/issues/67)** | `finsim-api` / `finsim-registry` | **Network existence validation on transfer**: Verify network exists and return HTTP 404 (`NETWORK_NOT_FOUND`) instead of creating orphaned transfer. |
| **[#68](https://github.com/mhnuk2007/finsim/issues/68)** | `finsim-api` / `finsim-core` | **CapabilityNotSupported reason code**: Distinguish missing capabilities as `ReasonCode.CAPABILITY_NOT_SUPPORTED` instead of masking as `ROUTING_ERROR`. |

## How to Contribute

1. Read [CONTRIBUTING.md](https://github.com/mhnuk2007/finsim/blob/main/CONTRIBUTING.md) for branch and PR guidelines.
2. Comment on the respective GitHub issue to claim it before opening a PR.
3. Ensure every commit carries a Developer Certificate of Origin sign-off (`git commit -s`).
4. Run `./gradlew spotlessCheck` and `./gradlew test` (or `scripts/conformance.sh <url>` for sample banks) before opening a PR.
