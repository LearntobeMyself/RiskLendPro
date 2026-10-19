"""
B 卡（贷后行为）训练与 OOT 评估 —— 阶段 A3。

与旧版的关键区别：
1. 训练数据来自 A2 产出的 as-of 快照（真实贷后行为），不再以 Home Credit 静态
   inst_/pos_ 特征作为唯一输入；
2. 切分改为按 vintage（as_of_date）的时间外切分，禁止随机切分；
3. 特征走 IV + 单调性 + 相关性 + 稳定性(PSI) 四道筛选，恒定/噪声/共线特征不入模；
4. 同时给出 WOE+LR 可解释基线与树模型挑战者（LightGBM / XGBoost / HistGradientBoosting /
   GradientBoosting 按可用性降级选择）；
5. 输出 AUC、KS、Lift、PR-AUC、PSI、分期坏账率与校准表，并记录配置/数据版本/
   特征版本/模型哈希，保证可重复；
6. 只有真实快照数据集（--dataset snapshot）才标记 deployable=true，
   且本脚本**永远不写** output/b_scoring_rules.json —— 上线必须走 A1 的发布/激活流程。
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import sys
from datetime import datetime

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score

from build_asof_features import load_catalog, load_snapshot_export, online_feature_names
from woe_binning import (fit_feature_woe, population_stability_index,
                         select_features_with_governance, transform_frame_woe)

PARQUET_PATH = "data/raw/home_credit_train_min.parquet"
CATALOG_PATH = os.path.join("output", "b_card_feature_catalog.json")
REAL_SNAPSHOT_EXPORT = os.path.join("data", "asof", "b_card_asof_snapshots.csv")
FIXTURE_EXPORT = os.path.join("output", "fixture", "b_card_asof_training_fixture.csv")
RUNS_DIR = os.path.join("output", "offline_runs")
LEGACY_RULES_PATH = os.path.join("output", "b_scoring_rules.json")
MAX_FEATURES = 12
MIN_NON_NULL = 0.5


# ---------------------------------------------------------------------------
# 旧辅助函数：仍被 clean_behavior_features.py 使用，保持向后兼容
# ---------------------------------------------------------------------------

def load_hc_frame() -> pd.DataFrame:
    if os.path.isfile(PARQUET_PATH):
        df = pd.read_parquet(PARQUET_PATH)
        print(f"读取 parquet: {PARQUET_PATH} ({len(df)} 行, {len(df.columns)} 列)")
        return df
    print("未找到 parquet，使用合成 B 卡行为特征")
    return synthesize_b_card_frame()


def synthesize_b_card_frame(n: int = 8000) -> pd.DataFrame:
    rng = np.random.RandomState(42)  # 兼容 numpy 1.15（default_rng 需要 1.17+）
    df = pd.DataFrame(
        {
            "inst_dpd_max": rng.randint(0, 120, size=n),
            "inst_dpd_mean": rng.uniform(0, 60, size=n),
            "inst_amt_payment": rng.uniform(0, 50000, size=n),
            "inst_cnt": rng.randint(0, 20, size=n),
            "pos_dpd_max": rng.randint(0, 90, size=n),
            "pos_cnt": rng.randint(0, 15, size=n),
        }
    )
    risk = (df["inst_dpd_max"] > 30) | (df["pos_dpd_max"] > 20) | (df["inst_cnt"] > 12)
    df["TARGET"] = ((risk.astype(int) + rng.random_sample(n)) < 0.4).astype(int)
    return df


def pick_b_card_columns(df: pd.DataFrame) -> list:
    candidates = [c for c in df.columns
                  if c.lower().startswith(("inst_", "pos_")) and pd.api.types.is_numeric_dtype(df[c])]
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


# ---------------------------------------------------------------------------
# A3：as-of 训练集
# ---------------------------------------------------------------------------

def make_training_fixture(path: str, seed: int = 20261013) -> pd.DataFrame:
    """生成仅用于流程验证的合成 as-of 训练集（不可上线）。

    刻意包含：恒定特征、纯噪声特征、与主特征完全共线的特征，
    用于验证四道筛选确实把它们挡在模型之外。
    """
    rng = np.random.RandomState(seed)
    vintages = ["2025-03-31", "2025-04-30", "2025-05-31",
                "2025-06-30", "2025-07-31", "2025-08-31"]
    rows = []
    for vintage_index, vintage in enumerate(vintages):
        for i in range(220):
            risk = rng.normal(0, 1)
            max_dpd12m = max(0, int(round(rng.gamma(2.0, 12.0) + risk * 18)))
            current_max_dpd = max(0, int(round(max_dpd12m * rng.uniform(0.4, 1.0))))
            late_count = max(0, int(round(max_dpd12m / 25.0 + rng.normal(0, 1))))
            utilization = float(np.clip(rng.beta(2.0, 3.0) + risk * 0.12, 0, 1.8))
            on_time = float(np.clip(1.0 - max_dpd12m / 240.0 + rng.normal(0, 0.08), 0, 1))
            partial = float(np.clip(0.1 + max_dpd12m / 600.0 + rng.normal(0, 0.05), 0, 1))
            tenure = float(max(30, 200 + rng.normal(0, 60)))
            outstanding = float(max(0.0, rng.gamma(2.0, 4200)))
            overdue_amount = float(max(0.0, max_dpd12m * rng.uniform(15, 60) - rng.uniform(0, 500)))
            total_limit = float(max(1000.0, rng.normal(20000, 6000)))
            used_limit = float(max(0.0, total_limit * utilization))
            remaining = float(max(0.0, total_limit - used_limit))
            logit = (-2.1 + 0.030 * max_dpd12m + 0.018 * current_max_dpd + 0.42 * utilization
                     + 0.35 * late_count - 1.4 * on_time + 0.00004 * overdue_amount)
            prob = 1.0 / (1.0 + math_exp(-logit))
            label30 = 1 if rng.random_sample() < prob else 0
            label90 = 1 if (label30 == 1 and rng.random_sample() < 0.35) else 0
            days_since = "" if max_dpd12m <= 0 else str(int(max(0, rng.normal(120, 60))))

            features = {
                "loanCountTotal": 1 + int(rng.random_sample() < 0.2),
                "activeLoanCount": 0,                      # 恒定 -> 退化
                "repaidLoanCount": 0,                      # 恒定 -> 退化
                "overdueLoanCount": 1 if max_dpd12m > 0 else 0,
                "overduePlanCount": 1 if max_dpd12m > 0 else 0,
                "currentMaxDpd": current_max_dpd,
                "maxDpd12m": max_dpd12m,
                "avgDpd12m": round(max_dpd12m / 2.0, 4),
                "latePaymentCount12m": late_count,
                "onTimeRate12m": round(on_time, 4),
                "partialPaymentRatio12m": round(partial, 4),
                "utilizationRate": round(utilization, 4),
                "cappedUtilizationRate": round(min(utilization, 2.0), 4),   # 与上者共线
                "overdueAmount": round(overdue_amount, 2),
                "outstandingAmount": round(outstanding, 2),
                "totalLimit": round(total_limit, 2),
                "usedLimit": round(used_limit, 2),
                "remainingLimit": round(remaining, 2),
                "recentNewLoanCount90d": int(rng.randint(0, 4)),            # 纯噪声
                "recentNewLoanCount180d": 3,                               # 恒定 -> 退化
                "daysSinceLastOverdue": days_since,
                "tenureDays": round(tenure, 1),
                "totalLoanAmount": round(total_limit * 0.35, 2),
                "avgLoanAmount": round(total_limit * 0.35, 2),              # 与上者共线
                "lastLoanAmount": round(total_limit * 0.30 + rng.normal(0, 200), 2),
                "recordCount": 6 + int(rng.randint(0, 6)),
                "paidRecordCount": 4 + int(rng.randint(0, 4)),
                "hasOverdue": bool(max_dpd12m > 0),
            }
            rows.append({
                "user_id": 100000 + vintage_index * 1000 + i,
                "as_of_date": vintage,
                "feature_version": "BF_V2.2",
                "sample_status": "LABELED",
                "label_version": "LBL_V1",
                "label_30dpd_6m": label30,
                "label_90dpd_12m": label90,
                "feature_json": json.dumps(features, ensure_ascii=False, sort_keys=True),
            })

    os.makedirs(os.path.dirname(path), exist_ok=True)
    frame = pd.DataFrame(rows)
    frame.to_csv(path, index=False, encoding="utf-8-sig")
    print(f"[fixture] 合成 as-of 训练集 {len(frame)} 行 -> {path}（不可上线）")
    return frame


def math_exp(value: float) -> float:
    return float(np.exp(value))


def load_training_frame(dataset: str, snapshot_path: str):
    catalog = load_catalog(CATALOG_PATH)
    if dataset == "snapshot":
        path = snapshot_path or REAL_SNAPSHOT_EXPORT
        rows, columns = load_snapshot_export(path, catalog)
    else:
        path = FIXTURE_EXPORT
        if not os.path.isfile(path):
            make_training_fixture(path)
        rows, columns = load_snapshot_export(path, catalog)
    frame = pd.DataFrame(rows)
    return frame, catalog, path


def split_by_vintage(frame: pd.DataFrame, label_col: str):
    """按 vintage 时间外切分：训练=较早、验证=中间、OOT=最新。"""
    vintages = sorted(frame["as_of_date"].unique())
    if len(vintages) < 3:
        raise ValueError(f"时间外验证至少需要 3 个 vintage，实际 {len(vintages)}")
    n = len(vintages)
    n_train = max(1, int(round(n * 0.6)))
    n_valid = max(1, int(round(n * 0.2)))
    if n_train + n_valid >= n:
        n_train, n_valid = n - 2, 1
    train_v = vintages[:n_train]
    valid_v = vintages[n_train:n_train + n_valid]
    oot_v = vintages[n_train + n_valid:]
    if not oot_v:
        raise ValueError("时间外切分后 OOT 为空")
    print(f"vintage 切分: train={train_v} valid={valid_v} oot={oot_v} label={label_col}")
    return (
        frame[frame["as_of_date"].isin(train_v)].copy(),
        frame[frame["as_of_date"].isin(valid_v)].copy(),
        frame[frame["as_of_date"].isin(oot_v)].copy(),
        {"train_vintages": train_v, "valid_vintages": valid_v, "oot_vintages": oot_v},
    )


def ks_statistic(y_true, proba) -> float:
    y = np.asarray(y_true, dtype=float)
    p = np.asarray(proba, dtype=float)
    order = np.argsort(p)
    y = y[order]
    bad_total = y.sum()
    good_total = len(y) - bad_total
    if bad_total == 0 or good_total == 0:
        return 0.0
    bad_cum = np.cumsum(y) / bad_total
    good_cum = np.cumsum(1.0 - y) / good_total
    return float(np.max(np.abs(bad_cum - good_cum)))


def lift_at(y_true, proba, frac: float = 0.1) -> float:
    y = np.asarray(y_true, dtype=float)
    p = np.asarray(proba, dtype=float)
    base = y.mean()
    if base <= 0:
        return 0.0
    k = max(1, int(round(len(p) * frac)))
    top = np.argsort(-p)[:k]
    return float(y[top].mean() / base)


def calibration_table(y_true, proba, n_bins: int = 10) -> list:
    y = np.asarray(y_true, dtype=float)
    p = np.asarray(proba, dtype=float)
    edges = np.unique(np.quantile(p, np.linspace(0, 1, n_bins + 1)))
    if len(edges) < 2:
        return []
    edges[0], edges[-1] = -np.inf, np.inf
    table = []
    for i in range(len(edges) - 1):
        mask = (p > edges[i]) & (p <= edges[i + 1])
        if mask.sum() == 0:
            continue
        table.append({"bucket": i + 1, "count": int(mask.sum()),
                      "avg_predicted": round(float(p[mask].mean()), 6),
                      "observed_bad_rate": round(float(y[mask].mean()), 6)})
    return table


def make_tree_model(seed: int):
    """按可用性降级选择树模型挑战者，返回 (名称, 模型)。"""
    try:
        from lightgbm import LGBMClassifier
        return "lightgbm", LGBMClassifier(n_estimators=300, learning_rate=0.05, num_leaves=31,
                                          subsample=0.9, colsample_bytree=0.9,
                                          random_state=seed, verbose=-1)
    except Exception:
        pass
    try:
        from xgboost import XGBClassifier
        return "xgboost", XGBClassifier(n_estimators=300, learning_rate=0.05, max_depth=4,
                                        subsample=0.9, colsample_bytree=0.9,
                                        random_state=seed, eval_metric="logloss")
    except Exception:
        pass
    try:
        from sklearn.ensemble import HistGradientBoostingClassifier
        return "hist_gradient_boosting", HistGradientBoostingClassifier(
            max_iter=300, learning_rate=0.05, random_state=seed)
    except Exception:
        pass
    from sklearn.ensemble import GradientBoostingClassifier
    return "gradient_boosting", GradientBoostingClassifier(random_state=seed, n_estimators=200,
                                                           learning_rate=0.05, max_depth=3)


def _sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _canonical(payload) -> str:
    return json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def train_and_evaluate(dataset: str = "fixture", snapshot_path: str = None,
                       out_dir: str = None, seed: int = 20261013,
                       label_col: str = "label_30dpd_6m") -> dict:
    out_dir = out_dir or os.path.join(RUNS_DIR, dataset)
    frame, catalog, source_path = load_training_frame(dataset, snapshot_path)
    online = online_feature_names(catalog)
    final_model_candidates = [f["name"] for f in catalog["features"]
                              if f.get("available_online") and f.get("in_final_model")]

    if label_col not in frame.columns:
        raise ValueError(f"训练集缺少标签列 {label_col}")
    frame = frame[frame[label_col].notna()].copy()
    frame[label_col] = frame[label_col].astype(int)

    train_df, valid_df, oot_df, split = split_by_vintage(frame, label_col)

    candidates = [c for c in final_model_candidates if c in frame.columns]
    y_train = train_df[label_col]
    selected, selection_report = select_features_with_governance(
        train_df, oot_df, candidates, y_train, min_iv=0.02, max_features=MAX_FEATURES,
        corr_threshold=0.8, min_monotonic=0.6, max_psi=0.25)
    if not selected:
        raise ValueError("四道筛选后没有任何特征入模，请检查数据质量")
    illegal = [c for c in selected if c not in online]
    if illegal:
        raise ValueError(f"入模特征不在在线可用集合内（会造成线上线下不一致）: {illegal}")

    feature_defs = {key: fit_feature_woe(train_df[key], y_train, key) for key in selected}
    x_train = transform_frame_woe(train_df, feature_defs)
    x_valid = transform_frame_woe(valid_df, feature_defs)
    x_oot = transform_frame_woe(oot_df, feature_defs)

    lr = LogisticRegression(class_weight="balanced", random_state=seed, max_iter=500,
                            C=0.5, solver="lbfgs")
    lr.fit(x_train, y_train)

    tree_name, tree = make_tree_model(seed)
    tree.fit(train_df[selected].astype(float), y_train)

    metrics = {
        "lr_baseline": _evaluate_model(lr, x_valid, valid_df, x_oot, oot_df, label_col, train_df, x_train),
        "tree_challenger": {
            "algorithm": tree_name,
            **_evaluate_model(tree, valid_df[selected].astype(float), valid_df,
                              oot_df[selected].astype(float), oot_df, label_col,
                              train_df[selected].astype(float), None),
        },
    }

    artifact = {
        "model_type": "b_card_asof",
        "algorithm": "logistic_regression_woe",
        "deployable": dataset == "snapshot",
        "selected_features": selected,
        "coefficients": {k: float(v) for k, v in zip(selected, lr.coef_[0])},
        "intercept": float(lr.intercept_[0]),
        "feature_defs": feature_defs,
    }
    model_hash = _sha256_text(_canonical(artifact))

    os.makedirs(out_dir, exist_ok=True)
    data_version = _sha256_text(_canonical(sorted(frame["user_id"].astype(str).tolist())))[:16]
    config = {
        "run_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "dataset": dataset,
        "dataset_path": source_path,
        "deployable": dataset == "snapshot",
        "data_version": data_version,
        "rows": int(len(frame)),
        "label_column": label_col,
        "label_version": catalog["label_version"],
        "feature_version": catalog["feature_version"],
        "catalog_version": catalog["catalog_version"],
        "split": split,
        "seed": seed,
        "sklearn_version": _sklearn_version(),
        "model_hash": model_hash,
        "tree_algorithm": tree_name,
    }

    _dump(os.path.join(out_dir, "config.json"), config)
    _dump(os.path.join(out_dir, "feature_selection.json"), selection_report)
    _dump(os.path.join(out_dir, "metrics.json"), metrics)
    _dump(os.path.join(out_dir, "model_artifact.json"), artifact)
    _dump(os.path.join(out_dir, "calibration_oot.json"),
          metrics["lr_baseline"]["calibration"])
    with io.open(os.path.join(out_dir, "model_hash.txt"), "w", encoding="utf-8") as handle:
        handle.write(model_hash + "\n")

    if dataset != "snapshot":
        print("[警告] 当前使用合成 fixture 数据集，结果仅用于验证流程，不得作为上线依据；"
              "产出目录标记 deployable=false")
    print(f"训练完成: LR OOT AUC={metrics['lr_baseline']['oot']['auc']:.4f} "
          f"KS={metrics['lr_baseline']['oot']['ks']:.4f} "
          f"| {tree_name} OOT AUC={metrics['tree_challenger']['oot']['auc']:.4f}")
    print(f"模型哈希={model_hash} 产出目录={out_dir}")
    return {"config": config, "metrics": metrics, "selected_features": selected,
            "model_hash": model_hash, "out_dir": out_dir}


def _evaluate_model(model, x_valid, valid_df, x_oot, oot_df, label_col, x_train, x_train_transformed):
    def _block(x, df):
        if len(df) == 0:
            return {}
        proba = model.predict_proba(x)[:, 1]
        y = df[label_col].values.astype(int)
        block = {
            "rows": int(len(df)),
            "bad_rate": round(float(y.mean()), 6),
            "auc": round(float(roc_auc_score(y, proba)), 6) if len(set(y.tolist())) > 1 else 0.0,
            "ks": round(ks_statistic(y, proba), 6),
            "pr_auc": round(float(average_precision_score(y, proba)), 6) if len(set(y.tolist())) > 1 else 0.0,
            "lift_top10": round(lift_at(y, proba, 0.1), 6),
            "brier": round(float(brier_score_loss(y, np.clip(proba, 1e-6, 1 - 1e-6))), 6),
            "bad_rate_by_vintage": {
                str(v): round(float(g.mean()), 6)
                for v, g in pd.Series(y, index=df["as_of_date"].values).groupby(level=0)
            },
        }
        return block, proba

    valid_block = _block(x_valid, valid_df)
    oot_block = _block(x_oot, oot_df)
    valid_metrics = valid_block[0] if valid_block else {}
    oot_metrics, oot_proba = oot_block[0], (oot_block[1] if oot_block else None)

    score_psi = 0.0
    if x_train_transformed is not None and oot_proba is not None:
        train_proba = model.predict_proba(x_train_transformed)[:, 1]
        score_psi = population_stability_index(train_proba, oot_proba, n_bins=10)

    calibration = calibration_table(oot_df[label_col].values.astype(int), oot_proba) if oot_proba is not None else []
    return {
        "valid": valid_metrics,
        "oot": {**oot_metrics, "score_psi_train_vs_oot": round(float(score_psi), 6)},
        "calibration": calibration,
    }


def _sklearn_version() -> str:
    try:
        import sklearn
        return sklearn.__version__
    except Exception:
        return "unknown"


def _dump(path: str, payload) -> None:
    with io.open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2, sort_keys=True)


def train_b_card_model() -> dict:
    """默认入口：使用 fixture 数据集验证流程（真实训练请用 --dataset snapshot）。"""
    return train_and_evaluate(dataset="fixture")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="B 卡 as-of 训练与 OOT 评估（阶段 A3）")
    parser.add_argument("--dataset", choices=["snapshot", "fixture"], default="snapshot")
    parser.add_argument("--snapshot-export", default=None)
    parser.add_argument("--out-dir", default=None)
    parser.add_argument("--label", default="label_30dpd_6m")
    parser.add_argument("--seed", type=int, default=20261013)
    parser.add_argument("--make-fixture", action="store_true",
                        help="只生成合成 fixture 训练集（不可上线）后退出")
    args = parser.parse_args(argv)

    if args.make_fixture:
        make_training_fixture(FIXTURE_EXPORT)
        return 0

    try:
        train_and_evaluate(dataset=args.dataset, snapshot_path=args.snapshot_export,
                           out_dir=args.out_dir, seed=args.seed, label_col=args.label)
    except FileNotFoundError as exc:
        print(f"[失败] {exc}", file=sys.stderr)
        print("未生成任何可部署规则。请先完成 A2 快照与标签的积累后再训练。", file=sys.stderr)
        return 2
    except ValueError as exc:
        print(f"[失败] {exc}", file=sys.stderr)
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main())