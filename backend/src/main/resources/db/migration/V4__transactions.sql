CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    reference_number VARCHAR(32) NOT NULL UNIQUE,
    from_account_number VARCHAR(15) NOT NULL,
    to_account_number VARCHAR(15) NOT NULL,
    amount NUMERIC(24, 4) NOT NULL,
    fee NUMERIC(24, 4) NOT NULL DEFAULT 0,
    transaction_type VARCHAR(16) NOT NULL,
    transaction_status VARCHAR(16) NOT NULL,
    fraud_score REAL,
    fraud_status VARCHAR(16),
    from_balance_before NUMERIC(24, 4) NOT NULL,
    from_balance_after NUMERIC(24, 4) NOT NULL,
    to_balance_before NUMERIC(24, 4) NOT NULL,
    to_balance_after NUMERIC(24, 4) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_transactions_from_account
        FOREIGN KEY (from_account_number) REFERENCES accounts(account_number),
    CONSTRAINT fk_transactions_to_account
        FOREIGN KEY (to_account_number) REFERENCES accounts(account_number),
    CONSTRAINT ck_transactions_amount_positive
        CHECK (amount > 0),
    CONSTRAINT ck_transactions_fee_nonnegative
        CHECK (fee >= 0)
);

CREATE INDEX idx_transactions_from_account_created
    ON transactions(from_account_number, created_at DESC);
CREATE INDEX idx_transactions_to_account_created
    ON transactions(to_account_number, created_at DESC);