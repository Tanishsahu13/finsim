# Create Good First Issues on GitHub repository: mhnuk2007/finsim
#
# Usage:
#   # Preview issues to be created (dry-run):
#   .\scripts\create-github-issues.ps1
#
#   # Actually create them with a GitHub Personal Access Token:
#   .\scripts\create-github-issues.ps1 -Token "ghp_yourTokenHere" -Apply
#   # Or with environment variable:
#   $env:GITHUB_TOKEN = "ghp_yourTokenHere"
#   .\scripts\create-github-issues.ps1 -Apply

param(
    [string]$Token = $env:GITHUB_TOKEN,
    [string]$Repo = "mhnuk2007/finsim",
    [switch]$Apply
)

$issues = @(
    @{
        Title = "feat(samples): implement Node.js/TypeScript sample bank"
        Labels = @("good first issue", "help wanted", "milestone-1", "samples")
        Body = @"
### Problem Description
FinSim is language-agnostic: institutions (banks, merchants) communicate with FinSim over standard HTTP APIs conforming to the `finsim/v1` participant contract. We currently have a reference Java implementation in `samples/bank-java/`. Milestone 1 requires a second independent sample bank written in Node.js/TypeScript to demonstrate cross-language payment flows.

### Expected Scope
Create `samples/bank-node/` using TypeScript and Express or Fastify:
1. Implement the 4 participant endpoints defined in `contract/finsim-v1.openapi.yaml`:
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

### Acceptance Criteria
- Running `npm test` inside `samples/bank-node/` passes all contract and idempotency tests.
- Starting the bank (`PORT=9002 npm start`) allows FinSim to interact with it.
- A concise `samples/bank-node/README.md` documents setup and curl examples.
"@
    },
    @{
        Title = "feat(samples): implement Python sample bank with FastAPI"
        Labels = @("good first issue", "help wanted", "samples")
        Body = @"
### Problem Description
Python is widely used in data engineering and fintech prototyping. Having a clean Python/FastAPI sample bank will let Python developers test FinSim without needing Java knowledge.

### Expected Scope
Create `samples/bank-python/` using FastAPI and Pydantic:
1. Implement the 4 participant endpoints from `contract/finsim-v1.openapi.yaml`.
2. In-memory ledger tracking accounts with `Decimal` arithmetic (no floating-point numbers!).
3. Idempotency store caching response status and body per `Idempotency-Key`.
4. Pytest test suite testing health, capabilities, debit, credit, duplicate detection, and insufficient funds.
5. `README.md` showing how to create a virtualenv and run the server via `uvicorn`.

### Acceptance Criteria
- `pytest` passes with 100% success on contract validation tests.
- Handles money exclusively using Python's `decimal.Decimal`.
- Clean documentation in `samples/bank-python/README.md`.
"@
    },
    @{
        Title = "feat(samples): implement Go sample bank"
        Labels = @("good first issue", "help wanted", "samples")
        Body = @"
### Problem Description
A lightweight Go sample bank provides high performance, minimal memory footprint, and proves seamless integration with compiled microservices.

### Expected Scope
Create `samples/bank-go/` using the standard library `net/http` or a lightweight router (`chi`):
1. Implement the 4 `finsim/v1` endpoints with thread-safe in-memory maps (`sync.RWMutex`).
2. Exact string/integer cents representation for money (avoid floating-point math).
3. Contract idempotency and conflict verification.
4. Comprehensive table-driven unit tests (`go test ./...`).

### Acceptance Criteria
- `go test ./...` passes.
- Code conforms to standard `gofmt` and idiomatic Go practices.
- `README.md` includes instructions for running and testing with `curl`.
"@
    },
    @{
        Title = "ci: add OpenAPI specification linting and Spectral check"
        Labels = @("good first issue", "ci", "tooling", "contract")
        Body = @"
### Problem Description
The `finsim/v1` contract in `contract/finsim-v1.openapi.yaml` is the single source of truth for the platform and external institutions. We need automated validation in CI to catch syntax errors, broken schema references, and style violations before PRs merge.

### Expected Scope
1. Add a validation step in `.github/workflows/ci.yml` that runs a linter (such as `@stoplight/spectral-cli` or `redocly/cli`) against `contract/finsim-v1.openapi.yaml`.
2. Configure a `.spectral.yaml` rule-set extending `spectral:oas` (OpenAPI 3.1).
3. Ensure the check runs and passes on every pull request.

### Acceptance Criteria
- GitHub Actions CI fails if a pull request introduces an invalid OpenAPI schema or broken reference.
- Passing CI run confirms that the current `contract/finsim-v1.openapi.yaml` complies with all linting rules.
"@
    },
    @{
        Title = "feat(core): expand ISO 20022 return reason codes in ReasonCode"
        Labels = @("good first issue", "milestone-1", "finsim-core")
        Body = @"
### Problem Description
In financial networks (such as ISO 20022 / SEPA / FedNow), rejections must provide standard return codes. In `ReasonCode.java`, we support a minimal baseline (`AC01`, `AC04`, `AM02`, `AM04`, `DUPL`, `MS03`, `TIMED_OUT`, `ROUTING_ERROR`). We should expand this with frequently used ISO 20022 return reason codes.

### Expected Scope
1. Add standard codes to `ReasonCode.java`:
   - `AC06`: Account blocked / frozen.
   - `AG01`: Transaction forbidden on this account type.
   - `BE04`: Missing or invalid creditor address.
   - `RC01`: Bank identifier incorrect / routing code invalid.
   - `RR01`: Regulatory reason.
   - `RR04`: Regulatory limit exceeded.
2. Add Javadoc comments explaining each code and its corresponding ISO 20022 definition.
3. Update unit tests in `ReasonCodeTest.java` to verify all codes are recognized as rejection reasons (`isRejectionReason() == true`).

### Acceptance Criteria
- `./gradlew :finsim-core:test` passes.
- `./gradlew spotlessCheck` passes without formatting errors.
"@
    },
    @{
        Title = "feat(api): add Swagger UI / OpenAPI interactive documentation"
        Labels = @("good first issue", "docs", "finsim-api")
        Body = @"
### Problem Description
Developers exploring FinSim currently read Markdown tables or curl endpoints manually. Providing an interactive Swagger UI allows developers to inspect request/response schemas and trigger endpoints (`/api/v1/institutions`, `/api/v1/networks`) directly in their browser.

### Expected Scope
1. Add `org.springdoc:springdoc-openapi-starter-webmvc-ui` dependency to `finsim-api/build.gradle.kts`.
2. Configure OpenAPI info (title, version `v1`, description) in a Spring configuration bean.
3. Verify that navigating to `http://localhost:8080/swagger-ui.html` loads the interactive interface.
4. Ensure all existing tests in `:finsim-api:test` continue to pass.

### Acceptance Criteria
- `http://localhost:8080/swagger-ui.html` renders interactive documentation.
- `./gradlew check` passes with no architecture or test violations.
"@
    },
    @{
        Title = "feat(cli): add --json raw output flag to CLI commands"
        Labels = @("good first issue", "finsim-cli", "enhancement")
        Body = @"
### Problem Description
The FinSim CLI (`finsim-cli`) formats output for terminal readability. For developers scripting FinSim or piping outputs to `jq`, the CLI should provide a `--json` / `-j` flag that outputs pure JSON to stdout.

### Expected Scope
1. Add `@Option(names = {"--json", "-j"}, description = "Output raw JSON") boolean jsonMode;` to commands in `FinSimCli.java`.
2. When `--json` is specified, skip tabular formatting and directly print the raw JSON response payload.
3. Add unit tests in `FinSimCliTest.java` verifying that `--json` prints valid parseable JSON.

### Acceptance Criteria
- Running `finsim institutions --json` prints raw JSON with no table headers.
- `./gradlew :finsim-cli:test` passes.
"@
    },
    @{
        Title = "test(core): add property-based tests for decimal Money invariants"
        Labels = @("good first issue", "finsim-core", "testing")
        Body = @"
### Problem Description
Financial software requires absolute precision. `Money.java` encapsulates decimal amounts with attached ISO currencies. We want property-based testing to verify mathematical invariants across thousands of randomized inputs.

### Expected Scope
1. Add property-based tests (using `jqwik` or JUnit 5 parameterized tests with randomized generators) in `finsim-core`:
   - Commutativity: `a.add(b) == b.add(a)`
   - Identity: `a.add(Money.zero(currency)) == a`
   - Currency isolation: adding different currencies must always throw `IllegalArgumentException`.
   - Scale preservation: amounts preserve 4 decimal places with exact round-trip conversion.
   - Non-negativity invariants: negative amounts rejected on creation.

### Acceptance Criteria
- Property tests execute thousands of random valid amounts in CI and pass consistently.
- `./gradlew :finsim-core:test` passes.
"@
    },
    @{
        Title = "feat(devops): add multi-stage Dockerfiles for API and sample bank"
        Labels = @("good first issue", "milestone-1", "devops")
        Body = @"
### Problem Description
Currently, `deploy/docker-compose.yml` only spins up PostgreSQL. To achieve Milestone 1's goal ("a stranger clones the repo and sees a traced payment in under five minutes"), Docker Compose should be able to run FinSim API and the sample banks without requiring Java to be installed locally.

### Expected Scope
1. Create multi-stage `Dockerfile` in `finsim-api/` using `eclipse-temurin:21-jdk` (build) and `eclipse-temurin:21-jre-alpine` (runtime).
2. Create `Dockerfile` in `samples/bank-java/` for running `bank-a` and `bank-b`.
3. Update `deploy/docker-compose.yml` to define services for:
   - `postgres` (port 5433:5432)
   - `finsim-api` (port 8080:8080)
   - `bank-a` (port 9001:9001)
   - `bank-b` (port 9002:9002)
4. Ensure healthchecks and network dependencies are configured.

### Acceptance Criteria
- `docker compose -f deploy/docker-compose.yml up` builds and starts all services cleanly.
- `curl http://localhost:8080/actuator/health` and `curl http://localhost:9001/finsim/v1/institution/health` both return UP.
"@
    },
    @{
        Title = "tooling: add verification scripts and pre-commit hook"
        Labels = @("good first issue", "tooling", "developer-experience")
        Body = @"
### Problem Description
Every pull request must have spotless formatting (`./gradlew spotlessCheck`), passing tests (`./gradlew test`), and Developer Certificate of Origin sign-offs (`git commit -s`). Having local pre-commit tooling prevents CI failures due to minor formatting discrepancies.

### Expected Scope
1. Create `scripts/verify.sh` (for Linux/macOS/Git Bash) and `scripts/verify.ps1` (for Windows PowerShell).
2. The script runs:
   - `./gradlew spotlessCheck`
   - `./gradlew test`
   - Validates that the latest git commit contains `Signed-off-by:`.
3. Create an optional `.githooks/pre-commit` hook that contributors can enable via `git config core.hooksPath .githooks`.
4. Document the scripts in `CONTRIBUTING.md`.

### Acceptance Criteria
- Both `scripts/verify.sh` and `scripts/verify.ps1` run successfully on a clean workspace.
- Clear error messages explain how to fix failed steps (e.g. `Run ./gradlew spotlessApply to fix formatting`).
"@
    }
)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "FinSim GitHub Issues Creator ($Repo)" -ForegroundColor Cyan
Write-Host "Total issues defined: $($issues.Count)" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

if (-not $Apply) {
    Write-Host "`nDRY RUN MODE: No issues will be created." -ForegroundColor Yellow
    Write-Host "To actually create issues, run with -Apply and provide a token (-Token <token> or `$env:GITHUB_TOKEN).`n" -ForegroundColor Yellow
    foreach ($item in $issues) {
        Write-Host "• Title: $($item.Title)" -ForegroundColor Green
        Write-Host "  Labels: $($item.Labels -join ', ')" -ForegroundColor DarkGray
    }
    exit 0
}

if (-not $Token) {
    Write-Error "Error: No GitHub token provided. Supply -Token <ghp_...> or set `$env:GITHUB_TOKEN."
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $Token"
    "Accept"        = "application/vnd.github+json"
    "User-Agent"    = "FinSim-Issue-Creator"
}

$apiUrl = "https://api.github.com/repos/$Repo/issues"

foreach ($item in $issues) {
    Write-Host "Creating issue: $($item.Title)... " -NoNewline
    $payload = @{
        title  = $item.Title
        labels = $item.Labels
        body   = $item.Body
    } | ConvertTo-Json -Depth 5

    try {
        $response = Invoke-RestMethod -Uri $apiUrl -Method Post -Headers $headers -Body $payload
        Write-Host "DONE! -> $($response.html_url)" -ForegroundColor Green
    }
    catch {
        Write-Host "FAILED!" -ForegroundColor Red
        Write-Error $_.Exception.Message
    }
}
