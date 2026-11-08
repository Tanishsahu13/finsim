-- Base migration for FinSim PostgreSQL schema
-- Every core table includes the common columns: id, created_at, updated_at, version

CREATE TABLE institutions (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    base_url VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    capabilities VARCHAR(256) NOT NULL,
    currencies VARCHAR(128) NOT NULL,
    provenance VARCHAR(32) NOT NULL DEFAULT 'SYNTHETIC',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transfers (
    id VARCHAR(64) PRIMARY KEY,
    network_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    state VARCHAR(32) NOT NULL,
    current_stage VARCHAR(64),
    from_institution VARCHAR(64) NOT NULL,
    from_account VARCHAR(64) NOT NULL,
    to_institution VARCHAR(64) NOT NULL,
    to_account VARCHAR(64) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reference VARCHAR(140),
    reason_code VARCHAR(32),
    emitted_by VARCHAR(32),
    pending_action VARCHAR(32),
    needs_manual_review BOOLEAN NOT NULL DEFAULT FALSE,
    provenance VARCHAR(32) NOT NULL DEFAULT 'SYNTHETIC',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transfer_stages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transfer_id VARCHAR(64) NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    stage VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transfer_stages_transfer_id ON transfer_stages(transfer_id);

CREATE TABLE fault_rules (
    id VARCHAR(64) PRIMARY KEY,
    target_institution VARCHAR(64) NOT NULL,
    fault_type VARCHAR(32) NOT NULL,
    delay_ms INTEGER,
    probability DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    remaining_count INTEGER,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
