# MLOps — Fraud Detection Model Lifecycle

> **Status:** v1 plan — SHAP explainability and MLflow tracking are the first two capabilities.
> Automated retraining (closed-loop from auditor labels) is deferred to Phase 2.
> **Audience:** ML team (this repo: `ml/`).

---

## 1. Why MLOps Matters Here

A fraud model that returns `fraud_score: 0.87` with empty `reason_codes: []` is a black box. The auditor reviewing a blocked transaction has no idea **why** it was blocked. The ML engineer retraining the model has no record of **which hyperparameters produced the best AUC**.

MLOps fixes both problems:

| Capability | Problem it solves | Phase |
|---|---|---|
| **SHAP Explainability** | Auditor sees `reason_codes: []` — no rationale for blocking a transaction | v1 |
| **MLflow Tracking + Registry** | No record of experiments; model versions are hand-typed strings | v1 |
| **Automated Retraining** | Model stagnates; no feedback loop from real fraud labels | v2 (planned) |

---

## 2. SHAP Explainability

### 2.1 Concept

SHAP (SHapley Additive exPlanations) decomposes the model's fraud score into per-feature contributions. For a transaction scored at `0.87`, SHAP answers:

> *"0.34 of that score came from the account being nearly emptied, 0.28 from the recipient being new, and 0.15 from the transaction happening at 3 AM."*

XGBoost has first-class SHAP support via `shap.TreeExplainer`. No model changes needed — it's a post-hoc explainer that runs at inference time.

### 2.2 Response Schema Change

`reason_codes` evolves from `list[str]` to a structured list:

**Current (stub):**
```json
"reason_codes": []
```

**Target (v1):**
```json
"reason_codes": [
  {"code": "BALANCE_EMPTYING", "weight": 0.34},
  {"code": "NEW_RECIPIENT",    "weight": 0.28},
  {"code": "OFF_HOURS",        "weight": 0.15}
]
```

| Field | Type | Meaning |
|---|---|---|
| `code` | string | Feature code — maps to a human-readable label in the Audit UI |
| `weight` | float | SHAP value — positive = pushes score up (risk), negative = pushes down (safe) |

The Audit UI derives direction from the sign of `weight` (`> 0` → red/increases risk, `< 0` → green/decreases risk) and compares against alert thresholds on its own. The ML service stays focused on what it actually computes: SHAP values.

Only features with `|weight| > 0.05` are included in the response (noise filtering). Results are sorted by `|weight|` descending.

### 2.3 Feature Code Catalog

| Code | Human-readable (Audit UI) | Feature |
|---|---|---|
| `BALANCE_EMPTYING` | "Tài khoản gần như bị rút sạch" | `amount / from_balance_before > 0.9` |
| `NEW_RECIPIENT` | "Người nhận chưa từng giao dịch" | `to_account` not in sender's history |
| `OFF_HOURS` | "Giao dịch ngoài giờ hành chính" | Hour ∈ [0, 5] (midnight–5 AM) |
| `VELOCITY_SPIKE` | "Tần suất giao dịch bất thường" | > 5 transactions in last hour |
| `LARGE_AMOUNT` | "Số tiền lớn bất thường" | Z-score > 3.0 vs. user's 30-day avg |
| `INTERBANK_RISK` | "Chuyển tiền liên ngân hàng" | `transaction_type == INTERBANK` |
| `ROUND_AMOUNT` | "Số tiền tròn đáng ngờ" | Amount ends in `000000` (laundering pattern) |

### 2.4 Implementation Plan

```
┌─────────────────────────────────────────────────────────┐
│                  SHAP AT INFERENCE TIME                   │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  1. Load ONNX model + shap.TreeExplainer at startup      │
│     (TreeExplainer is pre-computed once, cached)          │
│                                                          │
│  2. On each /score request:                              │
│     a. Compute structured features from ScoreRequest      │
│        + PostgreSQL history queries                       │
│     b. Run ONNX inference → fraud_score                  │
│     c. Run shap.Explainer(feature_vector) → SHAP values  │
│     d. Map top SHAP values → reason_codes                 │
│     e. Return ScoreResponse with populated reason_codes   │
│                                                          │
│  Latency budget:                                          │
│  - DB queries:     ~30ms                                  │
│  - ONNX inference: ~5ms                                   │
│  - SHAP explain:   ~10ms (pre-computed TreeExplainer)     │
│  - Total p99:      ~50ms (well under 250ms SLA)           │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

**Key implementation detail:** `shap.TreeExplainer` is computed once at model load time and cached. Per-request, only `explainer.shap_values(feature_vector)` is called — this is a fast matrix multiplication, not a re-computation of the explainer.

### 2.5 Dependencies

Add to `pyproject.toml`:
```toml
"shap>=0.46.0",
```

---

## 3. MLflow — Experiment Tracking & Model Registry

### 3.1 Concept

MLflow is the backbone of model governance. It answers:

- *"Which hyperparameter combination gave the best AUC-ROC?"*
- *"What model version is currently in production?"*
- *"If production degrades, can I instantly roll back to the previous version?"*

Two MLflow components are used:

| Component | Purpose |
|---|---|
| **Tracking Server** | Logs every training run: hyperparams, metrics, artifacts, code version |
| **Model Registry** | Manages model lifecycle: `None` → `Staging` → `Production` → `Archived` |

### 3.2 Experiment Tracking

Every training run logs:

```
Run: xgboost-v1-20260618-01
├── Hyperparameters
│   ├── max_depth: 6
│   ├── learning_rate: 0.05
│   ├── n_estimators: 200
│   ├── scale_pos_weight: 50 (fraud ~0.13%)
│   └── subsample: 0.8
├── Metrics
│   ├── auc_roc: 0.94
│   ├── auc_pr: 0.72
│   ├── precision@0.8: 0.85
│   ├── recall@0.8: 0.68
│   └── f1@0.8: 0.76
├── Artifacts
│   ├── confusion_matrix.png
│   ├── shap_summary.png
│   ├── feature_importance.csv
│   └── model.onnx (registered separately)
└── Tags
    ├── dataset: paysim-v1
    ├── git_commit: a1b2c3d
    └── trained_by: duy
```

### 3.3 Model Registry Lifecycle

```
┌──────────────────────────────────────────────┐
│              MODEL LIFECYCLE                   │
├──────────────────────────────────────────────┤
│                                               │
│  None ──────► Staging ──────► Production      │
│   ↑              │                │           │
│   │              │                │           │
│   └── Archived ◄─┴────────────────┘           │
│                                               │
│  Transitions:                                  │
│  - None → Staging: manual (ML engineer)        │
│  - Staging → Production: manual after review   │
│  - Production → Archived: when superseded      │
│  - Any → Archived: if performance degrades     │
│                                               │
└──────────────────────────────────────────────┘
```

The ML service loads the model tagged `Production` in the registry — not from a file path. This means:

- **Rollback is instant:** change the `Production` tag back to the previous version, restart the ML service.
- **Shadow deployment:** load `Staging` model alongside `Production`, log both scores for comparison.
- **Audit trail:** every model that ever served production traffic is in the registry.

### 3.4 Integration with the ML Service

**Current (`config.py`):**
```python
model_path: Path = Field(
    default=Path("models/fraud_model.onnx"),
    ...
)
```

**Target (v1):**
```python
model_path: Path = Field(
    default=Path("models/fraud_model.onnx"),
    description="Fallback path if MLflow is unreachable.",
)
mlflow_tracking_uri: str = Field(
    default="http://localhost:5000",
    description="MLflow Tracking Server URI.",
)
mlflow_model_name: str = Field(
    default="fraud-detector",
    description="Registered model name in MLflow Registry.",
)
```

**Startup logic:**
```python
def load_model(settings: Settings):
    try:
        # Try MLflow first
        client = MlflowClient(tracking_uri=settings.mlflow_tracking_uri)
        model = client.get_latest_versions(settings.mlflow_model_name,
                                            stages=["Production"])[0]
        model_path = client.download_artifacts(model.run_id, "model.onnx")
        logger.info(f"Loaded model {model.version} from MLflow Registry")
    except Exception:
        # Fall back to local file
        model_path = settings.model_path
        logger.warning(f"MLflow unreachable, falling back to {model_path}")
    return onnxruntime.InferenceSession(model_path)
```

### 3.5 Training Script Integration

```python
import mlflow
import mlflow.xgboost

mlflow.set_tracking_uri("http://localhost:5000")
mlflow.set_experiment("fraud-detection")

with mlflow.start_run(run_name="xgboost-v1-20260618-01"):
    # Log hyperparams
    mlflow.log_params({
        "max_depth": 6,
        "learning_rate": 0.05,
        "n_estimators": 200,
        "scale_pos_weight": 50,
    })

    # Train
    model = xgb.XGBClassifier(...)
    model.fit(X_train, y_train)

    # Log metrics
    mlflow.log_metrics({
        "auc_roc": 0.94,
        "auc_pr": 0.72,
        "f1": 0.76,
    })

    # Log model (auto-converts to ONNX via mlflow.xgboost)
    mlflow.xgboost.log_model(model, "model",
                             onnx_execution_backend="onnxruntime")

    # Log artifacts
    mlflow.log_artifact("confusion_matrix.png")
    mlflow.log_artifact("shap_summary.png")
```

### 3.6 Dependencies

Add to `pyproject.toml`:
```toml
"mlflow>=2.15.0",
```

### 3.7 Running MLflow Locally

```bash
# Start the tracking server (separate terminal)
mlflow server \
  --backend-store-uri sqlite:///mlflow.db \
  --default-artifact-root ./mlflow-artifacts \
  --host 0.0.0.0 \
  --port 5000

# UI available at http://localhost:5000
```

For production, swap SQLite for PostgreSQL:
```bash
mlflow server \
  --backend-store-uri postgresql://user:pass@host:5432/mlflow \
  --default-artifact-root s3://mlflow-models \
  --host 0.0.0.0 \
  --port 5000
```

---

## 4. How SHAP + MLflow Work Together

```
┌─────────────────────────────────────────────────────────┐
│                 END-TO-END MLOPS FLOW                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  TRAINING (offline, weekly)                              │
│  ┌──────────────────────────────────────┐                │
│  │ 1. Pull data from PostgreSQL          │                │
│  │ 2. Train XGBoost                      │                │
│  │ 3. Log to MLflow (params, metrics)    │                │
│  │ 4. Export ONNX + register model       │                │
│  │ 5. Compute TreeExplainer, cache it    │                │
│  │ 6. Promote to Staging if AUC improves │                │
│  └──────────────────────────────────────┘                │
│                    │                                     │
│                    v                                     │
│  INFERENCE (online, per-request)                         │
│  ┌──────────────────────────────────────┐                │
│  │ 1. Load Production model from MLflow  │                │
│  │ 2. Load cached TreeExplainer          │                │
│  │ 3. Compute features from DB + request │                │
│  │ 4. ONNX inference → fraud_score       │                │
│  │ 5. SHAP explain → reason_codes        │                │
│  │ 6. Return ScoreResponse               │                │
│  └──────────────────────────────────────┘                │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## 4b. Known Dataset Limitation — PaySim Patterns

The training and test sets come from [PaySim](https://www.kaggle.com/datasets/ealaxi/paysim1), a synthetic mobile-money simulator. The fraud generator in PaySim follows deterministic patterns that **do NOT generalize to real banking**:

| Pattern | PaySim behavior | Real-world likelihood |
|---|---|---|
| Fraudsters drain balance | **96.9%** of fraud has `balance_emptying_ratio == 1.0` (full drain) | Rare — most fraud is partial or gradual |
| Fraudsters use round amounts | `is_round_amount` over-represented in fraud | Variable, country-dependent |
| Fraudsters transact off-hours | `is_night` over-represented | Mixed |

This means **even after removing the explicit simulator-leak features** (`newbalanceOrig`, `post_balance_ratio` in v3), the model can still achieve PR-AUC ≈ 1.0 on the PaySim test set by exploiting the strong "fraudsters drain their account" pattern. This is **legitimate PaySim signal**, not a code bug.

**Implication for production:**
- Treat the PaySim test PR-AUC as an **upper bound**, not a real-world estimate.
- When real labeled banking data becomes available, expect PR-AUC to drop to roughly **0.80–0.95** depending on how closely real fraud matches PaySim patterns.
- The model and SHAP explainer are still useful: the **features it relies on** (balance emptying, round amounts, off-hours) are business-relevant signals that any production fraud system should monitor.

**What v3 changed (vs v2):**
- Removed `newbalanceOrig` and `post_balance_ratio` because they were **deterministic** post-transaction fields that encoded the simulator's "balance == 0 → fraud" rule directly.
- Kept `balance_emptying_ratio` (the legitimate "what fraction of the balance was sent" signal) and `amount_to_balance_pct` because both are derivable from pre-transaction data available in the live `ScoreRequest`.

---

## 4c. Feature Subset Experiments (v4 Sweep)

To test whether new request-time features improve model quality, we ran a 3-experiment sweep on 2026-06-25 with 9 total MLflow runs (3 model configs × 3 feature subsets):

| Experiment | Feature set | Features | What it tests |
|---|---|---|---|
| **E1** | v3 baseline | 20 | Re-confirm v3 metrics (sanity check) |
| **E2** | v3 + Group A (amount tier) | 23 | Does tier encoding help? |
| **E3** | v3 + Group A + Group D (interactions) | 25 | Does feature interaction help? |

**Group A — Amount tier** (3 binary features):
- `amount_tier_micro` — `amount < 1M VND`
- `amount_tier_small` — `1M ≤ amount < 10M VND`
- `amount_tier_medium` — `10M ≤ amount < 100M VND`

In PaySim, the medium tier is **99.4×** over-represented in fraud (5.7% fraud vs 0.1% legit).

**Group D — Interactions** (2 binary features):
- `is_transfer_and_draining` — INTERBANK + `balance_emptying_ratio > 0.9`
- `is_medium_and_draining` — medium tier + `ratio > 0.99`

### Results (sorted by PR-AUC)

| Model | E1 (20) | E2 (23) | E3 (25) | Δ E1→E3 |
|---|---|---|---|---|
| XGBoost | 0.9874 | 0.9860 | 0.9890 | +0.0016 |
| LightGBM v1 | 1.0000 (F1=0.987) | 1.0000 (F1=0.974) | 1.0000 (F1=0.934) | **F1 ↓ 0.05** |
| LightGBM v2 | 1.0000 (F1=0.990) | 1.0000 (F1=0.998) | 0.9999 (F1=0.996) | stable |

### Interpretation

- **PR-AUC is already saturated** for LightGBM (1.0) — the `balance_emptying_ratio` leak-discussed in §4b dominates.
- **F1@0.5 actually drops** in some configs when v4 features are added — the model becomes more aggressive (more false positives) because v4 features are correlated with the leak pattern but don't add orthogonal signal.
- **No clear winner for production** — the v3 model is retained as the shipping version. v4 features stay in `features_runtime.py` for future use when real banking data replaces PaySim.

**Lesson:** On a synthetic dataset with deterministic fraud patterns, adding more "obvious" features doesn't help — it just makes the model over-confident. Feature engineering should be validated against a holdout that breaks the simulator's assumptions.

### MLflow Artifacts

All 9 sweep runs are in experiment `fraud-detection-training` (ID 8) tagged with `experiment = E1/E2/E3`:

- Filter by `params.experiment = "E1"` (or E2/E3) in the MLflow UI to isolate one feature subset.
- Script: [`logs/run_feature_sweep_v2.py`](../logs/run_feature_sweep_v2.py)
- Output log: [`logs/feature_experiments_v2.log`](../logs/feature_experiments_v2.log)

### Production Decision

**v3 (XGBoost, 20 features) is retained as the production model** because:
1. Simplest inference pipeline (1 XGBoost ONNX, no LightGBM variant)
2. Lowest inference latency (0.48ms vs LightGBM 0.15ms — both acceptable, but XGBoost is the platform's chosen framework)
3. v4 features are kept in `features_runtime.py` for the day when real banking data shows tier/interaction patterns that PaySim doesn't.

### Calibration Observation — XGBoost vs LightGBM at BLOCKED threshold (0.8)

After running the full sweep with `f1_at_0.8`, we noticed:

| Model | F1@0.8 (E1) | F1@0.8 (E3) | Notes |
|---|---|---|---|
| XGBoost | 0.95 | 0.95 | Recovers at 0.8 — most fraud prob > 0.8 |
| XGBoost (E2) | **0.00** | — | **Bug-shaped**: no fraud prob reaches 0.8 — calibration shifted |
| LightGBM v1 | 0.99 | 0.97 | Both pass |
| LightGBM v2 | 1.00 | 0.99 | Both pass |

**XGBoost's `scale_pos_weight=450` compresses its probability output range** — even for true fraud, predicted probability often tops out at ~0.7. This means the `BLOCKED` contract threshold (≥0.8) rarely fires for XGBoost, so `risk_level=BLOCKED` decisions effectively don't happen with the current model.

**Recommendation:** LightGBM v2-deeper (E1, 20 features) is the strongest candidate if BLOCKED decision-matters — F1@0.8 = 1.0, inference 0.15ms (3× faster than XGBoost). Decision deferred to Phase 2 when real banking traffic calibrates the thresholds against actual business cost of false positives vs false negatives.

---

## 5. What's Deferred to Phase 2

| Capability | Why deferred |
|---|---|
| **Automated retraining** | Needs real auditor-labeled fraud data from the live system |
| **Data drift monitoring** | Needs production traffic to establish baseline distributions |
| **Shadow deployment** | Needs MLflow Registry fully operational first |
| **Feature store** | `core/features.py` already serves as a lightweight feature store — formalize later |

---

## 6. References

- [SHAP Documentation](https://shap.readthedocs.io/) — TreeExplainer for XGBoost
- [MLflow Documentation](https://mlflow.org/docs/latest/) — Tracking + Model Registry
- [ONNX Runtime](https://onnxruntime.ai/) — Inference engine
- [skl2onnx](https://github.com/onnx/sklearn-onnx) — scikit-learn/XGBoost → ONNX converter
