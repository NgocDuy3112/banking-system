# Data Modeling — Banking MVP

## Entity Relationship Overview

```
User ──────────────── CustomerProfile ──── Account ──── Card
│                                              │
│                                          Transaction
│
└──── StaffProfile

User ──── AuditLog
```

## Entities

### 1. User
Holds authentication information. Shared by all roles.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| email | VARCHAR | Unique — **used as the login identifier** |
| phone_number | VARCHAR | Unique, optional — contact channel / receives OTP |
| password_hash | VARCHAR | Bcrypt |
| role | ENUM | CUSTOMER, TELLER, AUDITOR, ADMIN |
| status | ENUM | ACTIVE, LOCKED |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

> **1 User — exactly 1 Role.** If a staff member also wants to use the banking app, they must create a separate Customer account.
>
> **Login identifier = email.** There is no separate `username`; users log in with the email they registered with. Decision: reduces registration friction — OTP and forgot-password use the same email channel.

---

### 2. CustomerProfile
Holds KYC information. **Only Customers have one.**

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| user_id | UUID | FK → User |
| full_name | VARCHAR | |
| cccd_number | VARCHAR | **Encrypted** |
| cccd_front_image_url | VARCHAR | |
| cccd_back_image_url | VARCHAR | |
| selfie_image_url | VARCHAR | |
| date_of_birth | DATE | |
| address | TEXT | |
| kyc_status | ENUM | PENDING, APPROVED, REJECTED |

---

### 3. StaffProfile
Staff information. Shared by Teller, Auditor, Admin.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| user_id | UUID | FK → User |
| full_name | VARCHAR | |
| employee_id | VARCHAR | Unique, staff ID |

> **No branch** in the MVP. May be added later.

---

### 4. Account
Customer's bank account.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| account_number | VARCHAR | Unique, format: 9-14 digits |
| customer_profile_id | UUID | FK → CustomerProfile |
| account_type | ENUM | CHECKING, SAVINGS |
| balance | DECIMAL(19,4) | **Do not use FLOAT** |
| currency | VARCHAR | Default: VND |
| status | ENUM | ACTIVE, LOCKED, CLOSED |
| created_at | TIMESTAMP | |

---

### 5. Card
DEBIT card linked to an Account.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| account_id | UUID | FK → Account |
| card_number | VARCHAR | **Encrypted** |
| card_type | ENUM | DEBIT |
| status | ENUM | ACTIVE, LOCKED, EXPIRED |
| expired_at | TIMESTAMP | |
| created_at | TIMESTAMP | |

> **Locking a card is not the same as locking an account:**
> - Card lock: the physical/online card cannot be used, but the customer can still transfer via the app.
> - Account lock: the account is fully frozen.

---

### 6. Transaction
Records every transaction, including balance snapshots and the fraud score.

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| reference_number | VARCHAR | Unique, used for tracing |
| from_account_id | UUID | FK → Account |
| to_account_id | UUID | FK → Account |
| amount | DECIMAL(19,4) | |
| fee | DECIMAL(19,4) | |
| transaction_type | ENUM | INTERNAL, INTERBANK |
| status | ENUM | PENDING, SUCCESS, FAILED, BLOCKED |
| fraud_score | FLOAT | ML output (0.0 – 1.0) |
| fraud_status | ENUM | CLEAR, SUSPICIOUS, BLOCKED |
| from_balance_before | DECIMAL(19,4) | Balance snapshot before the transaction |
| from_balance_after | DECIMAL(19,4) | Balance snapshot after the transaction |
| to_balance_before | DECIMAL(19,4) | Balance snapshot before the transaction |
| to_balance_after | DECIMAL(19,4) | Balance snapshot after the transaction |
| description | TEXT | |
| created_at | TIMESTAMP | |

> **Why are balance snapshots needed?**
> - Auditor verification: `from_balance_before - amount - fee = from_balance_after`
> - Dispute resolution: instant proof when a customer raises a complaint.
> - Forensics: trace balances back when investigating fraud.

**Technical note:** the balance snapshot must be written in the same database transaction as the balance update:
```java
@Transactional
public void transfer(...) {
    Account from = accountRepo.findByIdForUpdate(fromId); // pessimistic lock
    Account to = accountRepo.findByIdForUpdate(toId);

    BigDecimal fromBefore = from.getBalance();
    BigDecimal toBefore = to.getBalance();

    from.setBalance(fromBefore.subtract(amount).subtract(fee));
    to.setBalance(toBefore.add(amount));

    transaction.setFromBalanceBefore(fromBefore);
    transaction.setFromBalanceAfter(from.getBalance());
    transaction.setToBalanceBefore(toBefore);
    transaction.setToBalanceAfter(to.getBalance());

    accountRepo.save(from);
    accountRepo.save(to);
    transactionRepo.save(transaction);
}
```

---

### 7. AuditLog
Records every action in the system. **Immutable — no one can edit or delete it.**

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| actor_id | UUID | FK → User |
| actor_role | VARCHAR | Snapshot of the role at the time of the action |
| action | VARCHAR | TRANSFER, LOGIN, LOCK_ACCOUNT, APPROVE_KYC, ... |
| target_type | VARCHAR | ACCOUNT, CARD, USER, TRANSACTION, ... |
| target_id | UUID | ID of the affected object |
| ip_address | VARCHAR | |
| result | ENUM | SUCCESS, FAILED |
| metadata | JSONB | Extra details depending on the action |
| created_at | TIMESTAMP | |

---

### 8. OTP
Stored in **Redis** (not PostgreSQL) to take advantage of native TTL.

| Field | Type | Notes |
|---|---|---|
| user_id | String | |
| code | String | Hashed (bcrypt) |
| type | String | LOGIN, TRANSFER, UNLOCK |
| expired_at | TTL | Redis auto-deletes when expired |

**Redis key pattern:** `otp:{user_id}:{type}`

---

## Important Indexes

```sql
-- Account lookup
CREATE INDEX idx_account_number ON account(account_number);
CREATE INDEX idx_account_customer ON account(customer_profile_id);

-- Transaction query
CREATE INDEX idx_transaction_from ON transaction(from_account_id, created_at DESC);
CREATE INDEX idx_transaction_to ON transaction(to_account_id, created_at DESC);
CREATE INDEX idx_transaction_ref ON transaction(reference_number);

-- Audit log query
CREATE INDEX idx_audit_actor ON audit_log(actor_id, created_at DESC);
CREATE INDEX idx_audit_target ON audit_log(target_type, target_id);
```
