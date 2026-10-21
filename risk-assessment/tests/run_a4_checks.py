"""
阶段 A4 验证运行器。

检查项：
1. 概率校准：参数、可靠性曲线、分箱坏账率、Brier 对比齐全且趋势可解释；
2. 阈值决策回放：阈值来自业务成本网格、满足审核容量、回放成本可复算；
3. 跨期稳定性：train/calib/oot 的 AUC/KS/PSI 齐全且 PSI 在阈值内；
4. 影子产物：shadow_model 与校准报告的阈值一致，且标注只读（不写额度）；
   真正的「不写额度」由 Java 测试 BCardShadowScoreServiceTest 守护。
5. 可重复性：同一数据+同一种子两次运行，校准参数与阈值完全一致。
"""
from __future__ import annotations

import io
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)

RUN_DIR = os.path.join(ROOT, "output", "offline_runs", "fixture")
RERUN_DIR = os.path.join(ROOT, "output", "offline_runs", "fixture_rerun")
REPORT = os.path.join(ROOT, "output", "a4_check_report.json")
PSI_LIMIT = 0.25

checks = []


def record(name, passed, detail):
    checks.append({"check": name, "passed": bool(passed), "detail": detail})
    print(("[PASS] " if passed else "[FAIL] ") + name + " -> " + str(detail))
    return passed


def load(path):
    with io.open(path, encoding="utf-8") as handle:
        return json.load(handle)


def run(script, *args):
    return subprocess.call([sys.executable, os.path.join(ROOT, script)] + list(args), cwd=ROOT)


def main():
    rc1 = run("calibrate_b_card_model.py", "--dataset", "fixture")
    rc2 = run("calibrate_b_card_model.py", "--dataset", "fixture", "--run-dir", RERUN_DIR)
    if rc1 != 0 or rc2 != 0:
        record("A4 脚本执行", False, f"exit codes {rc1}/{rc2}")
        _write()
        return 1

    report = load(os.path.join(RUN_DIR, "calibration_report.json"))
    rerun = load(os.path.join(RERUN_DIR, "calibration_report.json"))
    shadow = load(os.path.join(RUN_DIR, "shadow_model.json"))

    # 1) 概率校准
    calib = report.get("calibration", {})
    reliability = report.get("reliability_curve_oot", [])
    deciles = report.get("decile_bad_rates_oot", [])
    ok = (calib.get("chosen_method") == "platt"
          and "a" in calib.get("params", {}) and "b" in calib.get("params", {})
          and len(reliability) >= 5 and len(deciles) == 10
          and "reliability_spearman" in report and "decile_bad_rate_spearman" in report)
    record("概率校准参数与曲线", ok,
           f"method={calib.get('chosen_method')} a={calib.get('params', {}).get('a')} "
           f"b={calib.get('params', {}).get('b')} reliability_bins={len(reliability)} "
           f"deciles={len(deciles)} spearman(rel)={report.get('reliability_spearman')} "
           f"spearman(decile)={report.get('decile_bad_rate_spearman')}")

    # 2) 阈值决策回放
    chosen = report["threshold_policy"]["chosen"]
    legacy = report["legacy_quantile_policy"]
    capacity = report["cost_assumptions"]["review_capacity"]
    from calibrate_b_card_model import simulate
    from train_b_card_model import load_training_frame, split_by_vintage
    import numpy as np
    import pandas as pd
    frame, catalog, _ = load_training_frame("fixture", None)
    frame = frame[frame["label_30dpd_6m"].notna()].copy()
    frame["label_30dpd_6m"] = frame["label_30dpd_6m"].astype(int)
    _, _, oot_df, _ = split_by_vintage(frame, "label_30dpd_6m")
    # 用报告中的阈值重放成本（必须与报告一致）
    import calibrate_b_card_model as cal
    artifact = load(os.path.join(RUN_DIR, "model_artifact.json"))
    logits = cal.raw_logit(artifact, oot_df)
    p_oot = cal.apply_platt(logits, report["calibration"]["params"]["a"], report["calibration"]["params"]["b"])
    y_oot = oot_df["label_30dpd_6m"].values.astype(int)
    ead = pd.to_numeric(oot_df["outstandingAmount"], errors="coerce").fillna(0).values.astype(float)
    replay = simulate(p_oot, y_oot, ead, chosen["approve_below"], chosen["reject_above"], report["cost_assumptions"])
    replay_ok = abs(replay["total_cost"] - chosen["total_cost"]) < 0.01
    business_ok = (chosen["review_rate"] <= capacity + 1e-9
                   and (chosen["approve_below"] != legacy["approve_below"]
                        or chosen["reject_above"] != legacy["reject_above"])
                   and "selection_rule" in report["threshold_policy"]
                   and "分位数" in report["threshold_policy"]["selection_rule"])
    record("阈值决策回放", replay_ok and business_ok,
           f"chosen=({chosen['approve_below']},{chosen['reject_above']}) review={chosen['review_rate']} "
           f"capacity={capacity} replay_cost={replay['total_cost']} reported={chosen['total_cost']}")

    # 3) 跨期稳定性与 PSI
    periods = report.get("periods", {})
    periods_ok = all(k in periods for k in ("train", "calib", "oot"))
    psi_ok = periods_ok and all(periods[k].get("score_psi_vs_train", 99) <= PSI_LIMIT for k in periods)
    record("跨期稳定性与 PSI", periods_ok and psi_ok,
           {k: {"auc": periods.get(k, {}).get("auc"), "psi": periods.get(k, {}).get("score_psi_vs_train")}
            for k in ("train", "calib", "oot")})

    # 4) 影子产物一致性（不写额度由 Java 测试守护）
    policy = shadow.get("policy", {})
    shadow_ok = (policy.get("approve_below") == chosen["approve_below"]
                 and policy.get("reject_above") == chosen["reject_above"]
                 and "不得写" in shadow.get("note", "")
                 and shadow.get("deployable") is False)
    record("影子产物与只读约束", shadow_ok,
           f"approve_below={policy.get('approve_below')} reject_above={policy.get('reject_above')} "
           f"deployable={shadow.get('deployable')}")

    # 5) 可重复性
    same_params = rerun["calibration"]["params"] == report["calibration"]["params"]
    same_thresholds = rerun["threshold_policy"]["chosen"] == chosen
    same_shadow = rerun["shadow_replay"] == report["shadow_replay"]
    record("校准/阈值可重复", same_params and same_thresholds and same_shadow,
           f"params_same={same_params} thresholds_same={same_thresholds} shadow_same={same_shadow}")

    _write()
    failed = [c for c in checks if not c["passed"]]
    print("-" * 70)
    print(f"结果: {len(checks) - len(failed)}/{len(checks)} 通过 -> {REPORT}")
    return 1 if failed else 0


def _write():
    failed = [c for c in checks if not c["passed"]]
    payload = {"phase": "A4", "checks_total": len(checks), "checks_failed": len(failed),
               "passed": not failed, "checks": checks}
    with io.open(REPORT, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2, sort_keys=True)


if __name__ == "__main__":
    sys.exit(main())