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
