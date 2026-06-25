"""Run E1/E2/E3 feature subset experiments and log to MLflow.

E1: v3 baseline (20 features) — drop Group A + Group D
E2: + Group A (23 features) — drop Group D
E3: + Group A + Group D (25 features) — no drops

Each: XGBoost + 2 LightGBM configs = 3 runs × 3 = 9 runs.
"""
import json
import pickle
import subprocess
import time
from pathlib import Path

import lightgbm as lgb
import mlflow
import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.metrics import (
    average_precision_score, f1_score, precision_score, recall_score, roc_auc_score
)

# --- Setup ---
DATA_DIR = Path("/Users/ngocduy/PROJECTS/smart-banking-system/ml/data/features")
MODELS_DIR = Path("/Users/ngocduy/PROJECTS/smart-banking-system/ml/models")
LOG_PATH = Path("/Users/ngocduy/PROJECTS/smart-banking-system/logs/feature_experiments_v2.log")

mlflow.set_tracking_uri("http://localhost:5050")
mlflow.set_registry_uri("http://localhost:5050")

EXPERIMENT_TRAINING = "fraud-detection-training"
exp = mlflow.get_experiment_by_name(EXPERIMENT_TRAINING)
exp_id = exp.experiment_id if exp else mlflow.create_experiment(EXPERIMENT_TRAINING)
mlflow.set_experiment(EXPERIMENT_TRAINING)
training_exp_id = exp_id

# --- Load data ---
train = pd.read_parquet(DATA_DIR / "train_features.parquet")
val = pd.read_parquet(DATA_DIR / "val_features.parquet")
test = pd.read_parquet(DATA_DIR / "test_features.parquet")

DROP_COLS = ["nameOrig", "nameDest", "oldbalanceOrg", "newbalanceOrig",
             "oldbalanceDest", "newbalanceDest", "orig_error", "dest_error",
             "has_orig_error", "has_dest_error", "type", "amount", "step",
             "isFraud", "isFlaggedFraud"]

# --- Experiment definitions ---
GROUP_A = ["amount_tier_micro", "amount_tier_small", "amount_tier_medium"]
GROUP_D = ["is_transfer_and_draining", "is_medium_and_draining"]

EXPERIMENTS = [
    {"name": "E1", "description": "v3 baseline (no Group A, no Group D)",
     "v4_drop": GROUP_A + GROUP_D},
    {"name": "E2", "description": "+ Group A (amount tier)",
     "v4_drop": GROUP_D},
    {"name": "E3", "description": "+ Group A + Group D (interactions)",
     "v4_drop": []},
]

# --- Model configs ---
XGBOOST_PARAMS = {
    "n_estimators": 2000, "max_depth": 6, "learning_rate": 0.05,
    "subsample": 0.8, "colsample_bytree": 0.8,
    "eval_metric": "aucpr", "early_stopping_rounds": 50,
    "random_state": 42, "n_jobs": -1,
}

LGBM_CONFIGS = {
    "lgbm-v1-default": {
        "objective": "binary", "metric": "average_precision",
        "num_leaves": 31, "learning_rate": 0.05, "max_depth": -1,
        "min_child_samples": 20, "subsample": 0.8, "colsample_bytree": 0.8,
        "reg_alpha": 0.0, "reg_lambda": 1.0,
        "verbose": -1, "n_jobs": -1, "random_state": 42,
    },
    "lgbm-v2-deeper": {
        "objective": "binary", "metric": "average_precision",
        "num_leaves": 63, "learning_rate": 0.03, "max_depth": 8,
        "min_child_samples": 50, "subsample": 0.8, "colsample_bytree": 0.8,
        "reg_alpha": 0.1, "reg_lambda": 1.0,
        "verbose": -1, "n_jobs": -1, "random_state": 42,
    },
}

# --- Scale pos weight ---
y_train = train["isFraud"]
scale_pos_weight = (y_train == 0).sum() / (y_train == 1).sum()
XGBOOST_PARAMS["scale_pos_weight"] = scale_pos_weight
for cfg in LGBM_CONFIGS.values():
    cfg["scale_pos_weight"] = scale_pos_weight

print(f"scale_pos_weight: {scale_pos_weight:.2f}")

# Get git SHA
try:
    git_sha = subprocess.check_output(
        ["git", "rev-parse", "--short", "HEAD"],
        cwd="/Users/ngocduy/PROJECTS/smart-banking-system",
        stderr=subprocess.DEVNULL
    ).decode().strip()
except:
    git_sha = "unknown"

timestamp = time.strftime("%Y%m%d-%H%M%S")
log_lines = [f"=== Feature experiment sweep @ {timestamp} ===\n"]
results = []

for exp_def in EXPERIMENTS:
    name = exp_def["name"]
    desc = exp_def["description"]
    v4_drop = exp_def["v4_drop"]

    base_features = [c for c in train.columns if c not in DROP_COLS and c not in v4_drop]

    log_lines.append(f"\n{'='*60}\n{name}: {desc}\n   Features: {len(base_features)} (dropped v4: {v4_drop})\n{'='*60}\n")

    X_train = train[base_features]
    X_val = val[base_features]
    X_test = test[base_features]
    y_test = test["isFraud"]

    # --- 1. XGBoost ---
    xgb_run_name = f"xgb-{name}-{timestamp}"
    print(f"\n📦 {xgb_run_name}...")
    log_lines.append(f"📦 {xgb_run_name}...\n")
    with mlflow.start_run(run_name=xgb_run_name):
        mlflow.log_param("experiment", name)
        mlflow.log_param("description", desc)
        mlflow.log_param("feature_set", f"{name}: {desc}")
        mlflow.log_param("feature_count", len(base_features))
        mlflow.log_param("model_type", "xgboost")
        mlflow.log_param("model_version", "v1.0.0")
        mlflow.log_param("git_sha", git_sha)
        mlflow.log_param("scale_pos_weight", scale_pos_weight)

        xgb_model = xgb.XGBClassifier(**XGBOOST_PARAMS)
        train_start = time.perf_counter()
        xgb_model.fit(X_train, y_train, eval_set=[(X_val, val["isFraud"])], verbose=False)
        train_seconds = time.perf_counter() - train_start

        y_prob = xgb_model.predict_proba(X_test)[:, 1]
        pr_auc = average_precision_score(y_test, y_prob)
        roc_auc = roc_auc_score(y_test, y_prob)

        def metrics_at_threshold(threshold):
            pred = (y_prob >= threshold).astype(int)
            return {
                "precision": precision_score(y_test, pred, zero_division=0),
                "recall": recall_score(y_test, pred),
                "f1": f1_score(y_test, pred),
                "flagged_count": int(pred.sum()),
                "flagged_pct": float(pred.mean() * 100),
            }

        m05 = metrics_at_threshold(0.5)
        m08 = metrics_at_threshold(0.8)

        # Inference latency (median over 100 calls on a single row)
        single = X_test.iloc[:1]
        timings = []
        for _ in range(100):
            t0 = time.perf_counter()
            _ = xgb_model.predict_proba(single)
            timings.append((time.perf_counter() - t0) * 1000)
        inf_ms = float(np.median(timings))

        # Model size
        model_bytes = pickle.dumps(xgb_model)
        model_size_mb = len(model_bytes) / (1024 * 1024)

        mlflow.log_metric("test_pr_auc", pr_auc)
        mlflow.log_metric("test_roc_auc", roc_auc)
        mlflow.log_metric("f1_at_0.5", m05["f1"])
        mlflow.log_metric("f1_at_0.8", m08["f1"])
        mlflow.log_metric("precision_at_0.5", m05["precision"])
        mlflow.log_metric("recall_at_0.5", m05["recall"])
        mlflow.log_metric("precision_at_0.8", m08["precision"])
        mlflow.log_metric("recall_at_0.8", m08["recall"])
        mlflow.log_metric("flagged_pct_at_0.5", m05["flagged_pct"])
        mlflow.log_metric("inference_ms", inf_ms)
        mlflow.log_metric("model_size_mb", model_size_mb)
        mlflow.log_metric("best_iteration", int(xgb_model.best_iteration))
        mlflow.log_metric("train_seconds", train_seconds)

        msg = f"   PR-AUC={pr_auc:.4f} | ROC-AUC={roc_auc:.4f} | F1@0.5={m05['f1']:.4f} | inf={inf_ms:.2f}ms"
        print(msg)
        log_lines.append(msg + "\n")

        results.append({
            "experiment": name, "model": "xgboost", "feature_count": len(base_features),
            "pr_auc": pr_auc, "roc_auc": roc_auc, "f1_at_0.5": m05["f1"],
            "recall_at_0.5": m05["recall"], "precision_at_0.5": m05["precision"],
            "inference_ms": inf_ms,
            "model_size_mb": model_size_mb, "train_seconds": train_seconds,
        })

    # --- 2. LightGBM sweep ---
    for cfg_name, cfg_params in LGBM_CONFIGS.items():
        lgbm_run_name = f"{cfg_name}-{name}-{timestamp}"
        print(f"\n📦 {lgbm_run_name}...")
        log_lines.append(f"\n📦 {lgbm_run_name}...\n")
        with mlflow.start_run(run_name=lgbm_run_name):
            mlflow.log_param("experiment", name)
            mlflow.log_param("description", desc)
            mlflow.log_param("feature_set", f"{name}: {desc}")
            mlflow.log_param("feature_count", len(base_features))
            mlflow.log_param("model_type", "lightgbm")
            mlflow.log_param("model_version", cfg_name)
            mlflow.log_param("git_sha", git_sha)
            mlflow.log_param("scale_pos_weight", scale_pos_weight)

            train_set = lgb.Dataset(X_train, label=y_train)
            val_set = lgb.Dataset(X_val, label=val["isFraud"], reference=train_set)

            train_start = time.perf_counter()
            booster = lgb.train(
                cfg_params, train_set, num_boost_round=2000,
                valid_sets=[val_set], valid_names=["val"],
                callbacks=[lgb.early_stopping(50, verbose=False)],
            )
            train_seconds = time.perf_counter() - train_start

            y_prob = booster.predict(X_test, num_iteration=booster.best_iteration)
            pr_auc = average_precision_score(y_test, y_prob)
            roc_auc = roc_auc_score(y_test, y_prob)

            def metrics_at_threshold(threshold):
                pred = (y_prob >= threshold).astype(int)
                return {
                    "precision": precision_score(y_test, pred, zero_division=0),
                    "recall": recall_score(y_test, pred),
                    "f1": f1_score(y_test, pred),
                    "flagged_count": int(pred.sum()),
                    "flagged_pct": float(pred.mean() * 100),
                }

            m05 = metrics_at_threshold(0.5)
            m08 = metrics_at_threshold(0.8)

            timings = []
            for _ in range(100):
                t0 = time.perf_counter()
                _ = booster.predict(single, num_iteration=booster.best_iteration)
                timings.append((time.perf_counter() - t0) * 1000)
            inf_ms = float(np.median(timings))

            # Model size
            model_bytes = pickle.dumps(booster)
            model_size_mb = len(model_bytes) / (1024 * 1024)

            mlflow.log_metric("train_seconds", train_seconds)
            mlflow.log_metric("best_iteration", int(booster.best_iteration))
            mlflow.log_metric("test_pr_auc", pr_auc)
            mlflow.log_metric("test_roc_auc", roc_auc)
            mlflow.log_metric("f1_at_0.5", m05["f1"])
            mlflow.log_metric("f1_at_0.8", m08["f1"])
            mlflow.log_metric("precision_at_0.5", m05["precision"])
            mlflow.log_metric("recall_at_0.5", m05["recall"])
            mlflow.log_metric("precision_at_0.8", m08["precision"])
            mlflow.log_metric("recall_at_0.8", m08["recall"])
            mlflow.log_metric("inference_ms", inf_ms)
            mlflow.log_metric("model_size_mb", model_size_mb)
            mlflow.log_metric("flagged_pct_at_0.5", m05["flagged_pct"])

            msg = f"   PR-AUC={pr_auc:.4f} | ROC-AUC={roc_auc:.4f} | F1@0.5={m05['f1']:.4f} | inf={inf_ms:.2f}ms | {model_size_mb:.2f}MB"
            print(msg)
            log_lines.append(msg + "\n")

            results.append({
                "experiment": name, "model": cfg_name, "feature_count": len(base_features),
                "pr_auc": pr_auc, "roc_auc": roc_auc, "f1_at_0.5": m05["f1"],
                "recall_at_0.5": m05["recall"], "precision_at_0.5": m05["precision"],
                "inference_ms": inf_ms, "model_size_mb": model_size_mb, "train_seconds": train_seconds,
            })

# --- Summary ---
log_lines.append(f"\n\n{'='*78}\nFEATURE EXPERIMENT COMPARISON (sorted by PR-AUC)\n{'='*78}\n")
df = pd.DataFrame(results).sort_values("pr_auc", ascending=False).reset_index(drop=True)
summary = df.to_string(index=False)
print("\n" + summary)
log_lines.append(summary + "\n")

log_lines.append(f"{'='*78}\nBest: {df.iloc[0]['experiment']}/{df.iloc[0]['model']} (PR-AUC={df.iloc[0]['pr_auc']:.4f})\n")
log_lines.append("\nBest PR-AUC per experiment:\n")
for exp_name in ["E1", "E2", "E3"]:
    sub = df[df["experiment"] == exp_name]
    if len(sub) > 0:
        best = sub.iloc[0]
        line = f"  {exp_name}: {best['model']} PR-AUC={best['pr_auc']:.4f} (features={best['feature_count']})"
        log_lines.append(line + "\n")
        print(line)

# Write log
LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
LOG_PATH.write_text("".join(log_lines))
print(f"\nLog written to {LOG_PATH}")
print(f"\n🔗 View in MLflow UI: http://localhost:5050/#/experiments/{exp_id}/runs")
