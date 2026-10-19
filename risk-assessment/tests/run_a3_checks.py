"""
阶段 A3 验证运行器：一键跑完四项必需测试并输出报告。

1. 特征一致性：生成离线基准 output/b_card_offline_features_golden.csv
   （Java 侧 BCardOfflineFeatureConsistencyTest 会用同一 fixture 与之逐字段比对）；
2. OOT 训练与评估脚本可重复运行：跑两次训练，模型哈希与指标必须完全一致；
3. 模型指标、PSI、Lift、校准输出完整；
4. 退化特征与噪声特征不进入最终模型。

另含两条安全护栏断言：
* 入模特征必须全部是在线可用特征（避免线上线下不一致）；
* fixture 数据集不得写出可部署规则，也不得改动 output/b_scoring_rules.json。

用法：python tests/run_a3_checks.py
"""
from __future__ import annotations

import hashlib
import io
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)

FIXTURE_ASOF = os.path.join(ROOT, "data", "fixture", "asof_consistency_fixture.json")
TRAIN_FIXTURE = os.path.join(ROOT, "data", "fixture", "b_card_asof_training_fixture.csv")
GOLDEN_CSV = os.path.join(ROOT, "output", "b_card_offline_features_golden.csv")
RUN_DIR = os.path.join(ROOT, "output", "offline_runs", "fixture")
RERUN_DIR = os.path.join(ROOT, "output", "offline_runs", "fixture_rerun")
REPORT = os.path.join(ROOT, "output", "a3_check_report.json")
PRODUCTION_RULES = os.path.join(ROOT, "output", "b_scoring_rules.json")

checks = []


def record(name, passed, detail):
    checks.append({"check": name, "passed": bool(passed), "detail": detail})
    print(("[通过] " if passed else "[失败] ") + name + " -> " + str(detail))
    return passed


def file_hash(path):
    if not os.path.isfile(path):
        return None
    with io.open(path, "rb") as handle:
        return hashlib.sha256(handle.read()).hexdigest()


def run(script, *args):
    cmd = [sys.executable, os.path.join(ROOT, script)] + list(args)
    return subprocess.call(cmd, cwd=ROOT)


def load_json(path):
    with io.open(path, encoding="utf-8") as handle:
        return json.load(handle)


def check_feature_consistency_golden():
    rc = run("build_asof_features.py", "--fixture", FIXTURE_ASOF, "--out", GOLDEN_CSV)
    if rc != 0:
        return record("特征一致性基准生成", False, f"build_asof_features 退出码 {rc}")
    with io.open(GOLDEN_CSV, encoding="utf-8") as handle:
        lines = [line.strip() for line in handle if line.strip()]
    cases = len(lines) - 1
    ok = cases == 3 and "data_quality_status" in lines[0]
    return record("特征一致性基准生成", ok, f"{cases} 个用例, 列数={len(lines[0].split(','))}")


def check_reproducible_training():
    rc1 = run("train_b_card_model.py", "--dataset", "fixture", "--out-dir", RUN_DIR)
    if rc1 != 0:
        return record("OOT 训练可重复", False, f"第一次训练退出码 {rc1}")
    rc2 = run("train_b_card_model.py", "--dataset", "fixture", "--out-dir", RERUN_DIR)
    if rc2 != 0:
        return record("OOT 训练可重复", False, f"第二次训练退出码 {rc2}")

    first = load_json(os.path.join(RUN_DIR, "metrics.json"))
    second = load_json(os.path.join(RERUN_DIR, "metrics.json"))
    hash1 = load_json(os.path.join(RUN_DIR, "config.json"))["model_hash"]
    hash2 = load_json(os.path.join(RERUN_DIR, "config.json"))["model_hash"]
    same = (hash1 == hash2) and (json.dumps(first, sort_keys=True) == json.dumps(second, sort_keys=True))
    return record("OOT 训练可重复", same, f"model_hash={hash1[:16]} 两次一致={hash1 == hash2}")


def check_metrics_complete():
    rc = run("evaluate_b_card_model.py", "--dataset", "fixture")
    if rc != 0:
        return record("指标/PSI/校准输出完整", False, f"评估退出码 {rc}")
    report = load_json(os.path.join(RUN_DIR, "oot_report.json"))
    required = ("rows", "bad_rate", "auc", "ks", "pr_auc", "lift_top10", "brier")
    problems = []
    for period in ("train", "valid", "oot"):
        for key in required:
            if key not in report["periods"].get(period, {}):
                problems.append(f"{period}.{key}")
    if not report.get("calibration_oot"):
        problems.append("calibration_oot")
    if "score_train_vs_oot" not in report.get("psi", {}):
        problems.append("psi.score")
    if not report.get("psi", {}).get("features_train_vs_oot"):
        problems.append("psi.features")
    oot = report["periods"]["oot"]
    detail = (f"OOT AUC={oot['auc']:.4f} KS={oot['ks']:.4f} Lift@10%={oot['lift_top10']:.2f} "
              f"score_PSI={report['psi']['score_train_vs_oot']:.4f} "
              f"max_feature_PSI={report['max_feature_psi']:.4f}")
    return record("指标/PSI/校准输出完整", not problems, detail if not problems else problems)


def check_degenerate_excluded():
    selection = load_json(os.path.join(RUN_DIR, "feature_selection.json"))
    selected = [row["feature"] for row in selection if row.get("selected")]
    reasons = {row["feature"]: row.get("reason") for row in selection if not row.get("selected")}

    degenerate = [c for c in ("activeLoanCount", "repaidLoanCount", "recentNewLoanCount180d")
                  if c in selected]
    noisy = [c for c in ("recentNewLoanCount90d",) if c in selected]
    collinear = [c for c in ("cappedUtilizationRate", "avgLoanAmount", "usedLimit") if c in selected]
    problems = []
    if degenerate:
        problems.append(f"退化/恒定特征入模: {degenerate}")
    if noisy:
        problems.append(f"噪声特征入模: {noisy}")
    if collinear:
        problems.append(f"共线特征入模: {collinear}")
    detail = (f"入模 {len(selected)} 个特征; 剔除示例: "
              f"activeLoanCount={reasons.get('activeLoanCount')}, "
              f"recentNewLoanCount90d={reasons.get('recentNewLoanCount90d')}, "
              f"cappedUtilizationRate={reasons.get('cappedUtilizationRate')}")
    return record("退化/噪声/共线特征不入模", not problems, detail if not problems else problems)


def check_online_parity_guard():
    catalog = load_json(os.path.join(ROOT, "output", "b_card_feature_catalog.json"))
    online = {f["name"] for f in catalog["features"] if f.get("available_online")}
    selection = load_json(os.path.join(RUN_DIR, "feature_selection.json"))
    selected = [row["feature"] for row in selection if row.get("selected")]
    illegal = [c for c in selected if c not in online]
    return record("入模特征全部在线可用", not illegal,
                  f"越界特征={illegal}" if illegal else f"{len(selected)} 个入模特征均在目录中标记 available_online=true")


def check_no_accidental_deploy():
    before = file_hash(PRODUCTION_RULES)
    rc = run("train_b_card_model.py", "--dataset", "snapshot")
    after = file_hash(PRODUCTION_RULES)
    config = load_json(os.path.join(RUN_DIR, "config.json"))
    ok = (rc != 0) and (before == after) and (config["deployable"] is False)
    detail = (f"真实数据缺失时退出码={rc}(应为非0), 生产规则文件未变={before == after}, "
              f"fixture 产物 deployable={config['deployable']}")
    return record("不产生可部署规则/不覆盖生产规则", ok, detail)


def main():
    print("=" * 78)
    print("阶段 A3 验证")
    print("=" * 78)
    check_feature_consistency_golden()
    check_reproducible_training()
    check_metrics_complete()
    check_degenerate_excluded()
    check_online_parity_guard()
    check_no_accidental_deploy()

    failed = [c for c in checks if not c["passed"]]
    summary = {
        "phase": "A3",
        "checks_total": len(checks),
        "checks_failed": len(failed),
        "passed": not failed,
        "checks": checks,
    }
    with io.open(REPORT, "w", encoding="utf-8") as handle:
        json.dump(summary, handle, ensure_ascii=False, indent=2, sort_keys=True)
    print("-" * 78)
    print(f"结果: {len(checks) - len(failed)}/{len(checks)} 通过 -> {REPORT}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())