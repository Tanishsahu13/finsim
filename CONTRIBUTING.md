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

1. **Find or open an issue.** For anything larger than a small fix, comment first so work isn't duplicated.
2. **Fork, branch, and make your change.** Keep pull requests focused: one concern per PR.
3. **Add tests.** Bug fixes need a test that fails without the fix. Anything touching payments, idempotency, or state transitions needs a property-based or scenario test that proves no duplicate or lost financial effect.
4. **Sign off your commits** with `git commit -s` (see DCO below).
5. **Open a pull request** and fill in the template. CI must pass.

## Developer Certificate of Origin (DCO)

We do not require a CLA. Instead, every commit must carry a `Signed-off-by: Your Name <you@example.com>` line, added by `git commit -s`. This certifies that you wrote the change or have the right to submit it under the project license (Apache 2.0). The full text is at <https://developercertificate.org>.

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
| `good first issue` | Small, well-scoped, mentored (see [docs/good-first-issues.md](docs/good-first-issues.md)) |
| `help wanted` | Maintainers would love a contributor here |
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
