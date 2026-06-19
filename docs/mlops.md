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
