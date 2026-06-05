"""
从 HC parquet + cleaned_user_features.csv 对齐 id_card，导出 B 卡行为特征快照。
"""
from __future__ import annotations

import json
import os

import pandas as pd

from train_b_card_model import PARQUET_PATH, load_hc_frame, pick_b_card_columns

USER_CSV = "data/cleaned/cleaned_user_features.csv"
RULES_PATH = "output/b_scoring_rules.json"
OUTPUT_PATH = "data/cleaned/cleaned_behavior_features.csv"


def clean_behavior_features() -> None:
    if not os.path.isfile(USER_CSV):
        raise FileNotFoundError(f"请先运行 clean_user_features.py，缺少 {USER_CSV}")

    users = pd.read_csv(USER_CSV, encoding="utf-8-sig")
    if os.path.isfile(RULES_PATH):
        with open(RULES_PATH, encoding="utf-8") as f:
            rules = json.load(f)
        feature_cols = rules.get("selected_features") or list(rules.get("coefficients", {}).keys())
    else:
        hc = load_hc_frame()
        feature_cols = pick_b_card_columns(hc)

    if os.path.isfile(PARQUET_PATH):
        hc = pd.read_parquet(PARQUET_PATH)
    else:
        hc = load_hc_frame()

    if "SK_ID_CURR" in hc.columns:
        hc = hc.rename(columns={"SK_ID_CURR": "sk_id_curr"})
    elif "sk_id_curr" not in hc.columns:
        raise ValueError("parquet 缺少 SK_ID_CURR")

    for c in feature_cols:
        if c not in hc.columns:
            hc[c] = 0.0

    subset = hc[["sk_id_curr"] + feature_cols].copy()
    merged = users[["sk_id_curr", "id_card"]].merge(subset, on="sk_id_curr", how="left")

    for c in feature_cols:
        merged[c] = pd.to_numeric(merged[c], errors="coerce").fillna(0)

    out_cols = ["sk_id_curr", "id_card"] + feature_cols
    merged = merged[out_cols]
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    merged.to_csv(OUTPUT_PATH, index=False, encoding="utf-8-sig")
    print(f"B 卡行为特征: {len(merged)} 条 -> {OUTPUT_PATH}")


if __name__ == "__main__":
    clean_behavior_features()
