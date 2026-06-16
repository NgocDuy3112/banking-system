# Architecture — Banking MVP

## Quyết định: Modular Monolith + ML Service tách riêng


> **Chọn Modular Monolith vì:** team 2 người, 2 tháng — overhead của Microservices quá lớn.
> Modular Monolith vẫn có separation of concerns rõ ràng, dễ migrate sau này.

### Tại sao ML Service tách riêng?

- Scale độc lập (ML cần nhiều RAM/CPU hơn)
- Update model không ảnh hưởng core banking
- Thể hiện hiểu biết về microservice pattern trong CV

## System Overview

```
[Client - Web/Mobile]
         |
         v
[Spring Boot - Modular Monolith]
|-- Auth Module
|-- Account Module
|-- Transaction Module
|-- Audit Module
         |
    _____|______________________
    |           |              |
    v           v              v
[PostgreSQL] [Redis]        [Kafka]
                               |
              _________________|__________________
              |                |                  |
              v                v                  v
       [Audit Consumer] [Notification      [ML Retraining
                         Consumer]          Consumer]

[FastAPI - ML Service]
         |
         v
  [Model Store - ONNX/pickle]
```

## Fraud Detection Flow (Real-time)

```
Customer request chuyển tiền
         |
         v
Transaction Module nhận request
         |
         v
Gọi ML Service (HTTP/gRPC sync)
         |
    _____|_____
    |         |
    v         v
Score thấp  Score cao
(<0.5)      (>0.8)
    |         |
    v         v
Cho qua    BLOCK
             |
             v
        Notify Auditor
        (qua Kafka)
```

## Kafka Topics

| Topic | Producer | Consumer | Mục đích |
|---|---|---|---|
| `transaction.completed` | Transaction Module | Audit Consumer, Notification Consumer | Giao dịch hoàn thành |
| `transaction.fraud` | Transaction Module | Audit Consumer | Fraud bị phát hiện |
| `auth.failed` | Auth Module | Audit Consumer | Đăng nhập thất bại |
| `ml.retrain` | Audit Consumer | ML Retraining Consumer | Trigger retrain model |

## Modules trong Monolith

### Auth Module
- Đăng ký, đăng nhập
- JWT + Refresh Token
- OTP (2FA)
- Rate limiting

### Account Module
- Tạo tài khoản, eKYC
- Xem số dư
- Khóa/mở khóa tài khoản, thẻ

### Transaction Module
- Chuyển tiền nội bộ, liên ngân hàng (mock)
- Tích hợp ML Service
- Lịch sử giao dịch

### Audit Module
- Ghi Audit Log
- API cho Auditor xem log
- Kafka consumer nhận events