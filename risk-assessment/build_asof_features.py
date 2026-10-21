"""
阶段 A3：离线 as-of 特征构建。

两种输入模式：

1. ``--fixture <json>``：原始贷款/还款计划/还款记录/额度历史，用于验证
   「离线特征 == 在线特征」。在线实现见
   ``services/risk-service/.../risk/bcard/BCardFeatureServiceImpl``，
   两边必须使用同一套窗口与缺失语义（见 output/b_card_feature_catalog.json）。
2. ``--snapshot-export <csv>``：A2 产出的 ``b_card_feature_snapshot`` 导出，
   训练集只能由 ``sample_status = LABELED`` 且 ``label_version`` 匹配的样本组成。

硬性规则：
* 任何特征都只允许使用 asOfDate 当日及以前的数据；
* 缺失值语义由特征目录统一定义（数值缺失=0.0），禁止离线用中位数、线上用 0；
* 离线独有的窗口特征（3/6 个月）标记为 available_online=false，
  不得进入最终模型，只用于 IV/稳定性分析。
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import os
import sys
from datetime import date, datetime

CATALOG_PATH = os.path.join("output", "b_card_feature_catalog.json")

PAID_STATUSES = ("COMPLETED", "PAID", "SETTLED")
EFFECTIVE_STATUSES = ("DISBURSED", "OVERDUE", "REPAID")


# ----------------------------------------------------------------------------
# 基础工具
# ----------------------------------------------------------------------------

def parse_date(value):
    if value in (None, ""):
        return None
    if isinstance(value, date) and not isinstance(value, datetime):
        return value
    if isinstance(value, datetime):
        return value.date()
    text = str(value).strip()
    for fmt in ("%Y-%m-%d", "%Y-%m-%d %H:%M:%S", "%Y/%m/%d"):
        try:
            return datetime.strptime(text, fmt).date()
        except ValueError:
            continue
    raise ValueError(f"无法解析日期: {value!r}")


def to_float(value):
    if value in (None, ""):
        return 0.0
    return float(value)


def days_between(start, end):
    return (end - start).days


def load_catalog(path=CATALOG_PATH):
    with io.open(path, encoding="utf-8") as handle:
        return json.load(handle)


def online_feature_names(catalog):
    return [f["name"] for f in catalog["features"] if f.get("available_online")]


def offline_only_feature_names(catalog):
    return [f["name"] for f in catalog["features"] if not f.get("available_online")]


# ----------------------------------------------------------------------------
# 与在线实现逐条对应的 as-of 特征计算
# ----------------------------------------------------------------------------

def build_case_features(case):
    """按 asOfDate 还原一组用户的贷后特征（与 Java 在线实现同口径）。"""
    as_of = parse_date(case["as_of_date"])
    flags = []

    loans = []
    for raw in case.get("loans") or []:
        disbursed = parse_date(raw.get("disbursement_time"))
        if raw.get("status") not in EFFECTIVE_STATUSES:
            continue
        # 关键：asOfDate 之后才放款的贷款当时并不存在
        if disbursed is None or disbursed > as_of:
            continue
        loans.append({
            "loan_id": raw.get("loan_id"),
            "amount": to_float(raw.get("amount")),
            "apply_time": parse_date(raw.get("apply_time")),
            "approve_time": parse_date(raw.get("approve_time")),
            "disbursement_time": disbursed,
        })

    plans = []
    for raw in case.get("plans") or []:
        create_time = parse_date(raw.get("create_time"))
        if create_time is not None and create_time > as_of:
            continue
        plans.append({
            "plan_id": raw.get("plan_id"),
            "loan_id": raw.get("loan_id"),
            "create_time": create_time,
            "update_time": parse_date(raw.get("update_time")),
        })

    loan_ids = {loan["loan_id"] for loan in loans}
    records = []
    for raw in case.get("records") or []:
        if raw.get("loan_id") not in loan_ids:
            continue
        create_time = parse_date(raw.get("create_time"))
        if create_time is not None and create_time > as_of:
            continue
        records.append({
            "record_id": raw.get("record_id"),
            "loan_id": raw.get("loan_id"),
            "due_date": parse_date(raw.get("due_date")),
            "repayment_date": parse_date(raw.get("repayment_date")),
            "status": raw.get("status"),
            "amount": to_float(raw.get("amount")),
            "actual_amount": to_float(raw.get("actual_amount")),
            "create_time": create_time,
        })

    records_by_loan = {}
    for record in records:
        records_by_loan.setdefault(record["loan_id"], []).append(record)

    def is_paid_at(record):
        if record["status"] not in PAID_STATUSES:
            return False
        repayment = record["repayment_date"]
        return repayment is None or repayment <= as_of

    def loan_overdue(loan_id):
        loan_records = records_by_loan.get(loan_id) or []
        return any(r["due_date"] is not None and r["due_date"] < as_of and not is_paid_at(r)
                   for r in loan_records)

    def loan_repaid(loan_id):
        loan_records = records_by_loan.get(loan_id) or []
        return bool(loan_records) and all(is_paid_at(r) for r in loan_records)

    def dpd_at(record):
        due = record["due_date"]
        if due is None:
            return 0
        repayment = record["repayment_date"]
        end = repayment if (repayment is not None and repayment <= as_of) else as_of
        days = days_between(due, end)
        return days if days > 0 else 0

    def max_dpd_in_window(start, end):
        best = 0
        for record in records:
            due = record["due_date"]
            if due is None or due > end:
                continue
            repayment = record["repayment_date"]
            effective_end = repayment if (repayment is not None and repayment <= end) else end
            if effective_end < start:
                continue
            days = days_between(due, effective_end)
            if days > 0:
                best = max(best, days)
        return best

    features = {}
    features["loanCountTotal"] = len(loans)
    repaid_count = sum(1 for loan in loans if loan_repaid(loan["loan_id"]))
    overdue_count = sum(1 for loan in loans if loan_overdue(loan["loan_id"]))
    features["repaidLoanCount"] = repaid_count
    features["overdueLoanCount"] = overdue_count
    features["activeLoanCount"] = len(loans) - repaid_count

    if any(not (records_by_loan.get(plan["loan_id"]) or []) for plan in plans):
        flags.append("PLAN_WITHOUT_RECORDS")
    features["overduePlanCount"] = sum(1 for plan in plans if loan_overdue(plan["loan_id"]))
    features["paidLoanCount"] = repaid_count

    window_start = _minus_months(as_of, 12)

    current_max_dpd = 0
    max_dpd_12m = 0
    dpd_sum = 0
    dpd_count = 0
    late_count = 0
    last_overdue = None
    paid_records = 0
    paid_records_12m = 0
    on_time_12m = 0
    partial_12m = 0
    outstanding = 0.0
    derived_overdue = 0.0
    overdue_amount_3m = 0.0
    overdue_amount_6m = 0.0
    window_3m = _minus_months(as_of, 3)
    window_6m = _minus_months(as_of, 6)

    for record in records:
        if record["create_time"] is None:
            flags.append("RECORD_CREATE_TIME_MISSING")
        paid = is_paid_at(record)
        if paid:
            paid_records += 1
            if record["repayment_date"] is None:
                flags.append("PAID_WITHOUT_REPAYMENT_DATE")

        due = record["due_date"]
        value = max(0.0, record["amount"] - record["actual_amount"]) if paid else record["amount"]
        outstanding += value

        past_due = due is not None and due < as_of
        if past_due and not paid:
            current_max_dpd = max(current_max_dpd, days_between(due, as_of))
            derived_overdue += value

        if due is None or due < window_start or due > as_of:
            continue
        dpd = dpd_at(record)
        if dpd > 0:
            max_dpd_12m = max(max_dpd_12m, dpd)
            dpd_sum += dpd
            dpd_count += 1
            late_count += 1
            if last_overdue is None or due > last_overdue:
                last_overdue = due
        if paid or due < as_of:
            paid_records_12m += 1
            if paid and _is_on_time(record):
                on_time_12m += 1
        if paid and record["actual_amount"] < record["amount"]:
            partial_12m += 1

    features["currentMaxDpd"] = current_max_dpd
    features["maxDpd12m"] = max_dpd_12m
    features["avgDpd12m"] = 0.0 if dpd_count == 0 else float(dpd_sum) / dpd_count
    features["latePaymentCount12m"] = late_count
    features["onTimeRate12m"] = 0.0 if paid_records_12m == 0 else float(on_time_12m) / paid_records_12m
    features["partialPaymentRatio12m"] = 0.0 if paid_records_12m == 0 else float(partial_12m) / paid_records_12m
    features["daysSinceLastOverdue"] = None if last_overdue is None else days_between(last_overdue, as_of)
    features["recordCount"] = len(records)
    features["paidRecordCount"] = paid_records
    features["outstandingAmount"] = outstanding

    max_dpd_3m = max_dpd_in_window(window_3m, as_of)
    max_dpd_6m = max_dpd_in_window(window_6m, as_of)
    features["maxDpd3m"] = max_dpd_3m
    features["maxDpd6m"] = max_dpd_6m
    features["latePaymentCount3m"] = _count_late(records, window_3m, as_of, dpd_at)
    features["latePaymentCount6m"] = _count_late(records, window_6m, as_of, dpd_at)

    # 离线独有窗口特征（仅用于 IV/稳定性分析，available_online=false，不进入最终模型）
    for record in records:
        due = record["due_date"]
        if due is None or due > as_of or is_paid_at(record):
            continue
        value = record["amount"]
        if due >= window_6m:
            overdue_amount_6m += value
        if due >= window_3m:
            overdue_amount_3m += value
    features["overdueAmount3m"] = overdue_amount_3m
    features["overdueAmount6m"] = overdue_amount_6m

    history = None
    for row in case.get("credit_limit_history") or []:
        snapshot_date = parse_date(row.get("snapshot_date"))
        if snapshot_date is None or snapshot_date > as_of:
            continue
        if history is None or snapshot_date > history["snapshot_date"]:
            history = {
                "snapshot_date": snapshot_date,
                "total_limit": to_float(row.get("total_limit")),
                "used_limit": to_float(row.get("used_limit")),
                "remaining_limit": to_float(row.get("remaining_limit")),
                "overdue_amount": to_float(row.get("overdue_amount")),
                "has_overdue": bool(row.get("has_overdue")),
            }

    if history is not None:
        total_limit = history["total_limit"]
        used_limit = history["used_limit"]
        remaining_limit = history["remaining_limit"]
        features["overdueAmount"] = max(derived_overdue, history["overdue_amount"])
        has_overdue = current_max_dpd > 0 or history["has_overdue"]
    else:
        # 历史日期且没有额度快照：宁可用 0 也不读当前额度（避免时间穿越）
        total_limit = 0.0
        used_limit = 0.0
        remaining_limit = 0.0
        features["overdueAmount"] = derived_overdue
        has_overdue = current_max_dpd > 0
        flags.append("LIMIT_HISTORY_MISSING")

    utilization = 0.0 if total_limit <= 0 else used_limit / total_limit
    features["totalLimit"] = total_limit
    features["usedLimit"] = used_limit
    features["remainingLimit"] = remaining_limit
    features["utilizationRate"] = utilization
    features["cappedUtilizationRate"] = min(utilization, 2.0)
    features["hasOverdue"] = has_overdue
    if utilization > 2.0:
        flags.append("UTILIZATION_GT_2")

    total_amount = sum(loan["amount"] for loan in loans)
    features["totalLoanAmount"] = total_amount
    features["avgLoanAmount"] = 0.0 if not loans else total_amount / len(loans)
    last_loan = None
    for loan in loans:
        if last_loan is None or loan["disbursement_time"] > last_loan["disbursement_time"]:
            last_loan = loan
    features["lastLoanAmount"] = 0.0 if last_loan is None else last_loan["amount"]

    ninety = _minus_days(as_of, 90)
    one_eighty = _minus_days(as_of, 180)
    features["recentNewLoanCount90d"] = sum(
        1 for loan in loans if ninety <= loan["disbursement_time"] <= as_of)
    features["recentNewLoanCount180d"] = sum(
        1 for loan in loans if one_eighty <= loan["disbursement_time"] <= as_of)

    known_dates = []
    for loan in loans:
        for key in ("disbursement_time", "apply_time", "approve_time"):
            value = loan.get(key)
            if value is not None and value <= as_of:
                known_dates.append(value)
    for plan in plans:
        for key in ("create_time", "update_time"):
            value = plan.get(key)
            if value is not None and value <= as_of:
                known_dates.append(value)
    for record in records:
        for key in ("create_time", "due_date"):
            value = record.get(key)
            if value is not None and value <= as_of:
                known_dates.append(value)
    features["tenureDays"] = 0 if not known_dates else max(0, days_between(min(known_dates), as_of))

    if not loans and not records:
        flags.append("NO_OBSERVATION_DATA")
    if features["tenureDays"] < current_max_dpd:
        flags.append("TENURE_LT_MAX_DPD")

    return features, sorted(set(flags))


def _minus_months(value, months):
    year = value.year
    month = value.month - months
    while month <= 0:
        month += 12
        year -= 1
    day = min(value.day, _days_in_month(year, month))
    return date(year, month, day)


def _days_in_month(year, month):
    if month == 12:
        return 31
    return (date(year, month + 1, 1) - date(year, month, 1)).days


def _minus_days(value, days):
    from datetime import timedelta
    return value - timedelta(days=days)


def _is_on_time(record):
    return (record["repayment_date"] is not None
            and record["due_date"] is not None
            and record["repayment_date"] <= record["due_date"])


def _count_late(records, start, end, dpd_at):
    count = 0
    for record in records:
        due = record["due_date"]
        if due is None or due < start or due > end:
            continue
        if dpd_at(record) > 0:
            count += 1
    return count


def resolve_data_quality_status(flags):
    if not flags:
        return "OK"
    if "NO_OBSERVATION_DATA" in flags:
        return "INSUFFICIENT"
    return "DEGRADED"


# ----------------------------------------------------------------------------
# 输入/输出
# ----------------------------------------------------------------------------

def build_from_fixture(fixture_path, out_path, catalog):
    with io.open(fixture_path, encoding="utf-8") as handle:
        fixture = json.load(handle)
    if fixture.get("deployable"):
        raise ValueError("一致性 fixture 不允许标记为可上线数据")

    online = online_feature_names(catalog)
    offline_only = offline_only_feature_names(catalog)
    rows = []
    for case in fixture["cases"]:
        features, flags = build_case_features(case)
        row = {"case_name": case["name"], "user_id": case["user_id"], "as_of_date": case["as_of_date"]}
        for name in online + offline_only:
            row[name] = features.get(name)
        row["data_quality_flags"] = "|".join(flags)
        row["data_quality_status"] = resolve_data_quality_status(flags)
        rows.append(row)

    columns = ["case_name", "user_id", "as_of_date"] + online + offline_only + \
              ["data_quality_flags", "data_quality_status"]
    _write_rows(rows, columns, out_path)
    return rows


def load_snapshot_export(path, catalog):
    """读取 A2 快照导出；只保留已成熟且标签版本一致的样本。"""
    if not os.path.isfile(path):
        raise FileNotFoundError(
            f"缺少 as-of 快照导出 {path}；请先从 b_card_feature_snapshot 导出 "
            f"sample_status=LABELED 的样本（见 docs/B卡A3特征工程与训练报告.md）")

    online = online_feature_names(catalog)
    label_version = catalog["label_version"]
    feature_version = catalog["feature_version"]
    rows = []
    skipped = {"status": 0, "label_version": 0, "feature_version": 0, "label_missing": 0, "json": 0}
    with io.open(path, encoding="utf-8-sig") as handle:
        for record in csv.DictReader(handle):
            if record.get("sample_status") != "LABELED":
                skipped["status"] += 1
                continue
            if record.get("label_version") != label_version:
                skipped["label_version"] += 1
                continue
            if record.get("feature_version") != feature_version:
                skipped["feature_version"] += 1
                continue
            if record.get("label_30dpd_6m") in (None, "") or record.get("label_90dpd_12m") in (None, ""):
                skipped["label_missing"] += 1
                continue
            try:
                payload = json.loads(record["feature_json"])
            except Exception:
                skipped["json"] += 1
                continue
            row = {
                "user_id": record.get("user_id"),
                "as_of_date": record.get("as_of_date"),
                "label_30dpd_6m": int(float(record["label_30dpd_6m"])),
                "label_90dpd_12m": int(float(record["label_90dpd_12m"])),
            }
            for name in online:
                row[name] = to_float(payload.get(name))
            rows.append(row)

    if not rows:
        raise ValueError(
            f"快照导出中没有可训练样本（要求 sample_status=LABELED 且 label_version={label_version}），"
            f"跳过统计={skipped}")
    print(f"快照导出载入完成: {len(rows)} 条可用样本, 过滤={skipped}")
    return rows, ["user_id", "as_of_date"] + online + ["label_30dpd_6m", "label_90dpd_12m"]


def _write_rows(rows, columns, out_path):
    directory = os.path.dirname(out_path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    with io.open(out_path, "w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns)
        writer.writeheader()
        for row in rows:
            writer.writerow({key: _format(row.get(key)) for key in columns})
    print(f"写出 {len(rows)} 行 -> {out_path}")


def _format(value):
    if value is None:
        return ""
    if isinstance(value, float):
        return repr(value)
    return value


def main(argv=None):
    parser = argparse.ArgumentParser(description="B 卡 as-of 特征构建（阶段 A3）")
    parser.add_argument("--catalog", default=CATALOG_PATH)
    parser.add_argument("--fixture")
    parser.add_argument("--snapshot-export")
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)

    catalog = load_catalog(args.catalog)
    if args.fixture:
        build_from_fixture(args.fixture, args.out, catalog)
        return 0
    if args.snapshot_export:
        rows, columns = load_snapshot_export(args.snapshot_export, catalog)
        _write_rows(rows, columns, args.out)
        return 0
    parser.error("必须指定 --fixture 或 --snapshot-export")
    return 2


if __name__ == "__main__":
    sys.exit(main())