-- Transaction risk decisions use the same explicit confidence contract as scan results.
-- This is additive because V1 and V2 may already be applied in developer databases.
ALTER TABLE transactions
    ADD COLUMN confidence_level ENUM('LOW', 'MEDIUM', 'HIGH') NULL AFTER risk_classification;
