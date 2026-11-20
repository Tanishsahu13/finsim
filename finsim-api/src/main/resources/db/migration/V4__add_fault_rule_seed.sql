-- Add seed column to fault_rules to support deterministic probabilistic fault injection (issue #22).
ALTER TABLE fault_rules
    ADD COLUMN seed BIGINT;
