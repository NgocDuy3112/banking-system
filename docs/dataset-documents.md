# Dataset Documentation — Fraud Detection Model

This document catalogs the datasets evaluated for training the Smart Banking fraud detection model, along with the final strategy.

---

## 1. Evaluated Datasets

### 1.1 PaySim — Synthetic Financial Dataset

| Property | Value |
|---|---|
| **Source** | [Kaggle — ealaxi/paysim1](https://www.kaggle.com/datasets/ealaxi/paysim1) |
| **Author** | Edgar Lopez-Rojas (PhD research) |
| **Rows** | ~6 million |
| **Domain** | Mobile money / bank transfers |
| **Fraud rate** | ~0.13% (highly imbalanced) |
| **Time span** | 30 days (744 steps, 1 step = 1 hour) |
| **License** | CC BY-SA 4.0 |

**Columns:**

| Column | Type | Description | Maps to `ScoreRequest` |
|---|---|---|---|
| `step` | int | Hour of simulation (1–744) | → `occurred_at` (derive timestamp) |
| `type` | enum | `CASH-IN`, `CASH-OUT`, `DEBIT`, `PAYMENT`, `TRANSFER` | → `transaction_type` |
| `amount` | float | Transaction amount | → `amount` |
| `nameOrig` | string | Sender account ID | → `from_account_number` |
| `oldbalanceOrg` | float | Sender balance before tx | → `from_balance_before` |
| `newbalanceOrig` | float | Sender balance after tx | (feature engineering) |
| `nameDest` | string | Recipient account ID | → `to_account_number` |
| `oldbalanceDest` | float | Recipient balance before tx | (feature engineering) |
| `newbalanceDest` | float | Recipient balance after tx | (feature engineering) |
| `isFraud` | bool | Target label (1 = fraud) | Target |
| `isFlaggedFraud` | bool | Business rule flag (>200k transfer) | (baseline comparison) |

**Strengths:**
- Account-to-account structure matches our banking domain
- Has `nameOrig` → `nameDest` for building recipient relationship features
- Balance columns enable "account emptying" pattern detection
- Large community (358+ notebooks, years of discussion)

**Weaknesses:**
- No `description` field (no NLP features)
- No device/IP metadata
- Binary fraud label only (no fraud type classification)
- Synthetic — may not capture all real-world edge cases

**Role in our pipeline:** **Primary training dataset** for the XGBoost model.

---

### 1.2 Financial Transactions Dataset (Aryan208)

| Property | Value |
|---|---|
| **Source** | [Kaggle — aryan208/financial-transactions-dataset-for-fraud-detection](https://www.kaggle.com/datasets/aryan208/financial-transactions-dataset-for-fraud-detection) |
| **Author** | Aryan Kumar |
| **Rows** | 5 million |
| **Domain** | General financial transactions |
| **License** | CC0 (Public Domain) |

**Columns:**

| Column | Description |
|---|---|
| `transaction_id` | Unique identifier |
| `timestamp` | Transaction timestamp |
| `sender_account` | Sender account ID |
| `receiver_account` | Receiver account ID |
| `amount` | Transaction amount |
| `transaction_type` | `deposit`, `transfer`, `withdrawal`, etc. |
| `merchant_category` | Category of merchant |
| `location` | Transaction location |
| `device` | Device used |
| `payment_channel` | Channel (online, POS, ATM) |
| `ip_address` | IP address |
| `device_hash` | Device fingerprint |
| `time_since_last_tx` | Time since last transaction |
| `spending_deviation_score` | Deviation from normal spending |
| `velocity_score` | Transaction frequency score |
| `geo_anomaly_score` | Location anomaly score |
| `is_fraud` | Binary fraud label |
| `fraud_type` | `money_laundering`, `account_takeover`, `phishing`, etc. |

**Strengths:**
- Pre-computed behavioral scores (`velocity_score`, `geo_anomaly_score`, `spending_deviation_score`)
- Multi-class fraud labels (`fraud_type`)
- Rich metadata (device, IP, payment channel)
- CC0 license — no attribution required

**Weaknesses:**
- No `description` field
- No balance columns
- Smaller community (12 notebooks)
- Synthetic — same caveats as PaySim

**Role in our pipeline:** **Feature engineering blueprint** — we replicate the behavioral scores in `core/features.py` using PostgreSQL queries.

---

## 2. Final Data Strategy

```
┌─────────────────────────────────────────────────────────┐
│                    TRAINING PIPELINE                      │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  PaySim (ealaxi/paysim1)                                 │
│  ├─ Primary training data (6M rows)                      │
│  ├─ Maps directly to ScoreRequest schema                 │
│  └─ Used for: XGBoost model training                     │
│                                                          │
│  Aryan208 (financial-transactions)                       │
│  ├─ Feature engineering blueprint                        │
│  ├─ Replicate: velocity_score, geo_anomaly_score,        │
│  │   spending_deviation_score in core/features.py         │
│  └─ Used for: feature design inspiration                 │
│                                                          │
│  Synthesized Vietnamese Descriptions                     │
│  ├─ Generated via Faker / LLM                            │
│  ├─ Legitimate: "Tiền thuê nhà tháng 6", "Chuyển khoản" │
│  ├─ Fraud: "Nộp thuế gấp", "Trúng thưởng đóng phí"      │
│  └─ Used for: keepitreal/vietnamese-sbert embeddings     │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### Column Mapping: PaySim → ScoreRequest

| PaySim | ScoreRequest | Transform |
|---|---|---|
| `nameOrig` | `from_account_number` | Direct (string) |
| `nameDest` | `to_account_number` | Direct (string) |
| `amount` | `amount` | Direct (Decimal) |
| `type` | `transaction_type` | Map: `TRANSFER`→`INTERNAL`, `CASH_OUT`→`INTERBANK` |
| `step` | `occurred_at` | Derive datetime from step number |
| `oldbalanceOrg` | `from_balance_before` | Direct (Decimal) |
| `isFraud` | Target label | Direct (0/1) |
| — | `description` | **Synthesized separately** |

---

## 3. What's Still Missing

| Gap | Solution |
|---|---|
| **Vietnamese descriptions** | Synthesize with `Faker` + curated fraud keyword templates |
| **Real-world validation** | Use your own system's data once the backend is live (Phase 2) |
| **Description embeddings** | `keepitreal/vietnamese-sbert` → PCA(50) at inference time |

---

## 4. References

- [PaySim Paper](http://urn.kb.se/resolve?urn=urn:nbn:se:bth-12932) — E. A. Lopez-Rojas et al., EMSS 2016
- [Machine Learning for Fraud Detection Handbook](https://fraud-detection-handbook.github.io/fraud-detection-handbook/)
- [IEEE-CIS Fraud Detection 1st Place Solution](https://www.kaggle.com/c/ieee-fraud-detection/discussion/111284)
- [keepitreal/vietnamese-sbert](https://huggingface.co/keepitreal/vietnamese-sbert) — Vietnamese sentence embeddings
