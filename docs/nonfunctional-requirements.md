# Non-Functional Requirements — Banking MVP

## Performance

| Metric | Value |
|---|---|
| Concurrent users | 1,000 |
| TPS (peak) | ~150 transactions/second |
| API latency | < 500ms |
| Transfer (with ML) | < 1s end-to-end |
| ML inference | < 250ms |

> **Note:** 150 TPS is a realistic number for 1,000 users (each user ~1 transaction / 30 seconds, peak x3).
> 10,000 TPS is not appropriate because it does not match the 1,000-user scale.

## Availability

| Metric | Value |
|---|---|
| Uptime target | 99.9% |
| Allowed downtime | ~45 minutes/month |

## Consistency

- **Strong Consistency is the priority** — a correct balance is more important than availability.
- If the system has an issue → rather fail the transfer than transfer the wrong amount.
- Applied via: ACID transactions, Pessimistic Locking on transfers.

## Security

| Requirement | Detail |
|---|---|
| Transport | HTTPS required |
| Authentication | JWT + Refresh Token |
| 2FA | OTP for transfers and login |
| Encryption | CCCD numbers and card numbers must be encrypted at rest |
| Rate Limiting | Prevent login brute force and API spam |
| Auth Logging | Log every failed login attempt |

## ML Service Requirements

| Metric | Value |
|---|---|
| Inference latency | < 250ms |
| Fallback | Rule-based scoring when the ML Service is down |
| Availability | ML Service down → must not block the whole system |

### Fraud Score Threshold

| Score | Action |
|---|---|
| 0.0 – 0.5 | Allow |
| 0.5 – 0.8 | Require additional OTP confirmation |
| 0.8 – 1.0 | Block transaction, notify Auditor |
