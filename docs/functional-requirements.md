# Functional Requirements — Banking MVP

## Core Features

| Feature | Description |
|---|---|
| Account registration | Customer registers online, simplified eKYC (uploads CCCD + selfie, not real verification) |
| Internal transfer | Transfer between accounts within the same bank, with ACID guarantees and concurrency control |
| Interbank transfer | Mock NAPAS 247, simulates a response from an external bank |
| View balance | Customer views account balance in real-time |
| Transaction history | List of transactions, filterable by time and transaction type |
| Lock/Unlock card | Customer self-service lock/unlock card (independent from account) |
| Lock/Unlock account | Customer/Teller can lock/unlock account (full freeze) |
| Fraud Detection | ML real-time blocking of suspicious transactions before they are executed |
| Audit Log | Records every action in the system |
| 2FA (OTP) | OTP verification for transfers and login |

## Actors

### Customer
- Self-register an account online (eKYC)
- Perform transactions: transfer, view balance, view history
- Self-service lock/unlock of cards and accounts
- Receive notifications on transactions

### Teller
- Assist Customer in opening accounts at the branch
- Manually Approve/Reject KYC
- Cash deposit/withdrawal at the branch
- Lock/unlock accounts at Customer's request
- Look up Customer account information

### Auditor
- View the full Audit Log
- View transaction history of any account
- Review transactions flagged as fraud
- **Read-only — cannot perform any write operation**

### Admin
- Create/manage Staff accounts (Teller, Auditor)
- Configure transfer limits and transaction fees
- View system-wide dashboard
- Activate/Deactivate Staff accounts
