# Architecture — Banking MVP

## Decision: Modular Monolith + ML Service as a separate process


> **Why Modular Monolith:** 2-person team, 2 months — the overhead of Microservices is too large.
> A Modular Monolith still gives clear separation of concerns and is easy to migrate later.

### Why a separate ML Service?

- Independent scaling (ML needs more RAM/CPU)
- Updating the model does not affect the core banking modules
- Demonstrates understanding of the microservice pattern (good CV story)

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
Customer sends a transfer request
         |
         v
Transaction Module receives the request
         |
         v
Call ML Service (HTTP/gRPC sync)
         |
    _____|_____
    |         |
    v         v
Score low   Score high
(<0.5)      (>0.8)
    |         |
    v         v
Allow      BLOCK
             |
             v
        Notify Auditor
        (via Kafka)
```

## Kafka Topics

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| `transaction.completed` | Transaction Module | Audit Consumer, Notification Consumer | Transaction completed |
| `transaction.fraud` | Transaction Module | Audit Consumer | Fraud detected |
| `auth.failed` | Auth Module | Audit Consumer | Login failed |
| `ml.retrain` | Audit Consumer | ML Retraining Consumer | Trigger model retrain |

## Modules in the Monolith

### Auth Module
- Registration, login
- JWT + Refresh Token
- OTP (2FA)
- Rate limiting

### Account Module
- Account creation, eKYC
- View balance
- Lock/Unlock account and card

### Transaction Module
- Internal and interbank transfers (mock)
- ML Service integration
- Transaction history

### Audit Module
- Write Audit Logs
- API for Auditor to read logs
- Kafka consumer that receives events
