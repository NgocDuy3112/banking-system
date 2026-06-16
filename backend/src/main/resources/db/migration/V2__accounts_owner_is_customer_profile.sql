-- =============================================================================
-- V2: Switch account ownership from users -> customer_profiles
-- =============================================================================
-- The V1 shape (accounts.user_id) lets any user (TELLER/AUDITOR/ADMIN) be
-- assigned an account, which violates the "Chỉ Customer mới có" rule from
-- data-modeling.md. We swap the FK to customer_profiles so the data model
-- itself enforces role separation: a customer_profile is the only path to
-- account ownership, and a customer_profile belongs to exactly one user.
--
-- Backfill is defensive: V1 has no accounts in production, but if any pre-
-- existing rows exist, they are linked to the matching customer_profiles row
-- via customer_profiles.user_id.
-- =============================================================================

ALTER TABLE accounts
    ADD COLUMN customer_profile_id UUID;

UPDATE accounts a
SET customer_profile_id = cp.id
FROM customer_profiles cp
WHERE cp.user_id = a.user_id;

ALTER TABLE accounts
    ALTER COLUMN customer_profile_id SET NOT NULL,
    ADD CONSTRAINT fk_accounts_customer_profile
        FOREIGN KEY (customer_profile_id) REFERENCES customer_profiles(id);

ALTER TABLE accounts
    DROP COLUMN user_id,
    DROP CONSTRAINT fk_accounts_user_id;

DROP INDEX IF EXISTS idx_accounts_user_id;

CREATE INDEX idx_accounts_customer_profile_id
    ON accounts(customer_profile_id);
