"""
B 卡（贷后行为）训练：从 Home Credit parquet 选取 inst_/pos_ 列，WOE+LR+PDO。
输出 output/b_scoring_rules.json，与 A 卡 scoring_rules.json 物理分离。
"""
from __future__ import annotations

import json
import os

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, roc_auc_score
from sklearn.model_selection import train_test_split

from train_scoring_model import (
    calibrate_odds_pdo_scorecard,
    scores_odds_pdo_batch,
)
from woe_binning import fit_feature_woe, select_features, transform_frame_woe

PARQUET_PATH = "data/raw/home_credit_train_min.parquet"
OUTPUT_PATH = "output/b_scoring_rules.json"
MAX_FEATURES = 7
MIN_NON_NULL = 0.5


def load_hc_frame() -> pd.DataFrame:
    if os.path.isfile(PARQUET_PATH):
        df = pd.read_parquet(PARQUET_PATH)
        print(f"读取 parquet: {PARQUET_PATH} ({len(df)} 行, {len(df.columns)} 列)")
        return df
    print("未找到 parquet，使用合成 B 卡行为特征")
    return synthesize_b_card_frame()


def synthesize_b_card_frame(n: int = 8000) -> pd.DataFrame:
    rng = np.random.default_rng(42)
    df = pd.DataFrame(
        {
            "inst_dpd_max": rng.integers(0, 120, size=n),
            "inst_dpd_mean": rng.uniform(0, 60, size=n),
            "inst_amt_payment": rng.uniform(0, 50000, size=n),
            "inst_cnt": rng.integers(0, 20, size=n),
            "pos_dpd_max": rng.integers(0, 90, size=n),
            "pos_cnt": rng.integers(0, 15, size=n),
        }
    )
    risk = (
        (df["inst_dpd_max"] > 30)
        | (df["pos_dpd_max"] > 20)
        | (df["inst_cnt"] > 12)
    )
    df["TARGET"] = (risk.astype(int) + rng.random(n) < 0.4).astype(int)
    return df


def pick_b_card_columns(df: pd.DataFrame) -> list[str]:
    candidates = [
        c
        for c in df.columns
        if c.lower().startswith(("inst_", "pos_"))
        and pd.api.types.is_numeric_dtype(df[c])
    ]
    scored = []
    for c in candidates:
        miss = float(df[c].isna().mean())
        if miss > 1.0 - MIN_NON_NULL:
            continue
        scored.append((c, miss, df[c].nunique()))
    scored.sort(key=lambda t: (t[1], -t[2]))
    selected = [c for c, _, _ in scored[:MAX_FEATURES]]
    if len(selected) < 3:
        for c in ["inst_dpd_max", "inst_dpd_mean", "pos_dpd_max", "active_loans_count"]:
            if c in df.columns and c not in selected:
                selected.append(c)
        selected = selected[:MAX_FEATURES]
    print(f"B 卡候选列 ({len(selected)}): {selected}")
    return selected


def train_b_card_model() -> dict:
    df = load_hc_frame()
    if "TARGET" not in df.columns:
        raise ValueError("缺少 TARGET 列")
    df = df.copy()
    df["defaulted"] = df["TARGET"].astype(int)

    selected = pick_b_card_columns(df)
    if not selected:
        raise ValueError("未找到可用的 inst_/pos_ 行为列")

    for c in selected:
        df[c] = pd.to_numeric(df[c], errors="coerce")
        df[c] = df[c].fillna(df[c].median() if df[c].notna().any() else 0)

    y = df["defaulted"].astype(int)
    try:
        train_idx, test_idx = train_test_split(
            df.index, test_size=0.2, random_state=42, stratify=y
        )
    except ValueError:
        train_idx, test_idx = train_test_split(df.index, test_size=0.2, random_state=42)

    train_df = df.loc[train_idx]
    test_df = df.loc[test_idx]
    y_train = y.loc[train_idx]
    y_test = y.loc[test_idx]

    iv_report = []
    for key in selected:
        from woe_binning import iv_numeric

        iv = iv_numeric(train_df[key], y_train)
        iv_report.append({"feature": key, "iv": float(iv), "selected": True})

    feature_defs = {key: fit_feature_woe(train_df[key], y_train, key) for key in selected}
    X_train = transform_frame_woe(train_df, feature_defs)
    X_test = transform_frame_woe(test_df, feature_defs)

    model = LogisticRegression(
        class_weight="balanced", random_state=42, max_iter=500, C=0.5, solver="lbfgs"
    )
    model.fit(X_train, y_train)

    y_pred_proba = model.predict_proba(X_test)[:, 1]
    accuracy = accuracy_score(y_test, model.predict(X_test))
    auc = roc_auc_score(y_test, y_pred_proba)

    coefficients = {k: float(v) for k, v in zip(selected, model.coef_[0])}
    scorecard_cfg = calibrate_odds_pdo_scorecard(y_pred_proba, min_s=350, max_s=950, span_frac=0.82)
    _, clipped = scores_odds_pdo_batch(y_pred_proba, scorecard_cfg)
    watch = float(np.quantile(clipped, 0.55))
    reduce_limit = float(np.quantile(clipped, 0.35))

    rules = {
        "version": "v1.0-b-woe",
        "model_type": "b_card_woe",
        "card_type": "B",
        "description": "B卡：HC inst_/pos_ 行为特征 WOE+LR+PDO",
        "selected_features": selected,
        "feature_derivation": {k: "来源=HC行为聚合; B卡入模" for k in selected},
        "features": feature_defs,
        "coefficients": coefficients,
        "intercept": float(model.intercept_[0]),
        "thresholds": {
            "watch": round(watch, 1),
            "reduce_limit": round(reduce_limit, 1),
            "_note": "贷后 B 分：低于 reduce_limit 大幅降额",
        },
        "scorecard": scorecard_cfg,
        "training_data": {
            "total_count": len(df),
            "default_rate": float(y.mean()),
            "data_source": "Home Credit inst_/pos_",
        },
        "model_metrics": {"accuracy": float(accuracy), "auc": float(auc)},
        "iv_table": iv_report,
    }

    os.makedirs("output", exist_ok=True)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(rules, f, ensure_ascii=False, indent=2)

    print(f"B 卡训练完成 AUC={auc:.4f} watch={rules['thresholds']['watch']} reduce={rules['thresholds']['reduce_limit']}")
    print(f"已写入 {OUTPUT_PATH}")
    return rules


if __name__ == "__main__":
    train_b_card_model()
