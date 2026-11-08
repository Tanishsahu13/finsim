## What does this change?

<!-- One or two sentences. Link the issue: Closes #123 -->

## Why?

<!-- The problem this solves or the behavior it adds. -->

## How was it tested?

<!-- Tests added or run. For anything touching payments, idempotency, or state transitions, name the property-based or scenario test. -->

## Checklist

- [ ] Commits are signed off (`git commit -s`)
- [ ] `./gradlew build` passes locally
- [ ] Tests added or updated
- [ ] Docs updated (README status table, contract docs, or roadmap if behavior changed)
- [ ] ADR added if this changes the contract, a state machine, a module boundary, or a locked rule
- [ ] Simulated entities expose `"provenance": "SYNTHETIC"`
- [ ] No secrets, real card numbers, or personal data in code, tests, or examples
