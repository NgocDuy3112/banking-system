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
Chứa thông tin authentication. Tất cả roles đều có.

| Field | Type | Ghi chú |
|---|---|---|
| id | UUID | Primary key |
| email | VARCHAR | Unique — **dùng làm login identifier** |
| phone_number | VARCHAR | Unique, optional — kênh liên lạc / nhận OTP |
| password_hash | VARCHAR | Bcrypt |
| role | ENUM | CUSTOMER, TELLER, AUDITOR, ADMIN |
| status | ENUM | ACTIVE, LOCKED |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

> **1 User — 1 Role duy nhất.** Nếu nhân viên muốn dùng app banking → tạo tài khoản Customer riêng biệt.
>
> **Login identifier = email.** Không có `username` riêng; user đăng nhập bằng email đã đăng ký. Quyết định: giảm friction đăng ký, OTP / forgot-password dùng cùng một kênh email.

---

### 2. CustomerProfile
Chứa thông tin KYC. **Chỉ Customer mới có.**

| Field | Type | Ghi chú                                                               |                                                
   |---|---|-----------------------------------------------------------------------|                                                             
| id | UUID | Primary key                                                           |                                               
| user_id | UUID | FK → User                                                             |                                            
| full_name | VARCHAR |                                                                       |                                                 
| citizen_id | VARCHAR(12) | UNIQUE, **chưa mã hoá** (xem open question                            
 trong ADR) |
| cccd_front_image_key | VARCHAR(512) | MinIO object key (kyc-assets/{customerProfileId}/cccd-front/{uuid}.{ext}) |
| cccd_back_image_key | VARCHAR(512) | MinIO object key (kyc-assets/{customerProfileId}/cccd-back/{uuid}.{ext}) |
| selfie_image_key | VARCHAR(512) | MinIO object key (kyc-assets/{customerProfileId}/selfie/{uuid}.{ext}) |
| date_of_birth | DATE |                                                                       |                                                
| address | TEXT | Set khi submit eKYC, nullable (chưa có KYC)                           |          
| kyc_status | ENUM | PENDING, APPROVED, REJECTED                                           | 

---

### 3. StaffProfile
Thông tin nhân viên. Dùng chung cho Teller, Auditor, Admin.

| Field | Type | Ghi chú |
|---|---|---|
| id | UUID | Primary key |
| user_id | UUID | FK → User |
| full_name | VARCHAR | |
| employee_id | VARCHAR | Unique, mã nhân viên |

> **Không có branch** trong MVP. Có thể thêm sau.

---

### 4. Account
Tài khoản ngân hàng của Customer.

| Field | Type | Ghi chú |
|---|---|---|
| id | UUID | Primary key |
| account_number | VARCHAR | Unique, format: 9-14 số |
| customer_profile_id | UUID | FK → CustomerProfile |
| account_type | ENUM | DEBIT, CREDIT, SAVINGS |
| balance | DECIMAL(19,4) | **Không dùng FLOAT** |
| currency | VARCHAR | Mặc định: VND |
| status | ENUM | ACTIVE, LOCKED, CLOSED |
| created_at | TIMESTAMP | |

---

### 5. Card
Thẻ DEBIT liên kết với Account.

| Field | Type | Ghi chú |
|---|---|---|
| id | UUID | Primary key |
| account_id | UUID | FK → Account |
| card_number | VARCHAR | **Encrypted** |
| card_type | ENUM | DEBIT |
| status | ENUM | ACTIVE, LOCKED, EXPIRED |
| expired_at | TIMESTAMP | |
| created_at | TIMESTAMP | |

> **Khóa thẻ ≠ Khóa tài khoản:**
> - Khóa thẻ: không dùng thẻ vật lý/online được, vẫn chuyển khoản app được
> - Khóa tài khoản: đóng băng hoàn toàn

---

### 6. Transaction
Ghi lại toàn bộ giao dịch, bao gồm snapshot số dư và fraud score.

| Field | Type | Ghi chú |
|---|---|---|
| id | UUID | Primary key |
| reference_number | VARCHAR | Unique, dùng để trace |
| from_account_id | UUID | FK → Account |
| to_account_id | UUID | FK → Account |
| amount | DECIMAL(19,4) | |
| fee | DECIMAL(19,4) | |
| transaction_type | ENUM | INTERNAL, INTERBANK |
| status | ENUM | PENDING, SUCCESS, FAILED, BLOCKED |
| fraud_score | FLOAT | Output của ML (0.0 - 1.0) |
| fraud_status | ENUM | CLEAR, SUSPICIOUS, BLOCKED |
| from_balance_before | DECIMAL(19,4) | Snapshot số dư trước giao dịch |
| from_balance_after | DECIMAL(19,4) | Snapshot số dư sau giao dịch |
| to_balance_before | DECIMAL(19,4) | Snapshot số dư trước giao dịch |
| to_balance_after | DECIMAL(19,4) | Snapshot số dư sau giao dịch |
| description | TEXT | |
| created_at | TIMESTAMP | |

> **Tại sao cần balance snapshot?**
> - Auditor verify: `from_balance_before - amount - fee = from_balance_after`
> - Dispute resolution: proof tức thì khi khách hàng khiếu nại
> - Forensics: trace lại số dư khi điều tra fraud

**Lưu ý kỹ thuật:** balance snapshot phải được ghi trong cùng 1 database transaction với việc update balance:
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
Ghi lại toàn bộ hành động trong hệ thống. **Immutable — không ai được sửa/xóa.**

| Field | Type | Ghi chú |
|---|---|---|
| id | UUID | Primary key |
| actor_id | UUID | FK → User |
| actor_role | VARCHAR | Snapshot role tại thời điểm hành động |
| action | VARCHAR | TRANSFER, LOGIN, LOCK_ACCOUNT, APPROVE_KYC,... |
| target_type | VARCHAR | ACCOUNT, CARD, USER, TRANSACTION,... |
| target_id | UUID | ID của object bị tác động |
| ip_address | VARCHAR | |
| result | ENUM | SUCCESS, FAILED |
| metadata | JSONB | Chi tiết thêm tuỳ action |
| created_at | TIMESTAMP | |

---

### 8. OTP
Lưu trên **Redis** (không phải PostgreSQL) để tận dụng TTL tự nhiên.

| Field | Type | Ghi chú |
|---|---|---|
| user_id | String | |
| code | String | Hashed (bcrypt) |
| type | String | LOGIN, TRANSFER, UNLOCK |
| expired_at | TTL | Redis tự xóa sau khi hết hạn |

**Redis key pattern:** `otp:{user_id}:{type}`

---

## Indexes quan trọng

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