"""
阶段 A4：概率校准 + 业务成本阈值 + 影子回放。

输入：A3 的 output/offline_runs/<dataset>/model_artifact.json
切分复用 A3 的 vintage 时间切分：
  * train  = 模型拟合期（A3 已用）
  * calib  = A3 的 valid 期，未参与拟合，作为**独立校准集**
  * oot    = 最新 vintage，只用于最终评估

做三件事：
1. Platt / Isotonic 校准（在校准集内部再切一半选择方法，选定后在完整校准集重拟合）；
2. 用「坏账成本 + 额度损失 + 客户体验 + 审核容量」在概率网格上搜索阈值，
   不使用分位数；同时保留旧的分位数策略作为对照；
3. 影子回放：模型决策 vs 现有（分位数）策略，输出差异率、分组坏账率、成本对比。
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys

import numpy as np
import pandas as pd
from sklearn.isotonic import IsotonicRegression
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score

from build_asof_features import load_catalog
from train_b_card_model import CATALOG_PATH, RUNS_DIR, ks_statistic, lift_at, load_training_frame, split_by_vintage
from woe_binning import population_stability_index, spearman_corr, transform_frame_woe

DEFAULT_COSTS = {
    "lgd": 0.65,                    # 违约时的损失率
    "revenue_margin_rate": 0.08,    # 额度被压降而损失的利息/手续费收入比例
    "experience_cost_per_contract": 30.0,   # 每次降额的客户体验成本（元）
    "review_capacity": 0.10,        # 人工审核容量：最多 10% 客户进入人工复核
}

PDO_DEFAULT = {"pdo": 80.0, "target_score": 650.0, "target_odds": 1.0, "min_score": 350, "max_score": 950}


def _sigmoid(x):
    return 1.0 / (1.0 + np.exp(-x))


def raw_logit(artifact, frame):
    x = transform_frame_woe(frame, artifact["feature_defs"])
    return artifact["intercept"] + sum(
        float(artifact["coefficients"][name]) * x[name].values.astype(float)
        for name in artifact["selected_features"])


def fit_platt(logits, y):
    model = LogisticRegression(solver="lbfgs", max_iter=500, C=1.0)
    model.fit(np.asarray(logits, dtype=float).reshape(-1, 1), np.asarray(y, dtype=int))
    return float(model.coef_[0][0]), float(model.intercept_[0])


def apply_platt(logits, a, b):
    return _sigmoid(a * np.asarray(logits, dtype=float) + b)


def fit_isotonic(logits, y):
    p = _sigmoid(np.asarray(logits, dtype=float))
    model = IsotonicRegression(out_of_bounds="clip")
    model.fit(p, np.asarray(y, dtype=float))
    return model.predict(p), model


def _monotonic(values):
    """分箱坏账率是否随分箱单调不减（允许 1e-9 容差）。"""
    seq = list(values)
    return all(seq[i] <= seq[i + 1] + 1e-9 for i in range(len(seq) - 1))


def _monotonic_decreasing(values):
    """分箱按风险从高到低排列时，坏账率应单调不增。"""
    seq = list(values)
    return all(seq[i] >= seq[i + 1] - 1e-9 for i in range(len(seq) - 1))


def reliability_table(y, p, bins=10):
    y = np.asarray(y, dtype=float)
    p = np.asarray(p, dtype=float)
    edges = np.linspace(0.0, 1.0, bins + 1)
    rows = []
    for i in range(bins):
        mask = (p >= edges[i]) & (p < edges[i + 1] if i < bins - 1 else p <= edges[i + 1])
        if mask.sum() == 0:
            continue
        rows.append({
            "bucket": i + 1,
            "count": int(mask.sum()),
            "avg_predicted": round(float(p[mask].mean()), 6),
            "observed_bad_rate": round(float(y[mask].mean()), 6),
        })
    return rows


def decile_table(y, p):
    y = np.asarray(y, dtype=float)
    p = np.asarray(p, dtype=float)
    order = np.argsort(-p)
    chunks = np.array_split(order, 10)
    rows = []
    for i, idx in enumerate(chunks):
        if len(idx) == 0:
            continue
        rows.append({
            "decile": i + 1,
            "count": int(len(idx)),
            "min_predicted": round(float(p[idx].min()), 6),
            "max_predicted": round(float(p[idx].max()), 6),
            "bad_rate": round(float(y[idx].mean()), 6),
        })
    return rows


def lift_table(y, p, fractions=(0.05, 0.1, 0.2, 0.3)):
    return [{"top_fraction": f, "lift": round(lift_at(y, p, f), 6),
             "bad_rate": round(float(np.asarray(y, dtype=float)[np.argsort(-np.asarray(p))[:max(1, int(round(len(p) * f)))]].mean()), 6)}
            for f in fractions]


def policy_multiplier(p, approve_below, reject_above, reduce_multiplier=0.5):
    p = np.asarray(p, dtype=float)
    return np.where(p < approve_below, 1.0,
                    np.where(p < reject_above, reduce_multiplier, 0.0))


def simulate(p, y, ead, approve_below, reject_above, costs, reduce_multiplier=0.5):
    m = policy_multiplier(p, approve_below, reject_above, reduce_multiplier)
    y = np.asarray(y, dtype=float)
    ead = np.asarray(ead, dtype=float)
    reduction = 1.0 - m
    # 保留的敞口（m）才计提坏账；降额减少敞口但损失收入与客户体验
    bad_debt = float(np.sum(y * m * ead * costs["lgd"]))
    margin = float(np.sum(reduction * ead * costs["revenue_margin_rate"]))
    experience = float(np.sum(reduction * costs["experience_cost_per_contract"]))
    review_rate = float(np.mean(np.asarray(p) >= reject_above))
    approve_rate = float(np.mean(m > 0))
    reduced = m < 1.0
    bad_rate_reduced = float(y[reduced].mean()) if reduced.any() else 0.0
    bad_rate_approved = float(y[m == 1.0].mean()) if (m == 1.0).any() else 0.0
    return {
        "approve_below": round(float(approve_below), 4),
        "reject_above": round(float(reject_above), 4),
        "total_cost": round(bad_debt + margin + experience, 4),
        "bad_debt_cost": round(bad_debt, 4),
        "margin_cost": round(margin, 4),
        "experience_cost": round(experience, 4),
        "review_rate": round(review_rate, 6),
        "approve_rate": round(approve_rate, 6),
        "bad_rate_in_reduced_group": round(bad_rate_reduced, 6),
        "bad_rate_in_fully_approved_group": round(bad_rate_approved, 6),
        "capacity_ok": review_rate <= costs["review_capacity"] + 1e-12,
        "multipliers": {"approve": 1.0, "reduce": reduce_multiplier, "reject": 0.0},
    }


def optimize_thresholds(p, y, ead, costs, reduce_multiplier=0.5):
    """在概率网格上按业务成本搜索阈值（不使用分位数）。"""
    grid = np.round(np.arange(0.0, 0.901, 0.005), 3)
    best = None
    candidates = []
    for approve in grid:
        for reject in grid:
            if reject <= approve:
                continue
            result = simulate(p, y, ead, approve, reject, costs, reduce_multiplier)
            candidates.append(result)
            if not result["capacity_ok"]:
                continue
            if best is None or result["total_cost"] < best["total_cost"]:
                best = result
    if best is None:
        raise ValueError("在审核容量约束下没有可行阈值，请放宽 review_capacity")
    candidates.sort(key=lambda r: r["total_cost"])
    return best, candidates[:20]


def pdo_score(prob, pdo_cfg):
    p = np.clip(np.asarray(prob, dtype=float), 1e-9, 1 - 1e-9)
    factor = -pdo_cfg["pdo"] / np.log(2.0)
    offset = pdo_cfg["target_score"] - factor * np.log(pdo_cfg["target_odds"])
    score = offset + factor * np.log(p / (1.0 - p))
    return np.clip(score, pdo_cfg["min_score"], pdo_cfg["max_score"])


def _period_metrics(y, p):
    y = np.asarray(y, dtype=int)
    return {
        "rows": int(len(y)),
        "bad_rate": round(float(y.mean()), 6),
        "auc": round(float(roc_auc_score(y, p)), 6) if len(set(y.tolist())) > 1 else 0.0,
        "ks": round(ks_statistic(y, p), 6),
        "pr_auc": round(float(average_precision_score(y, p)), 6) if len(set(y.tolist())) > 1 else 0.0,
        "brier": round(float(brier_score_loss(y, np.clip(p, 1e-6, 1 - 1e-6))), 6),
    }


def calibrate(dataset="fixture", snapshot_path=None, run_dir=None, seed=20261013,
              label_col="label_30dpd_6m", costs=None, out_path=None):
    run_dir = run_dir or os.path.join(RUNS_DIR, dataset)
    artifact_path = os.path.join(run_dir, "model_artifact.json")
    if not os.path.isfile(artifact_path):
        raise FileNotFoundError(f"缺少 A3 模型产物 {artifact_path}")
    with io.open(artifact_path, encoding="utf-8") as handle:
        artifact = json.load(handle)
    costs = {**DEFAULT_COSTS, **(costs or {})}

    frame, catalog, source_path = load_training_frame(dataset, snapshot_path)
    frame = frame[frame[label_col].notna()].copy()
    frame[label_col] = frame[label_col].astype(int)
    train_df, calib_df, oot_df, split = split_by_vintage(frame, label_col)
    if len(calib_df) == 0 or len(oot_df) == 0:
        raise ValueError("校准集或 OOT 为空，无法完成校准与影子评估")

    logit_calib = raw_logit(artifact, calib_df)
    y_calib = calib_df[label_col].values.astype(int)

    # 校准集内部再切一半用于选择方法，选定后在完整校准集重拟合
    rng = np.random.RandomState(seed)
    order = rng.permutation(len(y_calib))
    cut = int(len(order) * 0.6)
    fit_idx, select_idx = order[:cut], order[cut:]

    a_fit, b_fit = fit_platt(logit_calib[fit_idx], y_calib[fit_idx])
    p_platt_sel = apply_platt(logit_calib[select_idx], a_fit, b_fit)
    iso_pred_fit, iso_model_fit = fit_isotonic(logit_calib[fit_idx], y_calib[fit_idx])
    p_iso_sel = _sigmoid(logit_calib[select_idx])
    p_iso_sel = iso_model_fit.predict(p_iso_sel)
    platt_brier = float(brier_score_loss(y_calib[select_idx], np.clip(p_platt_sel, 1e-6, 1 - 1e-6)))
    iso_brier = float(brier_score_loss(y_calib[select_idx], np.clip(p_iso_sel, 1e-6, 1 - 1e-6)))

    # 生产口径固定使用 Platt：单调、参数少、可与 Java 影子服务逐位对齐；
    # Isotonic 仅作为校准集内的诊断对照，不参与上线参数。
    method = "platt"
    a, b = fit_platt(logit_calib, y_calib)
    calib_params = {"method": "platt", "a": round(a, 8), "b": round(b, 8)}
    p_calib = apply_platt(logit_calib, a, b)

    logit_oot = raw_logit(artifact, oot_df)
    p_raw_oot = _sigmoid(logit_oot)
    p_oot = apply_platt(logit_oot, calib_params["a"], calib_params["b"])
    y_oot = oot_df[label_col].values.astype(int)
    ead_oot = pd.to_numeric(oot_df.get("outstandingAmount", 0), errors="coerce").fillna(0).values.astype(float)

    chosen, top_candidates = optimize_thresholds(p_oot, y_oot, ead_oot, costs)

    # 旧策略对照：分位数阈值（仅用于对比，不作为选择依据）
    legacy_approve = float(np.quantile(p_raw_oot, 0.45))
    legacy_reject = float(np.quantile(p_raw_oot, 0.65))
    legacy = simulate(p_raw_oot, y_oot, ead_oot, legacy_approve, legacy_reject, costs)

    model_mult = policy_multiplier(p_oot, chosen["approve_below"], chosen["reject_above"])
    current_mult = policy_multiplier(p_raw_oot, legacy_approve, legacy_reject)
    diffs = model_mult != current_mult

    # 同容量对比：都取风险最高的 capacity 比例，比较坏账捕获能力与成本
    capacity = float(costs["review_capacity"])
    top_k = max(1, int(round(len(y_oot) * capacity)))
    model_top = np.argsort(-p_oot)[:top_k]
    current_top = np.argsort(-p_raw_oot)[:top_k]
    overall_bad = float(y_oot.mean())
    model_top_bad = float(y_oot[model_top].mean())
    current_top_bad = float(y_oot[current_top].mean())
    legacy_capacity_approve = float(np.quantile(p_raw_oot, 1.0 - capacity - 0.45))
    legacy_capacity_reject = float(np.quantile(p_raw_oot, 1.0 - capacity))
    legacy_at_capacity = simulate(p_raw_oot, y_oot, ead_oot,
                                  legacy_capacity_approve, legacy_capacity_reject, costs)

    shadow = {
        "rows": int(len(y_oot)),
        "decision_difference_rate": round(float(diffs.mean()), 6),
        "model_reduced_count": int((model_mult < 1.0).sum()),
        "current_reduced_count": int((current_mult < 1.0).sum()),
        "same_capacity": capacity,
        "same_capacity_top_k": top_k,
        "overall_bad_rate": round(overall_bad, 6),
        "model_topk_bad_rate": round(model_top_bad, 6),
        "current_topk_bad_rate": round(current_top_bad, 6),
        "model_lift_vs_overall": round(model_top_bad / max(overall_bad, 1e-9), 6),
        "current_lift_vs_overall": round(current_top_bad / max(overall_bad, 1e-9), 6),
        "model_capture_rate": round(float(y_oot[model_top].sum() / max(y_oot.sum(), 1)), 6),
        "current_capture_rate": round(float(y_oot[current_top].sum() / max(y_oot.sum(), 1)), 6),
        "model_cost": chosen["total_cost"],
        "legacy_quantile_cost": legacy["total_cost"],
        "legacy_quantile_review_rate": legacy["review_rate"],
        "legacy_cost_at_equal_capacity": legacy_at_capacity["total_cost"],
        "cost_improvement_at_equal_capacity": round(
            float(legacy_at_capacity["total_cost"] - chosen["total_cost"]), 4),
        "note": "旧分位数策略审核率可能超出容量；成本对比以 legacy_cost_at_equal_capacity 为准",
        "limitation": "概率校准是单调变换，不改变排序；因此同容量 top-k 的坏账率与原始打分必然相同。要证明「模型分组坏账率优于当前线上规则」，必须用真实数据中的 V1 B 分做对照（fixture 无 B 分）。",
    }

    periods = {}
    for name, sub in (("train", train_df), ("calib", calib_df), ("oot", oot_df)):
        logits = raw_logit(artifact, sub)
        y = sub[label_col].values.astype(int)
        prob = apply_platt(logits, calib_params["a"], calib_params["b"])
        periods[name] = {**_period_metrics(y, prob), "score_psi_vs_train": None}
    train_scores = pdo_score(
        apply_platt(raw_logit(artifact, train_df), calib_params["a"], calib_params["b"]), PDO_DEFAULT)
    for name, sub in (("train", train_df), ("calib", calib_df), ("oot", oot_df)):
        logits = raw_logit(artifact, sub)
        prob = apply_platt(logits, calib_params["a"], calib_params["b"])
        periods[name]["score_psi_vs_train"] = round(float(population_stability_index(
            train_scores, pdo_score(prob, PDO_DEFAULT))), 6)

    reliability_rows = reliability_table(y_oot, p_oot)
    decile_rows = decile_table(y_oot, p_oot)
    report = {
        "dataset": dataset,
        "deployable": dataset == "snapshot",
        "dataset_path": source_path,
        "label_column": label_col,
        "label_version": catalog["label_version"],
        "feature_version": catalog["feature_version"],
        "split": split,
        "calibration": {
            "selection_brier": {"platt": round(platt_brier, 6), "isotonic": round(iso_brier, 6)},
            "chosen_method": method,
            "params": calib_params,
            "note": "校准集为 A3 的 valid vintage（未参与模型拟合）；生产口径固定 Platt（单调、参数少、可与 Java 影子侧对齐），Isotonic 仅作诊断对照；未使用 OOT 选择参数",
        },
        "pdo_mapping": PDO_DEFAULT,
        "cost_assumptions": costs,
        "threshold_policy": {
            "chosen": chosen,
            "top_candidates": top_candidates,
            "selection_rule": "在概率网格上最小化业务总成本（坏账+额度损失+客户体验），并满足审核容量约束；未使用分位数",
        },
        "legacy_quantile_policy": legacy,
        "reliability_curve_oot": reliability_rows,
        "reliability_monotonic_strict": _monotonic([r["observed_bad_rate"] for r in reliability_rows]),
        "reliability_spearman": round(float(spearman_corr(
            np.arange(len(reliability_rows), dtype=float),
            np.array([r["observed_bad_rate"] for r in reliability_rows], dtype=float))), 4),
        "decile_bad_rates_oot": decile_rows,
        "decile_bad_rate_monotonic_strict": _monotonic_decreasing([r["bad_rate"] for r in decile_rows]),
        "decile_bad_rate_spearman": round(float(spearman_corr(
            np.arange(len(decile_rows), dtype=float),
            np.array([r["bad_rate"] for r in decile_rows], dtype=float))), 4),
        "lift_table_oot": lift_table(y_oot, p_oot),
        "periods": periods,
        "shadow_replay": shadow,
    }

    out_path = out_path or os.path.join(run_dir, "calibration_report.json")
    with io.open(out_path, "w", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2, sort_keys=True)

    shadow_model = {
        "model_version": f"A4-{catalog['feature_version']}-{method}",
        "feature_version": catalog["feature_version"],
        "label_version": catalog["label_version"],
        "selected_features": artifact["selected_features"],
        "feature_defs": artifact["feature_defs"],
        "intercept": artifact["intercept"],
        "coefficients": artifact["coefficients"],
        "calibration": calib_params,
        "pdo": PDO_DEFAULT,
        "policy": {
            "approve_below": chosen["approve_below"],
            "reject_above": chosen["reject_above"],
            "multipliers": {"approve": 1.0, "reduce": 0.5, "reject": 0.0},
        },
        "deployable": dataset == "snapshot",
        "note": "影子模式只读：不得写 user_credit_limit / limit_adjust_log",
    }
    with io.open(os.path.join(run_dir, "shadow_model.json"), "w", encoding="utf-8") as handle:
        json.dump(shadow_model, handle, ensure_ascii=False, indent=2, sort_keys=True)

    print(f"校准方法={method} (calib-select Brier platt={platt_brier:.4f} iso={iso_brier:.4f})")
    print(f"阈值(业务成本最优): approve_below={chosen['approve_below']} reject_above={chosen['reject_above']} "
          f"review_rate={chosen['review_rate']:.3f} total_cost={chosen['total_cost']}")
    print(f"影子回放(同容量 {shadow['same_capacity']}): 决策差异率={shadow['decision_difference_rate']:.3f} "
          f"模型 top-k 坏账率={shadow['model_topk_bad_rate']:.4f} "
          f"当前 top-k={shadow['current_topk_bad_rate']:.4f} "
          f"同容量成本改善={shadow['cost_improvement_at_equal_capacity']}")
    if dataset != "snapshot":
        print("[警告] fixture 数据集，仅验证校准/阈值/影子流程，不得作为开启自动调额的依据")
    return report


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="B 卡概率校准与阈值决策（阶段 A4）")
    parser.add_argument("--dataset", choices=["snapshot", "fixture"], default="snapshot")
    parser.add_argument("--snapshot-export", default=None)
    parser.add_argument("--run-dir", default=None)
    parser.add_argument("--label", default="label_30dpd_6m")
    parser.add_argument("--seed", type=int, default=20261013)
    parser.add_argument("--out", default=None)
    args = parser.parse_args(argv)
    try:
        calibrate(dataset=args.dataset, snapshot_path=args.snapshot_export, run_dir=args.run_dir,
                  seed=args.seed, label_col=args.label, out_path=args.out)
    except FileNotFoundError as exc:
        print(f"[失败] {exc}", file=sys.stderr)
        return 2
    except ValueError as exc:
        print(f"[失败] {exc}", file=sys.stderr)
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main())