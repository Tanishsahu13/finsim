# Sync/Update GitHub issues #11 through #20 with clean drafts
#
# Usage:
#   # Preview changes (dry-run):
#   .\scripts\sync-github-issues.ps1
#
#   # Update live GitHub issues:
#   .\scripts\sync-github-issues.ps1 -Token "ghp_yourTokenHere" -Apply

param(
    [string]$Token = $env:GITHUB_TOKEN,
    [string]$Repo = "mhnuk2007/finsim",
    [switch]$Apply
)

$issueUpdates = @{
    11 = @{
        Title = "Sample bank in Node.js / TypeScript"
        Labels = @("help wanted", "samples", "nodejs", "typescript")
        Body = @"
## Context

FinSim connects independent institutions over a small HTTP contract, `finsim/v1`. We have one reference bank in Java (`samples/bank-java`). A second bank in another language proves the contract needs nothing from FinSim's own code.

## What to build

A bank in `samples/bank-node/` (Express or Fastify, TypeScript) that implements the four participant endpoints with an in-memory ledger:

- `GET /finsim/v1/institution/health`
- `GET /finsim/v1/institution/capabilities`
- `POST /finsim/v1/payments`
- `GET /finsim/v1/payments/{transactionId}?leg=DEBIT|CREDIT`

The exact behavior, error codes, and seed accounts are in [contract/conformance/README.md](https://github.com/mhnuk2007/finsim/blob/main/contract/conformance/README.md). Read that first. The contract itself is [contract/finsim-v1.openapi.yaml](https://github.com/mhnuk2007/finsim/blob/main/contract/finsim-v1.openapi.yaml).

Summary of what the bank must do:

- Idempotency: same `Idempotency-Key` and same body replays the original result with `200`; different body gives `409 IDEMPOTENCY_CONFLICT`; missing key gives `400 MISSING_IDEMPOTENCY_KEY`.
- Business rejections return `422` with a reason code (`AC01`, `AC04`, `AM02`, `AM03`, `AM04`) and are remembered, so a status query reports the leg as `REJECTED`.
- A status query for a leg the bank never received returns `404 LEG_NOT_FOUND`.
- Reversals: a `CREDIT` leg with its own `transactionId` and `reversalOf`. It must match a completed debit, and a debit can be reversed once (`DUPL`, `MS03`).
- Only accept `"provenance": "SYNTHETIC"`.
- Seed accounts, all USD: `ACC-1` 1000.00, `ACC-2` 250.00, `ACC-CLOSED` (closed).
- Listen on `PORT`, default `9012` (to avoid colliding with Java `bank-b` on `9002`).

## Hints

- Money is a decimal string in the contract. Do not use JavaScript `number` for amounts; use a decimal library (for example `decimal.js`) or scaled `BigInt`.
- Port the logic and tests from `samples/bank-java/src/main/java/.../BankService.java` and its tests. It's about 200 lines.
- Handle concurrent requests safely (a simple per-process lock or a single-threaded ledger is fine).

## Acceptance criteria

- [ ] `npm test` passes in `samples/bank-node/` (unit tests for the ledger rules plus HTTP tests).
- [ ] `npm start` runs the bank, and `scripts/conformance.sh http://localhost:9012` passes every check.
- [ ] `samples/bank-node/README.md` explains setup, running, and shows `curl` examples.
- [ ] No dependency on any FinSim Java code.

## Out of scope

Persistence, authentication, and any endpoint beyond the four contract endpoints.

---
**Claiming and submitting:** comment on this issue so it can be assigned to you. Sign off every commit (`git commit -s`), and see [CONTRIBUTING.md](https://github.com/mhnuk2007/finsim/blob/main/CONTRIBUTING.md).
"@
    }
    12 = @{
        Title = "Sample bank in Python (FastAPI)"
        Labels = @("help wanted", "samples", "python")
        Body = @"
## Context

FinSim connects independent institutions over a small HTTP contract, `finsim/v1`. A Python bank lets Python developers try FinSim without any Java knowledge, and shows the contract is language-neutral.

## What to build

A bank in `samples/bank-python/` (FastAPI and Pydantic) implementing the four participant endpoints with an in-memory ledger:

- `GET /finsim/v1/institution/health`
- `GET /finsim/v1/institution/capabilities`
- `POST /finsim/v1/payments`
- `GET /finsim/v1/payments/{transactionId}?leg=DEBIT|CREDIT`

All behavior, error codes, and seed accounts are specified in [contract/conformance/README.md](https://github.com/mhnuk2007/finsim/blob/main/contract/conformance/README.md). Read that first. The contract is [contract/finsim-v1.openapi.yaml](https://github.com/mhnuk2007/finsim/blob/main/contract/finsim-v1.openapi.yaml).

Key points:

- Idempotency (`200` replay, `409 IDEMPOTENCY_CONFLICT`, `400 MISSING_IDEMPOTENCY_KEY`).
- Rejections are `422` with `AC01`, `AC04`, `AM02`, `AM03`, or `AM04`, and are remembered for status queries.
- Status query: `200` with the leg, or `404 LEG_NOT_FOUND`.
- Reversals via `reversalOf` (`DUPL`, `MS03`).
- Seed accounts, all USD: `ACC-1` 1000.00, `ACC-2` 250.00, `ACC-CLOSED` (closed). Default port `9003`.

## Hints

- Use `decimal.Decimal` for all money. Never `float`. Keep amounts as strings in JSON.
- FastAPI returns `422` for validation errors by default. The contract wants `400 INVALID_REQUEST` in the contract's error shape, so add an exception handler.
- Port the rules and tests from `samples/bank-java/src/main/java/.../BankService.java`.

## Acceptance criteria

- [ ] `pytest` passes in `samples/bank-python/`.
- [ ] After `uvicorn` starts the bank, `scripts/conformance.sh http://localhost:9003` passes every check.
- [ ] Money is handled only with `decimal.Decimal`.
- [ ] `samples/bank-python/README.md` shows how to create a virtualenv, run the server, and run the tests.

## Out of scope

Persistence, authentication, and endpoints beyond the four contract endpoints.

---
**Claiming and submitting:** comment on this issue so it can be assigned to you. Sign off every commit (`git commit -s`), and see [CONTRIBUTING.md](https://github.com/mhnuk2007/finsim/blob/main/CONTRIBUTING.md).
"@
    }
    13 = @{
        Title = "Sample bank in Go"
        Labels = @("help wanted", "samples", "go")
        Body = @"
## Context

FinSim connects independent institutions over a small HTTP contract, `finsim/v1`. A Go bank shows the contract works for compiled, cloud-native services too.

## What to build

A bank in `samples/bank-go/` using `net/http` (or a small router such as `chi`) implementing the four participant endpoints with an in-memory ledger:

- `GET /finsim/v1/institution/health`
- `GET /finsim/v1/institution/capabilities`
- `POST /finsim/v1/payments`
- `GET /finsim/v1/payments/{transactionId}?leg=DEBIT|CREDIT`

All behavior, error codes, and seed accounts are in [contract/conformance/README.md](https://github.com/mhnuk2007/finsim/blob/main/contract/conformance/README.md). Read that first. The contract is [contract/finsim-v1.openapi.yaml](https://github.com/mhnuk2007/finsim/blob/main/contract/finsim-v1.openapi.yaml).

Key points:

- Idempotency (`200` replay, `409 IDEMPOTENCY_CONFLICT`, `400 MISSING_IDEMPOTENCY_KEY`).
- Rejections are `422` with `AC01`, `AC04`, `AM02`, `AM03`, or `AM04`, and are remembered for status queries.
- Status query: `200` with the leg, or `404 LEG_NOT_FOUND`.
- Reversals via `reversalOf` (`DUPL`, `MS03`).
- Seed accounts, all USD: `ACC-1` 1000.00, `ACC-2` 250.00, `ACC-CLOSED` (closed). Default port `9004`.

## Hints

- Amounts are decimal strings with up to 8 decimal places. Parse them into integer units of 10^-8 (or use `math/big`). Never `float64`.
- Guard the ledger and idempotency store with a `sync.Mutex`; the check-then-apply must be atomic.
- Port the rules and tests from `samples/bank-java/src/main/java/.../BankService.java`.

## Acceptance criteria

- [ ] `go test ./...` passes (table-driven tests for the ledger rules plus HTTP tests with `httptest`).
- [ ] `gofmt` and `go vet` are clean.
- [ ] With the bank running, `scripts/conformance.sh http://localhost:9004` passes every check.
- [ ] `samples/bank-go/README.md` explains how to run and test it, with `curl` examples.

## Out of scope

Persistence, authentication, and endpoints beyond the four contract endpoints.

---
**Claiming and submitting:** comment on this issue so it can be assigned to you. Sign off every commit (`git commit -s`), and see [CONTRIBUTING.md](https://github.com/mhnuk2007/finsim/blob/main/CONTRIBUTING.md).
"@
    }
    14 = @{
        Title = "Lint the OpenAPI contract in CI"
        Labels = @("good first issue", "ci", "contract")
        Body = @"
## Context

[contract/finsim-v1.openapi.yaml](https://github.com/mhnuk2007/finsim/blob/main/contract/finsim-v1.openapi.yaml) is the source of truth for every institution that connects to FinSim. A typo or broken `$ref` there breaks every bank built from it, so CI should catch problems before a pull request merges.

## What to build

1. A `.spectral.yaml` at the repo root that extends `spectral:oas`.
2. A new workflow `.github/workflows/contract-lint.yml` that runs on pull requests touching `contract/**` or `.spectral.yaml`, and on pushes to `main`.
3. The lint command:
   ```bash
   npx --yes @stoplight/spectral-cli lint contract/finsim-v1.openapi.yaml --fail-severity=error
   ```

## Hints

- Fail the build on errors only at first. Warnings (missing contact, operation descriptions) should be visible but not blocking.
- The spec is OpenAPI 3.1.
- Document in `CONTRIBUTING.md` how to run the linter locally.

## Acceptance criteria

- [ ] CI is green on the current spec.
- [ ] Introducing a broken `$ref` or invalid schema in a test branch makes the workflow fail.
- [ ] The workflow runs only for contract-related changes, plus pushes to `main`.

## Out of scope

Checking that implementations match the spec. That is the job of the conformance suite in `contract/conformance/`.

---
**Claiming and submitting:** comment on this issue so it can be assigned to you. Sign off every commit (`git commit -s`), and see [CONTRIBUTING.md](https://github.com/mhnuk2007/finsim/blob/main/CONTRIBUTING.md).
"@
    }
    15 = @{
        Title = "Expand ISO 20022 reason codes"
        Labels = @("good first issue", "finsim-core")
        Body = @"
## Context

When a transfer is rejected, FinSim attaches a standard reason code so developers can tell what went wrong and who said so. `finsim-core` has a small baseline in `ReasonCode.java`. We want more of the commonly used ISO 20022 external return reason codes.

## What to build

Add these codes to `ReasonCode.java`, each with a short Javadoc giving its ISO 20022 name and meaning:

| Code | Intended meaning |
|---|---|
| `AC06` | Account blocked |
| `AG01` | Transaction forbidden |
| `BE04` | Missing creditor address |
| `RC01` | Bank identifier incorrect |
| `RR04` | Regulatory reason |
| `TM01` | Invalid cut-off time |

**Verify every code and its official name** against the ISO 20022 external code sets (the *ExternalReturnReason* list at [iso20022.org](https://www.iso20022.org/catalogue-messages/additional-content-messages/external-code-sets)) before merging. The table above is a starting point and may contain mistakes. If a code is wrong or missing, say so in the PR and use the official list.

## Hints

- Keep API HTTP error codes (`IDEMPOTENCY_CONFLICT`, `RECONCILIATION_REQUIRED`, `MISSING_IDEMPOTENCY_KEY`) strictly out of `ReasonCode`. They belong in `ApiErrorCode.java`. `ReasonCode` is only for reasons a *payment* is rejected.
- Follow the pattern of the existing entries and `isRejectionReason()`.
- Don't rename or remove existing codes in this issue.

## Acceptance criteria

- [ ] Each new code is added with Javadoc citing its ISO 20022 definition.
- [ ] `ReasonCodeTest` covers every new code: lookup by string, and `isRejectionReason()`.
- [ ] `./gradlew :finsim-core:test` and `./gradlew spotlessCheck` pass.

## Out of scope

Changing the contract, API error codes, or how rejections are produced.

---
**Claiming and submitting:** comment on this issue so it can be assigned to you. Sign off every commit (`git commit -s`), and see [CONTRIBUTING.md](https://github.com/mhnuk2007/finsim/blob/main/CONTRIBUTING.md).
"@
    }
    16 = @{
        Title = "Dev-only Swagger UI for the contract and admin API"
        Labels = @("help wanted", "docs", "finsim-api")
        Body = @"
## Context

Developers exploring FinSim currently read Markdown and craft `curl` commands. An interactive page where they can read and try the API would help a lot.

FinSim is **contract-first**: the hand-written [contract/finsim-v1.openapi.yaml](https://github.com/mhnuk2007/finsim/blob/main/contract/finsim-v1.openapi.yaml) is authoritative. So this page must **display that file**, not generate documentation from controller code (which would drift from the contract).

## What to build

1. **A hand-written spec for the admin API**, `contract/finsim-admin.openapi.yaml`, covering the registry endpoints described in [docs/registry.md](https://github.com/mhnuk2007/finsim/blob/main/docs/registry.md) (`/api/v1/institutions`, `/api/v1/networks`, `/api/v1/networks/{networkId}/members`) with their error shape.
2. **A dev-only Swagger UI page** served by `finsim-api` that lets the user pick between the two specs and try requests against the running app.
3. Gated by a property, for example `finsim.dev-ui.enabled=true` (default **false**), so it never ships enabled by accident.

## Hints

- Avoid generating specs from controllers. A simple approach is the `org.webjars:swagger-ui` webjar plus a small static HTML page pointing at the two YAML files.
- Put the config in the `finsim-api` module, not in the registry module.
- Document how to turn it on in `README.md` or `docs/registry.md`.

## Acceptance criteria

- [ ] With the property enabled, the UI loads in the browser and shows both specs. The contract spec is the unmodified file from `contract/`.
- [ ] With the property disabled (the default), the UI is not served.
- [ ] `finsim-admin.openapi.yaml` matches the real endpoints.
- [ ] `./gradlew check` passes.

## Out of scope

Authentication, generating clients, and documenting endpoints that don't exist yet.

---
**Claiming and submitting:** comment on this issue so it can be assigned to you. Sign off every commit (`git commit -s`), and see [CONTRIBUTING.md](https://github.com/mhnuk2007/finsim/blob/main/CONTRIBUTING.md).
"@
    }
    17 = @{
        Title = "Readable CLI output with a --json flag"
        Labels = @("good first issue", "finsim-cli")
        Body = @"
## Context

`finsim institutions`, `finsim networks`, `finsim register` and `finsim join` currently print the raw JSON FinSim returns. That's fine for scripts but hard to read in a terminal. We want readable output by default and raw JSON on request.

## What to build

- **Default output (human):**
  - `finsim institutions`: a table with columns `ID`, `TYPE`, `STATUS`, `BASE URL`, `CAPABILITIES`.
  - `finsim institutions --id bank-a`: a short key/value view.
  - `finsim networks`: a table with `ID`, `NAME`, `CURRENCIES`, `MEMBERS`.
  - `finsim register` and `finsim join`: a one-line confirmation (e.g. `Registered bank-a (created)`).
- **`--json` / `-j`:** prints exactly the JSON body FinSim returned, with nothing else on stdout, so it can be piped to `jq`.
- Errors still go to stderr and exit code `1`, as today.

## Hints

- The flag belongs in the shared `Connection` mixin in `FinSimCli.java` so every API command gets it.
- The CLI currently has no JSON library. Add a small, well-maintained one (for example Jackson, managed by the Spring Boot BOM) and keep the dependency footprint light.
- `FinSimCliTest` already has a stub HTTP server; extend it.

## Acceptance criteria

- [ ] `finsim institutions` prints an aligned table; `finsim institutions --json` prints valid, unmodified JSON.
- [ ] Same for `networks`; `register` and `join` print a one-line confirmation by default and raw JSON with `--json`.
- [ ] Tests cover default output and `--json` for each command, including empty lists.
- [ ] `./gradlew :finsim-cli:test` and `./gradlew spotlessCheck` pass.

## Out of scope

Colors, pagination, and the placeholder commands (`verify`, `transfer`, `trace`, `fault`).

---
**Claiming and submitting:** comment on this issue so it can be assigned to you. Sign off every commit (`git commit -s`), and see [CONTRIBUTING.md](https://github.com/mhnuk2007/finsim/blob/main/CONTRIBUTING.md).
"@
    }
    18 = @{
        Title = "Property-based tests for Money"
        Labels = @("good first issue", "finsim-core", "testing")
        Body = @"
## Context

`Money` (in `finsim-core`) is the exact-decimal amount-plus-currency type used across FinSim. Example-based tests check a few hand-picked numbers. Property-based tests check rules that must hold for thousands of generated inputs.

## What to build

Add property-based tests (using [jqwik](https://jqwik.net)) in `finsim-core/src/test/java/io/github/mhnuk2007/finsim/core/`. Read `Money.java` first to see the current API (`Money.of`, `plus`, `minus`, and its validation rules):

- **Commutativity:** `a.plus(b)` equals `b.plus(a)`.
- **Associativity:** `(a.plus(b)).plus(c)` equals `a.plus(b.plus(c))`.
- **Identity:** adding zero (`Money.of("0", cur)`) leaves the amount unchanged.
- **Round trip:** `(a.plus(b)).minus(b)` equals `a`.
- **Currency isolation:** adding or subtracting different currencies always throws `IllegalArgumentException`.
- **Non-negativity:** a negative amount never constructs; `minus` that would go below zero throws.
- **Precision:** amounts with more than 8 decimal places are rejected; amounts with up to 8 survive a trip through `toPlainString` and `Money.of` unchanged.

## Hints

- Compare amounts with `compareTo` (AssertJ `isEqualByComparingTo`), not `equals`.
- Add jqwik to `gradle/libs.versions.toml` and to `finsim-core`'s test dependencies.
- No change to `Money.java` in this PR (report bugs separately).

## Acceptance criteria

- [ ] All properties above are implemented and pass, each running at least 1000 tries.
- [ ] `./gradlew :finsim-core:test` and `./gradlew spotlessCheck` pass.

## Out of scope

Property tests for transfers or the state machine.

---
**Claiming and submitting:** comment on this issue so it can be assigned to you. Sign off every commit (`git commit -s`), and see [CONTRIBUTING.md](https://github.com/mhnuk2007/finsim/blob/main/CONTRIBUTING.md).
"@
    }
    19 = @{
        Title = "Dockerfiles and full Docker Compose stack"
        Labels = @("devops", "milestone-1")
        Body = @"
## Goal

`docker compose -f deploy/docker-compose.yml up --build` starts PostgreSQL, FinSim, and both sample banks with no local Java needed. This is what makes the five-minute quickstart possible.

*Note: Maintained directly by core team on the Milestone 1 critical path.*

## Scope

- Multi-stage `Dockerfile` for `finsim-api` (JDK 21 build stage using Gradle wrapper, JRE 21 runtime stage), plus `.dockerignore`.
- Multi-stage `Dockerfile` for `samples/bank-java`. The second bank is the **same image** run with `SPRING_PROFILES_ACTIVE=bank-b`.
- Extend `deploy/docker-compose.yml` with `finsim-api` (8080), `bank-a` (9001), and `bank-b` (9002), keeping `postgres` on 5432.
- A `docker` Spring profile for `finsim-api` (`application-docker.yml`) that points datasource at `postgres:5432` and sets seed URLs to `http://bank-a:9001` and `http://bank-b:9002`.
- Healthchecks using `wget -qO- http://localhost:8080/actuator/health` (Alpine JRE has `wget`, not `curl`).
- Update `docs/quickstart.md` once working.

## Acceptance criteria

- [ ] `docker compose -f deploy/docker-compose.yml up --build` brings everything up from clean checkout.
- [ ] `curl http://localhost:8080/actuator/health` and `curl http://localhost:9001/finsim/v1/institution/health` report UP.
- [ ] `curl http://localhost:8080/api/v1/networks/sandbox` lists `bank-a` and `bank-b`.
- [ ] `scripts/conformance.sh http://localhost:9001` and `http://localhost:9002` both pass against containers.
"@
    }
    20 = @{
        Title = "Pre-commit and commit-msg hooks with a verify script"
        Labels = @("good first issue", "tooling", "developer-experience")
        Body = @"
## Context

CI fails pull requests for unformatted code (`spotlessCheck`), failing tests, and commits without a DCO sign-off (`Signed-off-by:`). Contributors should find out before they push. Git for Windows ships Bash, so one set of POSIX shell scripts works on Linux, macOS, and Windows.

## What to build

1. **`scripts/verify.sh`**: runs `./gradlew spotlessCheck` and `./gradlew test` (with a `--fast` flag that skips the tests), then checks that every commit on the current branch that isn't on `origin/main` has a `Signed-off-by:` line. On each failure, print the command that fixes it (`Run ./gradlew spotlessApply` or `Run git commit --amend -s`).
2. **`.githooks/pre-commit`**: runs `./gradlew spotlessCheck`.
3. **`.githooks/commit-msg`**: reads the commit message file (`$1`) and rejects it if there's no `Signed-off-by:` line, telling the author to use `git commit -s`. Merge commits may be skipped.
4. **`CONTRIBUTING.md`**: explain how to enable the hooks with `git config core.hooksPath .githooks`, and mention `scripts/verify.sh`.

## Acceptance criteria

- [ ] With hooks enabled, committing unformatted Java is blocked with a clear fix command.
- [ ] A commit without a sign-off is rejected by `commit-msg`.
- [ ] `scripts/verify.sh` passes on a clean branch and fails with helpful messages otherwise.
- [ ] `CONTRIBUTING.md` explains how to enable and bypass the hooks (`--no-verify`).

---
**Claiming and submitting:** comment on this issue so it can be assigned to you. Sign off every commit (`git commit -s`), and see [CONTRIBUTING.md](https://github.com/mhnuk2007/finsim/blob/main/CONTRIBUTING.md).
"@
    }
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "FinSim GitHub Issues Updater ($Repo)" -ForegroundColor Cyan
Write-Host "Total issues to update: $($issueUpdates.Count)" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

if (-not $Apply) {
    Write-Host "`nDRY RUN MODE: No live issues will be modified." -ForegroundColor Yellow
    Write-Host "To update live GitHub issues, run with -Apply and -Token <token> (or set `$env:GITHUB_TOKEN).`n" -ForegroundColor Yellow
    foreach ($num in ($issueUpdates.Keys | Sort-Object)) {
        $item = $issueUpdates[$num]
        Write-Host "• Issue #$num -> $($item.Title)" -ForegroundColor Green
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
    "User-Agent"    = "FinSim-Issue-Updater"
}

foreach ($num in ($issueUpdates.Keys | Sort-Object)) {
    $item = $issueUpdates[$num]
    Write-Host "Updating issue #$num ($($item.Title))... " -NoNewline
    $payload = @{
        title  = $item.Title
        labels = $item.Labels
        body   = $item.Body
    } | ConvertTo-Json -Depth 5

    try {
        $uri = "https://api.github.com/repos/$Repo/issues/$num"
        $response = Invoke-RestMethod -Uri $uri -Method Patch -Headers $headers -Body $payload
        Write-Host "SUCCESS! -> $($response.html_url)" -ForegroundColor Green
    }
    catch {
        Write-Host "FAILED!" -ForegroundColor Red
        Write-Error $_.Exception.Message
    }
}
