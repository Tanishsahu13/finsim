# Database Architecture and Schema

FinSim uses **PostgreSQL 17** exclusively across all environments. There is no H2 or in-memory dialect fallback in tests or local development, guaranteeing exact SQL dialect parity, constraint enforcement, and transaction semantics everywhere.

---

## 1. Core Principles

- **Schema Ownership via Flyway**: Flyway manages all database migrations across modules. Spring Data JPA / Hibernate runs with `ddl-auto: validate`, ensuring startup immediately fails if JPA entity mappings drift from the migrated schema.
- **Multi-Module Migration Structure**: Flyway migrations are partitioned by the module owning the underlying domain:
  - `finsim-api/src/main/resources/db/migration/V1__baseline.sql`: platform foundation tables (`transfers`, `transfer_stages`, `fault_rules`, legacy baseline).
  - `finsim-registry/src/main/resources/db/migration/V2__registry.sql`: registry tables (`registry_institutions`, `registry_networks`, and relationships).
  - Version numbering is globally sequential (`V1`, `V2`, ...) across all modules.
- **Common Audit and Concurrency Columns**: Every persistent entity table maintains:
  - `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `version BIGINT NOT NULL DEFAULT 0` for optimistic locking to prevent lost concurrent updates.
- **Strict Provenance Guard**: Tables storing financial or institutional records include a `provenance` column constrained to `'SYNTHETIC'` (`CHECK (provenance = 'SYNTHETIC')`). This prevents synthetic simulation data from ever being confused with live production data.
- **Deterministic Time**: Aggregates such as `Transfer` never call system clock lookups directly (`Instant.now()`). Timestamped operations accept an explicit `Instant` or `Clock`, guaranteeing exact replayability and compatibility with scenario time travelers and virtual clocks.
- **Slugs vs. UUIDs**: Core financial transactions (`transfers`) and fault configurations (`fault_rules`) use generated identifiers (`VARCHAR(64)`), while registry entities use human-readable slugs (e.g., `bank-a`, `sandbox`) checked with `^[a-z0-9][a-z0-9-]{1,62}$` because they serve as public IDs in CLI commands, configuration files, and REST endpoints.

---

## 2. Table Definitions

### Platform Foundation (`V1__baseline.sql`)

#### `transfers`
The central aggregate root for payment transfers.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `VARCHAR(64)` | `PRIMARY KEY` | Transfer transaction identifier (e.g. `tx-01...`) |
| `network_id` | `VARCHAR(64)` | `NOT NULL` | The network where this transfer executes (e.g. `sandbox`) |
| `idempotency_key` | `VARCHAR(128)` | `NOT NULL UNIQUE` | Client-provided idempotency key for network API mutations |
| `state` | `VARCHAR(32)` | `NOT NULL` | Aggregate state (`CREATED`, `ACCEPTED`, `IN_PROGRESS`, `COMPLETED`, `REJECTED`, `UNKNOWN`) |
| `current_stage` | `VARCHAR(64)` | Nullable | Current stage name (e.g. `DEBIT_SOURCE`, `CREDIT_DESTINATION`) |
| `from_institution` | `VARCHAR(64)` | `NOT NULL` | Debited institution slug (`bank-a`) |
| `from_account` | `VARCHAR(64)` | `NOT NULL` | Debited account reference (`ACC-1`) |
| `to_institution` | `VARCHAR(64)` | `NOT NULL` | Credited institution slug (`bank-b`) |
| `to_account` | `VARCHAR(64)` | `NOT NULL` | Credited account reference (`ACC-2`) |
| `amount` | `NUMERIC(18, 4)` | `NOT NULL` | Precise monetary amount |
| `currency` | `VARCHAR(3)` | `NOT NULL` | ISO 4217 three-letter currency code (e.g. `USD`) |
| `reference` | `VARCHAR(140)` | Nullable | Client or payment reference |
| `reason_code` | `VARCHAR(32)` | Nullable | Rejection reason code (e.g. `AM04`, `AC01`, `DUPL`) |
| `emitted_by` | `VARCHAR(32)` | Nullable | Emitter of rejection (`SOURCE`, `DESTINATION`, `FINSIM`) |
| `pending_action` | `VARCHAR(32)` | Nullable | Action required to resolve `UNKNOWN` state |
| `needs_manual_review` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` | Flag set if resolution / reversal fails and requires intervention |
| `provenance` | `VARCHAR(32)` | `NOT NULL DEFAULT 'SYNTHETIC'` | Safety provenance indicator |
| `version` | `BIGINT` | `NOT NULL DEFAULT 0` | Optimistic locking version |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Last updated timestamp |

#### `transfer_stages`
Append-only log of every state transition and workflow stage executed for a transfer.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Sequential event identifier |
| `transfer_id` | `VARCHAR(64)` | `NOT NULL REFERENCES transfers(id) ON DELETE CASCADE` | Associated transfer |
| `stage` | `VARCHAR(64)` | `NOT NULL` | Executed workflow stage |
| `state` | `VARCHAR(32)` | `NOT NULL` | State at this stage |
| `details` | `TEXT` | Nullable | Additional context, error messages, or participant response payload |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Timestamp of entry creation |

*Index*: `idx_transfer_stages_transfer_id` on `transfer_id`.

#### `fault_rules`
Simulation rules injected dynamically to simulate real-world network anomalies.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `VARCHAR(64)` | `PRIMARY KEY` | Unique rule identifier |
| `target_institution` | `VARCHAR(64)` | `NOT NULL` | Target institution slug to intercept |
| `fault_type` | `VARCHAR(32)` | `NOT NULL` | `DELAY`, `TIMEOUT`, `DUPLICATE_DELIVERY`, or `CORRUPT_RESPONSE` |
| `delay_ms` | `INTEGER` | Nullable | Milliseconds of simulated delay |
| `probability` | `DOUBLE PRECISION` | `NOT NULL DEFAULT 1.0` | Probability between 0.0 and 1.0 |
| `remaining_count` | `INTEGER` | Nullable | Executions remaining before expiration (null = infinite) |
| `version` | `BIGINT` | `NOT NULL DEFAULT 0` | Optimistic locking version |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Last updated timestamp |

---

### Registry Domain (`V2__registry.sql`)

#### `registry_institutions`
Institutions registered on the FinSim platform.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `institution_id` | `TEXT` | `PRIMARY KEY CHECK (institution_id ~ '^[a-z0-9][a-z0-9-]{1,62}$')` | Human-readable institution slug |
| `name` | `TEXT` | `NOT NULL` | Descriptive institution name |
| `institution_type` | `TEXT` | `NOT NULL CHECK (institution_type IN ('BANK', 'MERCHANT', 'PAYMENT_GATEWAY', 'CUSTOM'))` | Role of institution |
| `base_url` | `TEXT` | `NOT NULL` | HTTP base URL of participant contract API |
| `status` | `TEXT` | `NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED'))` | Institution operational state |
| `provenance` | `TEXT` | `NOT NULL DEFAULT 'SYNTHETIC' CHECK (provenance = 'SYNTHETIC')` | Synthetic verification marker |
| `version` | `BIGINT` | `NOT NULL DEFAULT 0` | Optimistic locking version |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Registration timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Last update timestamp |

#### `registry_institution_capabilities`
Normalized table of participant capabilities declared by registered institutions.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `institution_id` | `TEXT` | `NOT NULL REFERENCES registry_institutions(institution_id) ON DELETE CASCADE` | Associated institution |
| `capability` | `TEXT` | `NOT NULL CHECK (capability IN ('ACCOUNT_DEBIT', 'ACCOUNT_CREDIT', 'STATUS_QUERY'))` | Contract capability |

*Primary Key*: `(institution_id, capability)`.

#### `registry_networks`
Isolated network environments (e.g. `sandbox`).

| Column | Type | Constraints | Description |
|---|---|---|---|
| `network_id` | `TEXT` | `PRIMARY KEY CHECK (network_id ~ '^[a-z0-9][a-z0-9-]{1,62}$')` | Unique network slug |
| `name` | `TEXT` | `NOT NULL` | Descriptive network name |
| `provenance` | `TEXT` | `NOT NULL DEFAULT 'SYNTHETIC' CHECK (provenance = 'SYNTHETIC')` | Synthetic verification marker |
| `version` | `BIGINT` | `NOT NULL DEFAULT 0` | Optimistic locking version |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Last update timestamp |

#### `registry_network_currencies`
Currencies supported by a network.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `network_id` | `TEXT` | `NOT NULL REFERENCES registry_networks(network_id) ON DELETE CASCADE` | Associated network |
| `currency` | `TEXT` | `NOT NULL CHECK (currency ~ '^[A-Z]{3}$')` | ISO 4217 currency code |

*Primary Key*: `(network_id, currency)`.

#### `registry_network_members`
Mapping of institutions to the networks they belong to.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `network_id` | `TEXT` | `NOT NULL REFERENCES registry_networks(network_id) ON DELETE CASCADE` | Network |
| `institution_id` | `TEXT` | `NOT NULL REFERENCES registry_institutions(institution_id)` | Member institution |
| `joined_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Timestamp when institution joined |

*Primary Key*: `(network_id, institution_id)`.

---

## 3. Database Execution & Testing

### Local Development via Docker Compose
For running the standalone database locally:
```bash
docker compose -f deploy/docker-compose.yml up -d
```
This spins up PostgreSQL 17 on host port **`5433`** (mapped from container port `5432` to avoid conflicting with any default local PostgreSQL instance on `5432`). Default credentials:
- Database: `finsim`
- User: `finsim`
- Password: `finsim`
- URL: `jdbc:postgresql://localhost:5433/finsim`

### Automated Tests via Testcontainers
Integration tests (e.g., in `finsim-api`) do not require a manually running database. Instead, they use Spring Boot's `@ServiceConnection` and Testcontainers:

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>("postgres:17-alpine");
  }
}
```

This dynamically spins up a fresh, isolated PostgreSQL 17 container, applies all Flyway migrations (`V1`, `V2`), runs the test suite, and cleanly tears down the container afterward.

### Running with Testcontainers Interactively
You can run the full API application against an ephemeral Testcontainers database without Docker Compose:
```bash
./gradlew :finsim-api:bootTestRun
```
This starts the Spring Boot application on `http://localhost:8080` backed by a temporary Testcontainers PostgreSQL 17 instance.
