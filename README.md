# FinSim

**An open-source simulator for financial networks: connect independent services, run realistic payment flows, and break them on purpose.**

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-pre--alpha-orange)](docs/roadmap.md)

> **Status: pre-alpha.** FinSim is in its design phase. The architecture is documented; the code is being built milestone by milestone. This README only claims what exists. Everything else lives in the [roadmap](docs/roadmap.md).

## What is FinSim?

Most payment sandboxes simulate one provider. FinSim simulates the *network*: independently built services (a bank, a merchant, a gateway) talk to each other through one versioned contract, and FinSim orchestrates the payment, injects failures, and shows you the full trace.

You write the institution, in any language. FinSim connects it, tests it, and shows what happened.

**Everything in FinSim is synthetic.** It never holds real money, never talks to real payment networks, and labels every simulated entity `SYNTHETIC`.

## Why?

Timeouts, duplicate requests, and uncertain outcomes are the hardest parts of payment engineering, and the parts most sandboxes hide. FinSim makes them reproducible:

- **`UNKNOWN` is a real outcome.** A timeout never turns into "failed". Retries are blocked until the outcome is resolved.
- **Idempotency is enforced.** Duplicate requests never create a second financial effect.
- **Fault injection.** Delays, timeouts, and duplicate deliveries on demand.
- **Full trace.** Every stage, call, and fault in one view.

## Status

| Capability | Status |
|---|---|
| Design: architecture spec, requirements, contract sketch | Done (see `docs/`) |
| Build scaffold: Gradle modules, CI, module-boundary checks, CLI skeleton | Done (initial commit) |
| Milestone 1: two sample banks, `finsim/v1` minimal contract, payment state machine with `UNKNOWN`, fault injection, `finsim trace`, `finsim verify` | In progress |
| Milestone 2: card authorize/capture/refund, virtual clock, ledger | Planned |
| Milestone 3: disputes, reconciliation, scenario replay | Planned |
| Clearing, RTGS, FX, screening, ISO 20022, billers, fees | Roadmap (help wanted) |

## Quickstart (target experience for milestone 1)

Full walkthrough: [docs/quickstart.md](docs/quickstart.md).

> These commands describe what milestone 1 will deliver. They will work when the milestone is marked done.

```bash
git clone https://github.com/mhnuk2007/finsim.git
cd finsim
docker compose -f deploy/docker-compose.yml up

# in another terminal
finsim transfer --network sandbox --from bank-a:ACC-1 --to bank-b:ACC-2 --amount 100.00 --currency USD
finsim trace <transactionId>

# inject a timeout on bank-b and try again
finsim fault add --target bank-b --type TIMEOUT
finsim transfer ...        # ends in UNKNOWN, not FAILED
```

## Core concepts

- **Institution:** an independently owned service (bank, merchant, gateway) that implements the `finsim/v1` contract.
- **Network:** a controlled environment where registered institutions can transact.
- **Scheme:** the rules a network applies (currencies, limits, workflow).
- **Contract (`finsim/v1`):** the versioned HTTP/JSON API an institution implements. See [docs/contract-v1.md](docs/contract-v1.md) and [contract/finsim-v1.openapi.yaml](contract/finsim-v1.openapi.yaml).

## What FinSim is not

- Not a real-money system, payment network, or bank.
- Not connected to real sanctions lists, FX feeds, or regulators.
- Not a certification of conformance to any real payment scheme.
- Not a place where uploaded code runs inside the FinSim API process.

## Contributing

Contributions are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md) and the [good first issues](https://github.com/mhnuk2007/finsim/labels/good%20first%20issue). Project decisions are described in [GOVERNANCE.md](GOVERNANCE.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
