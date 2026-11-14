# Contributing to FinSim

Thanks for your interest. FinSim is a young project, so early contributions have an outsized effect. This guide keeps the process light.

## Ways to contribute

- **Try it and report what broke.** A clear bug report with steps is valuable.
- **Write a sample institution** in a language we don't have yet (Python, Go, .NET...). This proves the contract is language-agnostic.
- **Add a fault type** (for example slow response, partial failure, malformed response).
- **Pick up a roadmap item** labeled `help wanted`.
- **Improve docs**: quickstart gaps, unclear contract text, typos.

## Getting set up

Prerequisites: Java 21, Docker and Docker Compose, Node.js 20+ (for the Node sample).

```bash
git clone https://github.com/mhnuk2007/finsim.git
cd finsim
./gradlew build                                   # build and unit tests
docker compose -f deploy/docker-compose.yml up    # run the local network
./gradlew test                                    # tests only
```

If any of these fail on a clean machine, that is a bug. Please open an issue.

## Making a change

1. **Find or open an issue.** Comment on any open issue labeled `good first issue` or `help wanted` stating you'd like to work on it, and a maintainer will assign it to you.
2. **10-day community reservation window:** To welcome community contributors while maintaining steady project velocity, open issues are reserved for community members for **10 days**. If an issue remains unclaimed after 10 days (or if an assigned contributor goes silent without an update), maintainers will step in to implement and resolve it. Need extra time? Just post a brief progress comment on the issue thread!
3. **Fork, branch, and make your change.** Keep pull requests focused: one concern per PR.
4. **Add tests.** Bug fixes need a test that fails without the fix. Anything touching payments, idempotency, or state transitions needs a property-based or scenario test that proves no duplicate or lost financial effect.
5. **Sign off your commits** with `git commit -s` (see DCO below).
6. **Open a pull request** and fill in the template. CI must pass.

## Developer Certificate of Origin (DCO)

We do not require a CLA. Instead, every commit must carry a `Signed-off-by: Your Name <you@example.com>` line, added by `git commit -s`. This certifies that you wrote the change or have the right to submit it under the project license (Apache 2.0). The full text is at <https://developercertificate.org>.

## Local verification

CI runs the same three checks on every pull request: a Spotless formatting check, the Gradle tests, and a DCO sign-off check on each commit. Run them locally before opening a pull request so avoidable CI failures are caught early.

```bash
# Linux/macOS/Git Bash
bash scripts/verify.sh

# Windows PowerShell
.\scripts\verify.ps1

# DCO-signed commit
git commit -s -m "Add feature"
```

The scripts run, in order:

1. **Formatting** — `./gradlew spotlessCheck` (or `.\gradlew.bat spotlessCheck` on Windows). If it fails, fix formatting with `./gradlew spotlessApply` and re-run.
2. **Tests** — `./gradlew test`. If tests fail, inspect the Gradle test output to find the failing test; do not hide or skip it.
3. **DCO sign-off** — checks that the latest commit carries a `Signed-off-by:` line. If it is missing, add it with `git commit --amend -s` (for the latest commit) or sign future commits with `git commit -s`.

A failing check stops the script with a non-zero exit code and a message saying what to do next.

### Optional pre-commit hook

A lightweight pre-commit hook is available in `.githooks/pre-commit`. It runs only the Spotless formatting check before each commit — it does not validate the DCO sign-off, because the commit message does not exist yet at pre-commit time. It is optional and off by default.

```bash
# Enable the hook
git config core.hooksPath .githooks

# Disable the hook
git config --unset core.hooksPath
```

## When you need an ADR

An **Architecture Decision Record** (in `docs/adr/`) is required for changes to:

- the `finsim/v1` contract (endpoints, fields, error codes, semantics),
- payment or other state machines (states or allowed transitions),
- module boundaries,
- any rule listed under "Locked rules" in `docs/roadmap.md`.

Copy `docs/adr/0000-template.md`, describe the context, the decision, and the alternatives you rejected, and open it as a PR. Everything else needs tests and a clear description, not an ADR.

## Code standards

- Java 21, Spring Boot. Formatting is enforced by the build (run `./gradlew spotlessApply`).
- Module boundaries are checked by ArchUnit. Don't access another module's repositories directly; use its public interface or events.
- Money is never a `double`. Use `BigDecimal` or integer minor units, with the currency attached.
- Every simulated entity exposed over the API carries `"provenance": "SYNTHETIC"`.
- No secrets in code or test fixtures.

## Issue labels

| Label | Meaning |
|---|---|
| [`good first issue`](https://github.com/mhnuk2007/finsim/labels/good%20first%20issue) | Small, well-scoped, mentored (see [docs/good-first-issues.md](docs/good-first-issues.md)) |
| [`help wanted`](https://github.com/mhnuk2007/finsim/labels/help%20wanted) | Intermediate community tasks (sample banks, tooling) |
| `roadmap` | Planned work from `docs/roadmap.md` |
| `needs-design` | Needs discussion or an ADR before coding |
| `bug`, `docs`, `question` | As expected |
| `milestone-1`, `milestone-2`... | Which milestone it belongs to |

## Review expectations

Maintainers aim to give a first response to every issue and pull request **within a few days**. If you haven't heard back after a week, ping the thread. Review feedback is about the code, not you. Small, focused PRs are reviewed faster.

## Security

Please do not open public issues for security vulnerabilities. Use GitHub's private vulnerability reporting: <https://github.com/mhnuk2007/finsim/security/advisories/new>. See [SECURITY.md](SECURITY.md). We'll acknowledge within a few days.

Because FinSim is a simulator, remember the boundary: no real credentials, card numbers, or personal data belong in issues, tests, or examples.

## Conduct

By participating you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md) (we use the Contributor Covenant 2.1).
