# Quickstart

> **Status: not yet runnable.** FinSim is pre-alpha. This page describes the experience milestone 1 will deliver, so contributors know the target. It will be updated, and this banner removed, when the milestone is done. Track progress in the [milestone 1 issues](milestone-1-issues.md).

## Prerequisites

- Docker and Docker Compose
- Java 21 (to build from source)
- Node.js 20+ (for the Node sample bank)

## 1. Start a local network

```bash
git clone https://github.com/mhnuk2007/finsim.git
cd finsim
docker compose -f deploy/docker-compose.yml up
```

This will start FinSim, PostgreSQL, two sample banks (`bank-a` in Java, `bank-b` in Node), and a seeded `sandbox` network. All data is synthetic.

## 2. Check a bank against the contract

```bash
finsim verify --url http://localhost:9001
```

Each check (health, capabilities, debit and credit, idempotent replay, idempotency conflict, status query, error shape) reports pass or fail, with how to fix failures.

## 3. Make a transfer

```bash
finsim transfer \
  --network sandbox \
  --from bank-a:ACC-1 --to bank-b:ACC-2 \
  --amount 100.00 --currency USD \
  --reference DEMO-001

finsim trace <transactionId>
```

The trace lists each stage, each call to a bank, and the final state.

## 4. Break it on purpose

```bash
finsim fault add --target bank-b --type TIMEOUT
finsim transfer --network sandbox --from bank-a:ACC-1 --to bank-b:ACC-2 --amount 50.00 --currency USD --reference DEMO-002
```

The transfer ends in `UNKNOWN`, not `REJECTED`. Retrying is blocked until you resolve it:

```bash
finsim fault remove <faultId>
finsim resolve <transactionId>
finsim trace <transactionId>
```

## 5. Connect your own institution

Implement the four participant endpoints from the [contract](contract-v1.md) in any language, run `finsim verify --url <your-url>` until it passes, then register it:

```bash
finsim register --id my-bank --type BANK --url http://host.docker.internal:9100 \
  --capabilities ACCOUNT_DEBIT,ACCOUNT_CREDIT,STATUS_QUERY
```

## What's next

See the [roadmap](roadmap.md) for what's planned after milestone 1, and [CONTRIBUTING.md](../CONTRIBUTING.md) to help build it.
