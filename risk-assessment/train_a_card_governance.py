"""
阶段 A5+：A 卡（申请评分）模型治理与优化。

针对现有 A 卡模型的三点问题给出可运行方案：
1. 原来的训练使用随机切分（train_test_split(random_state=42)），无法做时间外验证；
2. 概率未校准，阈值来自分位数（auto_approve/manual_review 由分位点推导）；
3. 外部队列（多头查询、换号天数、历史拒绝、逾期代理等）没有系统化进入特征治理。

本脚本在**合成 fixture**上完整跑通：
vintage 时间外切分 → IV/单调性/相关性/PSI 四道筛选 → WOE+LR 基线 vs 树模型挑战
→ Platt 校准 → 业务成本阈值 → OOT 指标/校准/PSI → 模型卡。

注意：fixture 仅用于验证逻辑（deployable=false），不覆盖线上 output/scoring_rules.json。
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys

import numpy as np
import pandas as pd
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score

from build_asof_features import load_catalog
from calibrate_b_card_model import (DEFAULT_COSTS, PDO_DEFAULT, apply_platt, fit_platt,
                                    optimize_thresholds, pdo_score)
from train_b_card_model import calibration_table, ks_statistic, lift_at
from woe_binning import (fit_feature_woe, population_stability_index,
                         select_features_with_governance, transform_frame_woe)

FIXTURE_PATH = os.path.join("data", "fixture", "a_card_application_fixture.csv")
REPORT_PATH = os.path.join("output", "a_card_model_report.json")
CANDIDATE_PATH = os.path.join("output", "a_card_scoring_rules_candidate.json")
CURRENT_RULES = os.path.join("output", "scoring_rules.json")

CANDIDATES = [
    "income_deviation_ratio", "credit_bureau_week", "credit_bureau_mon",
    "days_last_phone_change", "active_loans_count", "ext_source_2", "ext_source_3",
    "prev_refused_count", "loan_overdue_max_6m", "cc_utilization",
    "age_years", "employment_years", "has_house", "has_car",
]


def _sigmoid(x):
    return 1.0 / (1.0 + np.exp(-x))


def make_fixture(path: str, seed: int = 20261020) -> pd.DataFrame:
    rng = np.random.RandomState(seed)
    months = ["2024-07", "2024-08", "2024-09", "2024-10", "2024-11", "2024-12",
              "2025-01", "2025-02", "2025-03", "2025-04", "2025-05", "2025-06"]
    rows = []
    for mi, month in enumerate(months):
        for i in range(150):
            income = float(max(3000, rng.normal(12000, 5000)))
            income_dev = float(np.clip(abs(rng.normal(0.10, 0.18)), 0, 2.0))
            bureau_week = int(max(0, rng.poisson(2.0)))
            bureau_mon = int(max(bureau_week, rng.poisson(5.0)))
            phone_days = int(max(0, rng.exponential(260)))
            active_loans = int(max(0, rng.poisson(1.4)))
            ext2 = float(np.clip(rng.beta(5, 2), 0, 1))
            ext3 = float(np.clip(rng.beta(4, 2), 0, 1))
            refused = int(max(0, rng.poisson(0.7)))
            overdue = int(max(0, rng.poisson(0.5)))
            cc_util = float(np.clip(rng.beta(2, 3) + rng.normal(0, 0.08), 0, 2))
            age = float(np.clip(rng.normal(36, 9), 18, 70))
            employ = float(np.clip(rng.exponential(6), 0, 40))
            has_house = int(rng.random_sample() < 0.35)
            has_car = int(rng.random_sample() < 0.25)

            risk = (-3.2
                    + 1.1 * income_dev
                    + 0.16 * bureau_week
                    + 0.07 * bureau_mon
                    + 0.006 * max(0, 120 - phone_days)
                    + 0.30 * active_loans
                    + 0.55 * refused
                    + 0.85 * overdue
                    + 1.2 * cc_util
                    - 1.3 * ext2
                    - 1.1 * ext3
                    - 0.018 * age
                    - 0.015 * employ)
            prob = _sigmoid(risk)
            default = 1 if rng.random_sample() < prob else 0
            rows.append({
                "apply_id": 900000 + mi * 1000 + i,
                "apply_month": month,
                "income_deviation_ratio": round(income_dev, 4),
                "credit_bureau_week": bureau_week,
                "credit_bureau_mon": bureau_mon,
                "days_last_phone_change": phone_days,
                "active_loans_count": active_loans,
                "ext_source_2": round(ext2, 4),
                "ext_source_3": round(ext3, 4),
                "prev_refused_count": refused,
                "loan_overdue_max_6m": overdue,
                "cc_utilization": round(cc_util, 4),
                "age_years": round(age, 1),
                "employment_years": round(employ, 1),
                "has_house": has_house,
                "has_car": has_car,
                "monthly_income": round(income, 2),
                "label_default": default,
            })
    os.makedirs(os.path.dirname(path), exist_ok=True)
    frame = pd.DataFrame(rows)
    frame.to_csv(path, index=False, encoding="utf-8-sig")
    print(f"[fixture] A 卡申请样本 {len(frame)} 行 -> {path}（不可上线）")
    return frame


def load_fixture(path: str) -> pd.DataFrame:
    if not os.path.isfile(path):
        return make_fixture(path)
    return pd.read_csv(path)


def split_by_month(frame: pd.DataFrame):
    months = sorted(frame["apply_month"].unique())
    n = len(months)
    n_train = max(1, int(round(n * 0.6)))
    n_calib = max(1, int(round(n * 0.2)))
    train_m = months[:n_train]
    calib_m = months[n_train:n_train + n_calib]
    oot_m = months[n_train + n_calib:]
    return (frame[frame["apply_month"].isin(train_m)].copy(),
            frame[frame["apply_month"].isin(calib_m)].copy(),
            frame[frame["apply_month"].isin(oot_m)].copy(),
            {"train_months": train_m, "calib_months": calib_m, "oot_months": oot_m})


def _metrics(y, p):
    y = np.asarray(y, dtype=int)
    return {
        "rows": int(len(y)),
        "bad_rate": round(float(y.mean()), 6),
        "auc": round(float(roc_auc_score(y, p)), 6) if len(set(y.tolist())) > 1 else 0.0,
        "ks": round(ks_statistic(y, p), 6),
        "pr_auc": round(float(average_precision_score(y, p)), 6) if len(set(y.tolist())) > 1 else 0.0,
        "lift_top10": round(lift_at(y, p, 0.1), 6),
        "brier": round(float(brier_score_loss(y, np.clip(p, 1e-6, 1 - 1e-6))), 6),
    }


def run(fixture_path: str = FIXTURE_PATH) -> dict:
    frame = load_fixture(fixture_path)
    train_df, calib_df, oot_df, split = split_by_month(frame)
    y_train = train_df["label_default"].astype(int)  # 保持 Series，供 IV/单调性筛选按索引对齐

    selected, selection_report = select_features_with_governance(
        train_df, oot_df, CANDIDATES, y_train, min_iv=0.02, max_features=10,
        corr_threshold=0.8, min_monotonic=0.6, max_psi=0.25)
    if not selected:
        raise ValueError("A 卡候选特征全部被筛选掉")

    feature_defs = {k: fit_feature_woe(train_df[k], y_train, k) for k in selected}
    x_train = transform_frame_woe(train_df, feature_defs)
    x_calib = transform_frame_woe(calib_df, feature_defs)
    x_oot = transform_frame_woe(oot_df, feature_defs)

    lr = LogisticRegression(class_weight="balanced", random_state=42, max_iter=500,
                            C=0.5, solver="lbfgs")
    lr.fit(x_train, y_train)

    tree = GradientBoostingClassifier(random_state=42, n_estimators=200,
                                      learning_rate=0.05, max_depth=3)
    tree.fit(train_df[selected].astype(float), y_train)

    # 校准：独立 calib 集（未参与拟合）
    logit_calib = lr.intercept_[0] + np.sum(
        lr.coef_[0] * x_calib[selected].values.astype(float), axis=1)
    a, b = fit_platt(logit_calib, calib_df["label_default"].values.astype(int))
    logit_oot = lr.intercept_[0] + np.sum(
        lr.coef_[0] * x_oot[selected].values.astype(float), axis=1)
    p_raw_oot = _sigmoid(logit_oot)
    p_cal_oot = apply_platt(logit_oot, a, b)
    p_tree_oot = tree.predict_proba(oot_df[selected].astype(float))[:, 1]
    y_oot = oot_df["label_default"].values.astype(int)

    # 业务成本阈值（A 卡：以年化收入作为敞口代理）
    ead = (pd.to_numeric(oot_df["monthly_income"], errors="coerce").fillna(0).values * 12 * 0.5)
    chosen, top_candidates = optimize_thresholds(p_cal_oot, y_oot, ead, DEFAULT_COSTS)

    periods = {}
    for name, sub, x in (("train", train_df, x_train), ("calib", calib_df, x_calib), ("oot", oot_df, x_oot)):
        logits = lr.intercept_[0] + np.sum(lr.coef_[0] * x[selected].values.astype(float), axis=1)
        prob = apply_platt(logits, a, b)
        periods[name] = _metrics(sub["label_default"].values.astype(int), prob)

    train_scores = pdo_score(apply_platt(
        lr.intercept_[0] + np.sum(lr.coef_[0] * x_train[selected].values.astype(float), axis=1), a, b), PDO_DEFAULT)
    for name, sub, x in (("train", train_df, x_train), ("calib", calib_df, x_calib), ("oot", oot_df, x_oot)):
        logits = lr.intercept_[0] + np.sum(lr.coef_[0] * x[selected].values.astype(float), axis=1)
        periods[name]["score_psi_vs_train"] = round(float(population_stability_index(
            train_scores, pdo_score(apply_platt(logits, a, b), PDO_DEFAULT))), 6)

    current_metrics = None
    if os.path.isfile(CURRENT_RULES):
        with io.open(CURRENT_RULES, encoding="utf-8") as handle:
            current_metrics = json.load(handle).get("model_metrics")

    report = {
        "dataset": "fixture",
        "deployable": False,
        "split": split,
        "feature_selection": selection_report,
        "selected_features": selected,
        "calibration": {"method": "platt", "a": round(a, 8), "b": round(b, 8)},
        "pdo_mapping": PDO_DEFAULT,
        "cost_assumptions": DEFAULT_COSTS,
        "threshold_policy": {"chosen": chosen, "top_candidates": top_candidates,
                             "selection_rule": "概率网格上最小化业务总成本，满足审核容量，非分位数"},
        "metrics": {
            "lr_woe_raw_oot": _metrics(y_oot, p_raw_oot),
            "lr_woe_calibrated_oot": _metrics(y_oot, p_cal_oot),
            "tree_challenger_oot": _metrics(y_oot, p_tree_oot),
        },
        "periods_calibrated": periods,
        "reliability_curve_oot": calibration_table(y_oot, p_cal_oot),
        "lift_table_oot": [{"top_fraction": f, "lift": round(lift_at(y_oot, p_cal_oot, f), 6)}
                           for f in (0.05, 0.1, 0.2, 0.3)],
        "current_rule_reported_metrics": current_metrics,
        "note": "fixture 为合成数据，仅验证 A 卡模型治理与优化流程；不覆盖线上 scoring_rules.json",
    }
    with io.open(REPORT_PATH, "w", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2, sort_keys=True)

    candidate = {
        "version": "a-card-candidate-v8",
        "model_type": "hc_woe_lr",
        "description": "A 卡候选：时间外验证 + 四道筛选 + Platt 校准 + 业务成本阈值（fixture 验证版）",
        "selected_features": selected,
        "features": feature_defs,
        "coefficients": {k: float(v) for k, v in zip(selected, lr.coef_[0])},
        "intercept": float(lr.intercept_[0]),
        "thresholds": {
            "auto_approve": round(float(pdo_score(chosen["approve_below"], PDO_DEFAULT)), 1),
            "manual_review": round(float(pdo_score(chosen["reject_above"], PDO_DEFAULT)), 1),
            "calibration": {"a": round(a, 8), "b": round(b, 8)},
        },
        "scorecard": PDO_DEFAULT,
        "deployable": False,
    }
    with io.open(CANDIDATE_PATH, "w", encoding="utf-8") as handle:
        json.dump(candidate, handle, ensure_ascii=False, indent=2, sort_keys=True)

    print(f"入模特征({len(selected)}): {selected}")
    print(f"OOT: LR raw AUC={report['metrics']['lr_woe_raw_oot']['auc']:.4f} "
          f"LR calibrated AUC={report['metrics']['lr_woe_calibrated_oot']['auc']:.4f} "
          f"Brier={report['metrics']['lr_woe_calibrated_oot']['brier']:.4f} | "
          f"tree AUC={report['metrics']['tree_challenger_oot']['auc']:.4f}")
    print(f"阈值(业务成本): approve<{chosen['approve_below']} reject>={chosen['reject_above']} "
          f"review={chosen['review_rate']:.3f} cost={chosen['total_cost']}")
    print(f"报告 -> {REPORT_PATH}")
    return report


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="A 卡模型治理与优化（fixture 验证）")
    parser.add_argument("--fixture", default=FIXTURE_PATH)
    parser.add_argument("--make-fixture", action="store_true")
    args = parser.parse_args(argv)
    if args.make_fixture:
        make_fixture(args.fixture)
        return 0
    try:
        run(args.fixture)
    except Exception as exc:
        print(f"[失败] {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())