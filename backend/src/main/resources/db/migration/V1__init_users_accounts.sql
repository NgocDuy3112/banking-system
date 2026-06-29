CREATE TABLE users (
    id              UUID         PRIMARY KEY,
    email           VARCHAR(320) NOT NULL UNIQUE,
    phone_number    VARCHAR(20)  UNIQUE,
    hashed_password VARCHAR      NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);

CREATE TABLE customer_profiles (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL UNIQUE,
    full_name     VARCHAR      NOT NULL,
    citizen_id    VARCHAR(12)  NOT NULL UNIQUE,
    date_of_birth DATE,
    kyc_status    VARCHAR(16)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPZ    NOT NULL,
    updated_at    TIMESTAMPZ    NOT NULL,

    CONSTRAINT fk_customer_profiles_users_id
        FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE accounts (
    account_number VARCHAR(15)   NOT NULL UNIQUE,
    user_id        UUID          NOT NULL,
    balance        NUMERIC(24,4) NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    account_status VARCHAR(16)   NOT NULL,
    account_type   VARCHAR(16)   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPZ     NOT NULL,
    updated_at     TIMESTAMPZ     NOT NULL,

    CONSTRAINT pk_accounts PRIMARY KEY (account_number),
    CONSTRAINT fk_accounts_user_id
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_accounts_balance_nonneg CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_customer_profiles_user_id ON customer_profiles(user_id);
