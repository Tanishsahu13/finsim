# Database Design

Every core table includes the common columns: `id`, `created_at`, `updated_at`, and `version`.

## Registry tables and identifiers

The registry tables (`registry_institutions`, `registry_networks`, and their child tables) use the human-chosen slug (for example `bank-a`, `sandbox`) as the primary key instead of a generated UUID, because that slug is the public identifier used in APIs and the CLI. Slugs are 2 to 63 characters of lowercase letters, digits, and hyphens. Every other convention above still applies, and Flyway migrations live with the module that owns the tables (`finsim-registry/src/main/resources/db/migration`). Version numbers are global across modules.

