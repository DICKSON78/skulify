-- The card UID is an identifier, not a secret. Keep a display label alongside
-- its hash so the Pocket Money UI can show the same value the sensor reads.
ALTER TABLE pm_cards ADD COLUMN uid_label VARCHAR(60);

-- Sensor receipts show the wallet balance immediately after the debit.
ALTER TABLE pm_purchases ADD COLUMN balance_after BIGINT;
