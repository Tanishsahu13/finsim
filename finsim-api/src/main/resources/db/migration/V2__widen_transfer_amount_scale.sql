-- Widen transfers.amount from NUMERIC(18, 4) to NUMERIC(22, 8) so persisted amounts keep
-- the 8 decimal places the Money domain model and the OpenAPI contract accept (issue #46).
-- NUMERIC(22, 8) preserves the existing 14-digit integer capacity.
ALTER TABLE transfers
    ALTER COLUMN amount TYPE NUMERIC(22, 8);
