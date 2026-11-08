-- Registry: institutions, networks, network membership.
-- Registry entities use the human-chosen slug (for example "bank-a") as primary key because it is
-- the public identifier used in APIs and the CLI. See docs/database.md.

CREATE TABLE registry_institutions (
    institution_id   text        PRIMARY KEY CHECK (institution_id ~ '^[a-z0-9][a-z0-9-]{1,62}$'),
    name             text        NOT NULL,
    institution_type text        NOT NULL CHECK (institution_type IN ('BANK', 'MERCHANT', 'PAYMENT_GATEWAY', 'CUSTOM')),
    base_url         text        NOT NULL,
    status           text        NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    provenance       text        NOT NULL DEFAULT 'SYNTHETIC' CHECK (provenance = 'SYNTHETIC'),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    version          bigint      NOT NULL DEFAULT 0
);

CREATE TABLE registry_institution_capabilities (
    institution_id text NOT NULL REFERENCES registry_institutions (institution_id) ON DELETE CASCADE,
    capability     text NOT NULL CHECK (capability IN ('ACCOUNT_DEBIT', 'ACCOUNT_CREDIT', 'STATUS_QUERY')),
    PRIMARY KEY (institution_id, capability)
);

CREATE TABLE registry_networks (
    network_id text        PRIMARY KEY CHECK (network_id ~ '^[a-z0-9][a-z0-9-]{1,62}$'),
    name       text        NOT NULL,
    provenance text        NOT NULL DEFAULT 'SYNTHETIC' CHECK (provenance = 'SYNTHETIC'),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version    bigint      NOT NULL DEFAULT 0
);

CREATE TABLE registry_network_currencies (
    network_id text NOT NULL REFERENCES registry_networks (network_id) ON DELETE CASCADE,
    currency   text NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    PRIMARY KEY (network_id, currency)
);

CREATE TABLE registry_network_members (
    network_id     text        NOT NULL REFERENCES registry_networks (network_id) ON DELETE CASCADE,
    institution_id text        NOT NULL REFERENCES registry_institutions (institution_id),
    joined_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (network_id, institution_id)
);
