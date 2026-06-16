# Non-Functional Requirements — Banking MVP

## Performance

| Metric | Value |
|---|---|
| Concurrent users | 1,000 |
| TPS (peak) | ~150 transactions/giây |
| API latency | < 500ms |
| Chuyển tiền (có ML) | < 1s end-to-end |
| ML inference | < 250ms |

> **Lưu ý:** 150 TPS là con số realistic cho 1,000 user (mỗi user ~1 giao dịch/30 giây, peak x3).
> Không nên dùng 10,000 TPS vì không match với quy mô 1,000 user.

## Availability

| Metric | Value |
|---|---|
| Uptime target | 99.9% |
| Downtime cho phép | ~45 phút/tháng |

## Consistency

- **Ưu tiên Strong Consistency** — đúng số dư quan trọng hơn availability
- Nếu hệ thống có vấn đề → thà không chuyển được còn hơn chuyển sai số tiền
- Áp dụng: ACID transactions, Pessimistic Locking cho chuyển tiền

## Security

| Requirement | Chi tiết |
|---|---|
| Transport | HTTPS bắt buộc |
| Authentication | JWT + Refresh Token |
| 2FA | OTP khi chuyển tiền, đăng nhập |
| Mã hoá | Số CCCD, số thẻ phải được mã hoá khi lưu |
| Rate Limiting | Chống brute force đăng nhập, chống spam API |
| Auth Logging | Log toàn bộ đăng nhập thất bại |

## ML Service Requirements

| Metric | Value |
|---|---|
| Inference latency | < 250ms |
| Fallback | Rule-based khi ML Service down |
| Availability | ML Service down → không block toàn bộ hệ thống |

### Fraud Score Threshold

| Score | Hành động |
|---|---|
| 0.0 – 0.5 | Cho qua |
| 0.5 – 0.8 | Yêu cầu xác nhận OTP thêm |
| 0.8 – 1.0 | Block giao dịch, notify Auditor |