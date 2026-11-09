# Quickstart

> **Status: runnable platform baseline (milestone 1 in active progress).**
> The database, FinSim platform API, institution registry, payment transfer orchestration (`POST /api/v1/networks/{networkId}/transfers`), reconciliation resolution (`POST /api/v1/transfers/{id}/resolve`), CLI commands (`institutions`, `networks`, `register`, `join`), and Java sample banks (`bank-a`, `bank-b`) are **runnable today**. Fault injection (#22), trace recording (#23), chaos testing (#24), and native CLI verification (#25) are in active development.
> Track detailed issue progress in [milestone-1-issues.md](milestone-1-issues.md).

---

## Prerequisites

- **Docker and Docker Compose** (for PostgreSQL)
- **Java 21+** (for building FinSim and the sample bank from source)
- **Git**

---

## 1. Start the Database and FinSim API

FinSim connects to a PostgreSQL 17 database. You can start the database using Docker Compose or let Testcontainers manage an ephemeral container automatically.

### Option A: Using Docker Compose (Standard)

```bash
# 1. Start PostgreSQL 17 container on port 5433
docker compose -f deploy/docker-compose.yml up -d

# 2. Start the FinSim API (runs on http://localhost:8080)
./gradlew :finsim-api:bootRun
```

### Option B: Using Testcontainers (No pre-existing DB needed)

```bash
# Starts FinSim API attached to an ephemeral Testcontainers PostgreSQL 17 container
./gradlew :finsim-api:bootTestRun
```

Verify that FinSim is healthy:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

On startup, Flyway automatically runs database migrations (`V1__baseline.sql`, `V2__registry.sql`), creates the `sandbox` network (USD), and seeds member institutions `bank-a` (`http://localhost:9001`) and `bank-b` (`http://localhost:9002`).

---

## 2. Start the Sample Banks

FinSim includes an in-memory sample bank implementing the `finsim/v1` participant contract in `samples/bank-java`.

In a new terminal, start **Bank A** on port 9001:

```bash
./gradlew :sample-bank-java:bootRun
```

In another terminal, start **Bank B** on port 9002:

```bash
./gradlew :sample-bank-java:bootRun --args="--spring.profiles.active=bank-b"
```

Verify their participant contract endpoints:

```bash
# Health & versioning
curl http://localhost:9001/finsim/v1/institution/health
curl http://localhost:9002/finsim/v1/institution/health

# Capabilities
curl http://localhost:9001/finsim/v1/institution/capabilities

# Inspect initial synthetic balances
curl http://localhost:9001/sample/accounts
curl http://localhost:9002/sample/accounts
```

You can even test a direct idempotent payment leg against Bank A:

```bash
curl -i -X POST http://localhost:9001/finsim/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-0001" \
  -d '{"transactionId":"tx-demo-1","direction":"DEBIT","accountReference":"ACC-1","money":{"amount":"100.00","currency":"USD"},"reference":"DEMO-001","provenance":"SYNTHETIC"}'
```

---

## 3. Interact with the Registry via the CLI

FinSim includes a CLI to manage institutions and networks. Build the CLI distribution:

```bash
./gradlew :finsim-cli:installDist
```

On Linux/macOS:
```bash
alias finsim="$(pwd)/finsim-cli/build/install/finsim/bin/finsim"
```

On Windows (PowerShell):
```powershell
Set-Alias finsim "$PWD\finsim-cli\build\install\finsim\bin\finsim.bat"
```

*(Alternatively, run commands directly via `./gradlew :finsim-cli:run --args="<command>"`)*

### List Seeded Institutions and Networks

```bash
# View institutions registered in the system
finsim institutions

# View details for a single institution
finsim institutions --id bank-a

# View the seeded sandbox network and its members
finsim networks --id sandbox
```

### Register and Join a Custom Institution

```bash
# Register a custom merchant service
finsim register --id merchant-x --name "Merchant X" --base-url http://localhost:9100 \
  --capabilities ACCOUNT_CREDIT

# Join it to the sandbox network
finsim join --network sandbox --institution merchant-x

# Verify updated network membership
finsim networks --id sandbox
```

---

## 4. Transfer Execution & Fault Injection *(Target Milestone 1 Experience)*

The following capabilities represent the target end-to-end payment workflow currently being assembled:

### Transfer orchestration

```bash
finsim transfer \
  --network sandbox \
  --from bank-a:ACC-1 --to bank-b:ACC-2 \
  --amount 100.00 --currency USD \
  --reference DEMO-001

finsim trace <transactionId>
```

FinSim orchestrates the debit at `bank-a` and credit at `bank-b` using stage-derived idempotency keys, updating the transfer aggregate state from `CREATED` to `COMPLETED`.

### Injecting Faults and Handling `UNKNOWN`

```bash
# Simulate a timeout on Bank B
finsim fault add --target bank-b --type TIMEOUT

# Attempt transfer: ends in UNKNOWN, not REJECTED
finsim transfer --network sandbox --from bank-a:ACC-1 --to bank-b:ACC-2 --amount 50.00 --currency USD --reference DEMO-002

# Inspect the trace: shows debit succeeded, credit timed out, state is UNKNOWN
finsim trace <transactionId>

# Retrying while UNKNOWN is blocked with 409 RECONCILIATION_REQUIRED
# Remove the fault and resolve the transfer via participant status query:
finsim fault remove <faultId>
finsim resolve <transactionId>
finsim trace <transactionId>
```

---

## 5. Connect Your Own Institution

You can implement the `finsim/v1` participant contract in any language:
1. Implement the four endpoints described in [contract-v1.md](contract-v1.md):
   - `GET /finsim/v1/institution/health`
   - `GET /finsim/v1/institution/capabilities`
   - `POST /finsim/v1/payments`
   - `GET /finsim/v1/payments/{transactionId}?leg=...`
2. Register your institution using `finsim register` or `POST /api/v1/institutions`.
3. Add it to a network using `finsim join` or `POST /api/v1/networks/{networkId}/members`.

---

## What's Next

- See [registry.md](registry.md) for details on registry APIs and SSRF URL protection.
- See [database.md](database.md) for database schema, Flyway migrations, and Testcontainers setup.
- See [roadmap.md](roadmap.md) for the long-term roadmap beyond Milestone 1.
- See [CONTRIBUTING.md](../CONTRIBUTING.md) to contribute code or sample banks.
