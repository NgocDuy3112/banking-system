-- =============================================================================
-- V1: Initial schema for users, customer_profiles, accounts
-- =============================================================================
-- Mirrors the JPA entities exactly. Hibernate runs in `validate` mode (see
-- application.yaml), so any drift between this file and the @Column / @JoinColumn
-- annotations will fail-fast at startup. If you change an entity, write a new
-- migration (V2__...) — never edit this file after it has been applied.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- users
-- -----------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------
-- customer_profiles
-- Independent UUID PK; user_id is a separate FK column with a unique constraint
-- enforcing 1:1 with users.
-- -----------------------------------------------------------------------------
CREATE TABLE customer_profiles (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL UNIQUE,
    full_name     VARCHAR      NOT NULL,
    citizen_id    VARCHAR(12)  NOT NULL UNIQUE,
    date_of_birth DATE,
    kyc_status    VARCHAR(16)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,

    CONSTRAINT fk_customer_profiles_users_id
        FOREIGN KEY (user_id) REFERENCES users(id)
);

-- -----------------------------------------------------------------------------
-- accounts
-- account_number is the natural primary key (varchar(15)); balance carries a
-- CHECK constraint to enforce non-negative at the database layer (defence in
-- depth — the entity also guards this).
-- -----------------------------------------------------------------------------
CREATE TABLE accounts (
    account_number VARCHAR(15)   NOT NULL UNIQUE,
    user_id        UUID          NOT NULL,
    balance        NUMERIC(24,4) NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    account_status VARCHAR(16)   NOT NULL,
    account_type   VARCHAR(16)   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL,
    updated_at     TIMESTAMP     NOT NULL,

    CONSTRAINT pk_accounts PRIMARY KEY (account_number),
    CONSTRAINT fk_accounts_user_id
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_accounts_balance_nonneg CHECK (balance >= 0)
);

-- -----------------------------------------------------------------------------
-- Indexes for the hot read paths called out in data-modeling.md §"Indexes quan
-- trọng".
-- -----------------------------------------------------------------------------
CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_customer_profiles_user_id ON customer_profiles(user_id);
