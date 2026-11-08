# Good First Issues for FinSim Contributors

Welcome! FinSim is an open-source financial network simulator designed to connect independent services, run realistic payment flows, and inject distributed systems failures.

We love welcoming new contributors. The issues below are well-scoped, self-contained, and accompanied by clear acceptance criteria and implementation hints. They span multiple programming languages and tech stacks (Java, Node.js, Python, Go, Docker, CI/CD).

If you want to work on one of these, please comment on the GitHub issue so we can assign it to you and avoid duplicate work!

---

## Index of Good First Issues

1. [Issue 1: Sample Bank in Node.js / TypeScript (`samples/bank-node`)](#1-sample-bank-in-nodejs--typescript)
2. [Issue 2: Sample Bank in Python with FastAPI (`samples/bank-python`)](#2-sample-bank-in-python-with-fastapi)
3. [Issue 3: Sample Bank in Go (`samples/bank-go`)](#3-sample-bank-in-go)
4. [Issue 4: OpenAPI Spec Linting and Spectral Check in CI](#4-openapi-spec-linting-and-spectral-check-in-ci)
5. [Issue 5: Expand ISO 20022 Return Reason Codes in `ReasonCode.java`](#5-expand-iso-20022-return-reason-codes-in-reasoncodejava)
6. [Issue 6: Add Swagger UI / OpenAPI Interactive Documentation to FinSim API](#6-add-swagger-ui--openapi-interactive-documentation-to-finsim-api)
7. [Issue 7: Add Raw JSON Output Flag (`--json`) to CLI Commands](#7-add-raw-json-output-flag---json-to-cli-commands)
8. [Issue 8: Property-Based Tests for Decimal `Money` Arithmetic Invariants](#8-property-based-tests-for-decimal-money-arithmetic-invariants)
9. [Issue 9: Multi-Stage Dockerfiles for FinSim API and Java Sample Bank](#9-multi-stage-dockerfiles-for-finsim-api-and-java-sample-bank)
10. [Issue 10: Local Verification Scripts and Pre-Commit Git Hook](#10-local-verification-scripts-and-pre-commit-git-hook)

---

### 1. Sample Bank in Node.js / TypeScript

**Labels**: `good first issue`, `help wanted`, `milestone-1`, `samples`  
**Area**: Multi-language ecosystem / Samples  
**Prerequisites**: Node.js 20+, npm or pnpm

#### Problem Description
FinSim is language-agnostic: institutions (banks, merchants) communicate with FinSim over standard HTTP APIs conforming to the `finsim/v1` participant contract. We currently have a reference Java implementation in `samples/bank-java/`. Milestone 1 requires a second independent sample bank written in Node.js/TypeScript to demonstrate cross-language payment flows.

#### Expected Scope
Create `samples/bank-node/` using TypeScript and Express or Fastify:
1. Implement the 4 participant endpoints defined in [`contract/finsim-v1.openapi.yaml`](../contract/finsim-v1.openapi.yaml):
   - `GET /finsim/v1/institution/health`: returns `{"status": "UP", "contractVersion": "finsim/v1", "provenance": "SYNTHETIC"}`.
   - `GET /finsim/v1/institution/capabilities`: returns supported capabilities (`ACCOUNT_DEBIT`, `ACCOUNT_CREDIT`, `STATUS_QUERY`) and currencies (`["USD"]`).
   - `POST /finsim/v1/payments`: applies `DEBIT` or `CREDIT` legs to in-memory accounts.
   - `GET /finsim/v1/payments/{transactionId}?leg=...`: reports leg status (`COMPLETED` or `REJECTED`, or 404 if not found).
2. Seed initial synthetic accounts:
   - `ACC-1`: $500.00
   - `ACC-2`: $100.00
   - `ACC-CLOSED`: closed account
3. Enforce contract rules:
   - Require `Idempotency-Key` header on `POST /payments`.
   - Same key + same body: return cached response with `200 OK`.
   - Same key + different body: return `409 IDEMPOTENCY_CONFLICT`.
   - Missing key: return `400 BAD_REQUEST`.
   - Insufficient funds: return `422` with reason code `AM04`.
   - Unknown account: return `422` with reason code `AC01`.
   - Closed account: return `422` with reason code `AC04`.
   - Check `"provenance": "SYNTHETIC"` in request body.
4. Add automated tests covering the contract endpoints.

#### Acceptance Criteria ("Done when")
- Running `npm test` inside `samples/bank-node/` passes all contract and idempotency tests.
- Starting the bank (`PORT=9002 npm start`) allows FinSim to interact with it.
- A concise `samples/bank-node/README.md` documents setup and curl examples.

---

### 2. Sample Bank in Python with FastAPI

**Labels**: `good first issue`, `help wanted`, `samples`  
**Area**: Python ecosystem / Samples  
**Prerequisites**: Python 3.11+, pytest

#### Problem Description
Python is widely used in data engineering and fintech prototyping. Having a clean Python/FastAPI sample bank will let Python developers test FinSim without needing Java knowledge.

#### Expected Scope
Create `samples/bank-python/` using FastAPI and Pydantic:
1. Implement the 4 participant endpoints from [`contract/finsim-v1.openapi.yaml`](../contract/finsim-v1.openapi.yaml).
2. In-memory ledger tracking accounts with `Decimal` arithmetic (no floats!).
3. Idempotency store caching response status and body per `Idempotency-Key`.
4. Pytest test suite testing health, capabilities, debit, credit, duplicate detection, and insufficient funds.
5. `README.md` showing how to create a virtualenv and run the server via `uvicorn`.

#### Acceptance Criteria ("Done when")
- `pytest` passes with 100% success on contract validation tests.
- Handles money exclusively using Python's `decimal.Decimal`.
- Clean documentation in `samples/bank-python/README.md`.

---

### 3. Sample Bank in Go

**Labels**: `good first issue`, `help wanted`, `samples`  
**Area**: Go ecosystem / Samples  
**Prerequisites**: Go 1.22+

#### Problem Description
A lightweight Go sample bank provides high performance, minimal memory footprint, and proves seamless integration with compiled microservices.

#### Expected Scope
Create `samples/bank-go/` using the standard library `net/http` or a lightweight router (`chi`):
1. Implement the 4 `finsim/v1` endpoints with thread-safe in-memory maps (`sync.RWMutex`).
2. Exact string/integer cents representation for money (avoid floating-point math).
3. Contract idempotency and conflict verification.
4. Comprehensive table-driven unit tests (`go test ./...`).

#### Acceptance Criteria ("Done when")
- `go test ./...` passes.
- Code conforms to standard `gofmt` and idiomatic Go practices.
- `README.md` includes instructions for running and testing with `curl`.

---

### 4. OpenAPI Spec Linting and Spectral Check in CI

**Labels**: `good first issue`, `ci`, `tooling`, `contract`  
**Area**: CI / Contract Tooling  
**Prerequisites**: GitHub Actions, Spectral / Redocly CLI

#### Problem Description
The `finsim/v1` contract in [`contract/finsim-v1.openapi.yaml`](../contract/finsim-v1.openapi.yaml) is the single source of truth for the platform and external institutions. We need automated validation in CI to catch syntax errors, broken schema references, and style violations before PRs merge.

#### Expected Scope
1. Add a validation step in `.github/workflows/ci.yml` that runs a linter (such as `@stoplight/spectral-cli` or `redocly/cli`) against `contract/finsim-v1.openapi.yaml`.
2. Configure a `.spectral.yaml` rule-set extending `spectral:oas` (OpenAPI 3.1).
3. Ensure the check runs and passes on every pull request.

#### Acceptance Criteria ("Done when")
- GitHub Actions CI fails if a pull request introduces an invalid OpenAPI schema or broken reference.
- Passing CI run confirms that the current `contract/finsim-v1.openapi.yaml` complies with all linting rules.

---

### 5. Expand ISO 20022 Return Reason Codes in `ReasonCode.java`

**Labels**: `good first issue`, `milestone-1`, `finsim-core`  
**Area**: Core Domain / Java  
**Prerequisites**: Java 21, Gradle

#### Problem Description
In financial networks (such as ISO 20022 / SEPA / FedNow), rejections must provide standard return codes. In [`ReasonCode.java`](file:///d:/finsim/finsim-core/src/main/java/io/github/mhnuk2007/finsim/core/ReasonCode.java), we support a minimal baseline (`AC01`, `AC04`, `AM02`, `AM04`, `DUPL`, `MS03`, `TIMED_OUT`, `ROUTING_ERROR`). We should expand this with frequently used ISO 20022 return reason codes.

#### Expected Scope
1. Add standard codes to `ReasonCode.java`:
   - `AC06`: Account blocked / frozen.
   - `AG01`: Transaction forbidden on this account type.
   - `BE04`: Missing or invalid creditor address.
   - `RC01`: Bank identifier incorrect / routing code invalid.
   - `RR01`: Regulatory reason.
   - `RR04`: Regulatory regulatory limit exceeded.
2. Add Javadoc comments explaining each code and its corresponding ISO 20022 definition.
3. Update unit tests in [`ReasonCodeTest.java`](file:///d:/finsim/finsim-core/src/test/java/io/github/mhnuk2007/finsim/core/ReasonCodeTest.java) to verify all codes are recognized as rejection reasons (`isRejectionReason() == true`).

#### Acceptance Criteria ("Done when")
- `./gradlew :finsim-core:test` passes.
- `./gradlew spotlessCheck` passes without formatting errors.

---

### 6. Add Swagger UI / OpenAPI Interactive Documentation to FinSim API

**Labels**: `good first issue`, `docs`, `finsim-api`  
**Area**: Developer Experience / Platform API  
**Prerequisites**: Java 21, Spring Boot

#### Problem Description
Developers exploring FinSim currently read Markdown tables or curl endpoints manually. Providing an interactive Swagger UI allows developers to inspect request/response schemas and trigger endpoints (`/api/v1/institutions`, `/api/v1/networks`) directly in their browser.

#### Expected Scope
1. Add `org.springdoc:springdoc-openapi-starter-webmvc-ui` dependency to [`finsim-api/build.gradle.kts`](file:///d:/finsim/finsim-api/build.gradle.kts).
2. Configure OpenAPI info (title, version `v1`, description) in a Spring configuration bean.
3. Verify that navigating to `http://localhost:8080/swagger-ui.html` loads the interactive interface.
4. Ensure all existing tests in `:finsim-api:test` continue to pass.

#### Acceptance Criteria ("Done when")
- `http://localhost:8080/swagger-ui.html` renders interactive documentation.
- `./gradlew check` passes with no architecture or test violations.

---

### 7. Add Raw JSON Output Flag (`--json`) to CLI Commands

**Labels**: `good first issue`, `finsim-cli`, `enhancement`  
**Area**: CLI / Automation  
**Prerequisites**: Java 21, Picocli

#### Problem Description
The FinSim CLI (`finsim-cli`) formats output for terminal readability. For developers scripting FinSim or piping outputs to `jq`, the CLI should provide a `--json` / `-j` flag that outputs pure JSON to stdout.

#### Expected Scope
1. Add `@Option(names = {"--json", "-j"}, description = "Output raw JSON") boolean jsonMode;` to commands in [`FinSimCli.java`](file:///d:/finsim/finsim-cli/src/main/java/io/github/mhnuk2007/finsim/cli/FinSimCli.java).
2. When `--json` is specified, skip tabular formatting and directly print the raw JSON response payload.
3. Add unit tests in `FinSimCliTest.java` verifying that `--json` prints valid parseable JSON.

#### Acceptance Criteria ("Done when")
- Running `finsim institutions --json` prints raw JSON with no table headers.
- `./gradlew :finsim-cli:test` passes.

---

### 8. Property-Based Tests for Decimal `Money` Arithmetic Invariants

**Labels**: `good first issue`, `finsim-core`, `testing`  
**Area**: Core Domain / Formal Testing  
**Prerequisites**: Java 21, JUnit 5 / jqwik

#### Problem Description
Financial software requires absolute precision. [`Money.java`](file:///d:/finsim/finsim-core/src/main/java/io/github/mhnuk2007/finsim/core/Money.java) encapsulates decimal amounts with attached ISO currencies. We want property-based testing to verify mathematical invariants across thousands of randomized inputs.

#### Expected Scope
1. Add property-based tests (using `jqwik` or JUnit 5 parameterized tests with randomized generators) in `finsim-core`:
   - Commutativity: `a.add(b) == b.add(a)`
   - Identity: `a.add(Money.zero(currency)) == a`
   - Currency isolation: adding different currencies must always throw `IllegalArgumentException`.
   - Scale preservation: amounts preserve 4 decimal places with exact round-trip conversion.
   - Non-negativity invariants: negative amounts rejected on creation.

#### Acceptance Criteria ("Done when")
- Property tests execute thousands of random valid amounts in CI and pass consistently.
- `./gradlew :finsim-core:test` passes.

---

### 9. Multi-Stage Dockerfiles for FinSim API and Java Sample Bank

**Labels**: `good first issue`, `milestone-1`, `devops`  
**Area**: DevOps / Containerization  
**Prerequisites**: Docker, Docker Compose

#### Problem Description
Currently, `deploy/docker-compose.yml` only spins up PostgreSQL. To achieve Milestone 1's goal ("a stranger clones the repo and sees a traced payment in under five minutes"), Docker Compose should be able to run FinSim API and the sample banks without requiring Java to be installed locally.

#### Expected Scope
1. Create multi-stage `Dockerfile` in `finsim-api/` using `eclipse-temurin:21-jdk` (build) and `eclipse-temurin:21-jre-alpine` (runtime).
2. Create `Dockerfile` in `samples/bank-java/` for running `bank-a` and `bank-b`.
3. Update `deploy/docker-compose.yml` to define services for:
   - `postgres` (port 5433:5432)
   - `finsim-api` (port 8080:8080)
   - `bank-a` (port 9001:9001)
   - `bank-b` (port 9002:9002)
4. Ensure healthchecks and network dependencies are configured.

#### Acceptance Criteria ("Done when")
- `docker compose -f deploy/docker-compose.yml up` builds and starts all services cleanly.
- `curl http://localhost:8080/actuator/health` and `curl http://localhost:9001/finsim/v1/institution/health` both return UP.

---

### 10. Local Verification Scripts and Pre-Commit Git Hook

**Labels**: `good first issue`, `tooling`, `developer-experience`  
**Area**: Tooling / Developer Experience  
**Prerequisites**: Bash, PowerShell, Git

#### Problem Description
Every pull request must have spotless formatting (`./gradlew spotlessCheck`), passing tests (`./gradlew test`), and Developer Certificate of Origin sign-offs (`git commit -s`). Having local pre-commit tooling prevents CI failures due to minor formatting discrepancies.

#### Expected Scope
1. Create `scripts/verify.sh` (for Linux/macOS/Git Bash) and `scripts/verify.ps1` (for Windows PowerShell).
2. The script runs:
   - `./gradlew spotlessCheck`
   - `./gradlew test`
   - Validates that the latest git commit contains `Signed-off-by:`.
3. Create an optional `.githooks/pre-commit` hook that contributors can enable via `git config core.hooksPath .githooks`.
4. Document the scripts in `CONTRIBUTING.md`.

#### Acceptance Criteria ("Done when")
- Both `scripts/verify.sh` and `scripts/verify.ps1` run successfully on a clean workspace.
- Clear error messages explain how to fix failed steps (e.g. `Run ./gradlew spotlessApply to fix formatting`).
