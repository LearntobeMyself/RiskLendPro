"""
阶段 A3：独立的 OOT / PSI / 校准评估脚本。

不重新训练，只加载 output/offline_runs/<dataset>/model_artifact.json 中的
WOE 定义与系数，在时间外切分上重新计算指标，保证「训练脚本」和「评估脚本」
可以独立、重复地跑出同一结果。
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys

import numpy as np
import pandas as pd
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score

from build_asof_features import load_catalog
from train_b_card_model import (CATALOG_PATH, RUNS_DIR, calibration_table, ks_statistic,
                                lift_at, load_training_frame, split_by_vintage)
from woe_binning import population_stability_index, transform_frame_woe

REQUIRED_PERIOD_METRICS = ("rows", "bad_rate", "auc", "ks", "pr_auc", "lift_top10", "brier")


def _sigmoid(value):
    return 1.0 / (1.0 + np.exp(-value))


def score_artifact(artifact, frame):
    x = transform_frame_woe(frame, artifact["feature_defs"])
    linear = artifact["intercept"] + sum(
        float(artifact["coefficients"][name]) * x[name].values.astype(float)
        for name in artifact["selected_features"])
    return _sigmoid(linear)


def evaluate(dataset: str, snapshot_path: str = None, run_dir: str = None,
             label_col: str = "label_30dpd_6m", out_path: str = None) -> dict:
    run_dir = run_dir or os.path.join(RUNS_DIR, dataset)
    artifact_path = os.path.join(run_dir, "model_artifact.json")
    if not os.path.isfile(artifact_path):
        raise FileNotFoundError(f"缺少模型产物 {artifact_path}，请先运行 train_b_card_model.py")
    with io.open(artifact_path, encoding="utf-8") as handle:
        artifact = json.load(handle)

    frame, catalog, source_path = load_training_frame(dataset, snapshot_path)
    frame = frame[frame[label_col].notna()].copy()
    frame[label_col] = frame[label_col].astype(int)
    train_df, valid_df, oot_df, split = split_by_vintage(frame, label_col)

    periods = {}
    proba_by_period = {}
    for name, sub in (("train", train_df), ("valid", valid_df), ("oot", oot_df)):
        if len(sub) == 0:
            continue
        proba = score_artifact(artifact, sub)
        proba_by_period[name] = proba
        y = sub[label_col].values.astype(int)
        periods[name] = {
            "rows": int(len(sub)),
            "bad_rate": round(float(y.mean()), 6),
            "auc": round(float(roc_auc_score(y, proba)), 6) if len(set(y.tolist())) > 1 else 0.0,
            "ks": round(ks_statistic(y, proba), 6),
            "pr_auc": round(float(average_precision_score(y, proba)), 6) if len(set(y.tolist())) > 1 else 0.0,
            "lift_top10": round(lift_at(y, proba, 0.1), 6),
            "brier": round(float(brier_score_loss(y, np.clip(proba, 1e-6, 1 - 1e-6))), 6),
        }

    # 特征级与分数级 PSI：训练期 vs OOT
    feature_psi = {}
    for name in artifact["selected_features"]:
        feature_psi[name] = round(population_stability_index(
            pd.to_numeric(train_df[name], errors="coerce").values.astype(float),
            pd.to_numeric(oot_df[name], errors="coerce").values.astype(float)), 6)
    score_psi = round(population_stability_index(
        proba_by_period.get("train", np.array([])), proba_by_period.get("oot", np.array([]))), 6)

    calibration = calibration_table(oot_df[label_col].values.astype(int), proba_by_period["oot"]) \
        if "oot" in proba_by_period else []

    report = {
        "dataset": dataset,
        "deployable": dataset == "snapshot",
        "dataset_path": source_path,
        "label_column": label_col,
        "label_version": catalog["label_version"],
        "feature_version": catalog["feature_version"],
        "catalog_version": catalog["catalog_version"],
        "split": split,
        "periods": periods,
        "psi": {"score_train_vs_oot": score_psi, "features_train_vs_oot": feature_psi},
        "max_feature_psi": max(feature_psi.values()) if feature_psi else 0.0,
        "calibration_oot": calibration,
        "selected_features": artifact["selected_features"],
    }

    for name, block in periods.items():
        missing = [key for key in REQUIRED_PERIOD_METRICS if key not in block]
        if missing:
            raise ValueError(f"{name} 期指标缺失: {missing}")
    if not calibration:
        raise ValueError("OOT 校准表为空")

    out_path = out_path or os.path.join(run_dir, "oot_report.json")
    with io.open(out_path, "w", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2, sort_keys=True)
    print(f"OOT 报告已写出 -> {out_path}")
    print(f"OOT: AUC={periods['oot']['auc']:.4f} KS={periods['oot']['ks']:.4f} "
          f"Lift@10%={periods['oot']['lift_top10']:.2f} score_PSI={score_psi:.4f} "
          f"max_feature_PSI={report['max_feature_psi']:.4f}")
    if dataset != "snapshot":
        print("[警告] fixture 数据集，仅用于验证评估流程，不得作为上线依据")
    return report


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="B 卡 OOT/PSI/校准评估（阶段 A3）")
    parser.add_argument("--dataset", choices=["snapshot", "fixture"], default="snapshot")
    parser.add_argument("--snapshot-export", default=None)
    parser.add_argument("--run-dir", default=None)
    parser.add_argument("--label", default="label_30dpd_6m")
    parser.add_argument("--out", default=None)
    args = parser.parse_args(argv)
    try:
        evaluate(dataset=args.dataset, snapshot_path=args.snapshot_export,
                 run_dir=args.run_dir, label_col=args.label, out_path=args.out)
    except FileNotFoundError as exc:
        print(f"[失败] {exc}", file=sys.stderr)
        return 2
    except ValueError as exc:
        print(f"[失败] {exc}", file=sys.stderr)
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main())