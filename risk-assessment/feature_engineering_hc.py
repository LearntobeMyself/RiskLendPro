"""
Home Credit 宽表 → 统一训练/入库特征（对标修改意见 9 类 + HC 权威分）。
训练阶段从 parquet 构建；键名与 scoring_rules v7 WOE 及 Java 解析一致。
"""
from __future__ import annotations

import numpy as np
import pandas as pd

HC_CANDIDATE_FEATURES = [
    "gender_male",
    "married",
    "own_car",
    "own_realty",
    "edu_high",
    "edu_mid",
    "employment_stable",
    "age_years",
    "amt_income_total",
    "credit_inquiry_1m",
    "credit_inquiry_week",
    "ext_source_2",
    "ext_source_3",
    "active_loans_count",
    "credit_income_ratio",
    "cc_utilization",
    "loan_overdue_max_6m",
    "phone_change_days",
    "prev_refused_count",
]

HC_WOE_DEFAULT_FEATURES = [
    "ext_source_2",
    "ext_source_3",
    "gender_male",
    "married",
    "own_car",
    "credit_inquiry_1m",
    "active_loans_count",
    "amt_income_total",
    "credit_income_ratio",
    "employment_stable",
    "age_years",
    "cc_utilization",
]

FEATURE_SOURCE = {
    "gender_male": "application",
    "married": "application",
    "own_car": "application",
    "own_realty": "application",
    "edu_high": "application",
    "edu_mid": "application",
    "employment_stable": "application",
    "age_years": "application",
    "amt_income_total": "application",
    "credit_inquiry_1m": "external",
    "credit_inquiry_week": "external",
    "ext_source_2": "external",
    "ext_source_3": "external",
    "active_loans_count": "external",
    "credit_income_ratio": "derived",
    "cc_utilization": "external",
    "loan_overdue_max_6m": "external",
    "phone_change_days": "external",
    "prev_refused_count": "external",
}


def _first_col(df: pd.DataFrame, names: list[str]):
    for n in names:
        if n in df.columns:
            return n
    return None


def _edu_tiers(series: pd.Series) -> tuple[pd.Series, pd.Series]:
    high = {"Academic degree"}
    mid = {"Higher education", "Incomplete higher"}

    def split(v):
        if pd.isna(v):
            return 0, 0
        s = str(v).strip()
        if s in high:
            return 1, 0
        if s in mid:
            return 0, 1
        return 0, 0

    pairs = series.map(split)
    return (
        pairs.map(lambda x: x[0]).astype(float),
        pairs.map(lambda x: x[1]).astype(float),
    )


def _flag_y(series: pd.Series) -> pd.Series:
    return series.fillna("N").astype(str).str.upper().str.startswith("Y").astype(float)


def build_hc_training_features(raw: pd.DataFrame) -> pd.DataFrame:
    """从 HC 原始宽表构建候选特征 + defaulted 标签。"""
    out = pd.DataFrame(index=raw.index)

    if "TARGET" in raw.columns:
        out["defaulted"] = pd.to_numeric(raw["TARGET"], errors="coerce").fillna(0).astype(int)
    else:
        out["defaulted"] = 0

    col_gender = _first_col(raw, ["CODE_GENDER"])
    if col_gender:
        out["gender_male"] = raw[col_gender].astype(str).str.upper().eq("M").astype(float)
    else:
        out["gender_male"] = np.nan

    col_fam = _first_col(raw, ["NAME_FAMILY_STATUS"])
    if col_fam:
        out["married"] = (
            raw[col_fam].astype(str).str.contains("Married", case=False, na=False).astype(float)
        )
    else:
        out["married"] = np.nan

    col_car = _first_col(raw, ["FLAG_OWN_CAR"])
    out["own_car"] = _flag_y(raw[col_car]) if col_car else np.nan

    col_house = _first_col(raw, ["FLAG_OWN_REALTY"])
    out["own_realty"] = _flag_y(raw[col_house]) if col_house else np.nan

    col_edu = _first_col(raw, ["NAME_EDUCATION_TYPE"])
    if col_edu:
        out["edu_high"], out["edu_mid"] = _edu_tiers(raw[col_edu])
    else:
        out["edu_high"] = np.nan
        out["edu_mid"] = np.nan

    if "DAYS_EMPLOYED" in raw.columns:
        de = pd.to_numeric(raw["DAYS_EMPLOYED"], errors="coerce")
        de = de.where(de > -300000, np.nan)
        out["employment_stable"] = (de < -365).astype(float)
        out["employment_stable"] = out["employment_stable"].where(de.notna(), np.nan)
    else:
        out["employment_stable"] = np.nan

    if "DAYS_BIRTH" in raw.columns:
        out["age_years"] = (pd.to_numeric(raw["DAYS_BIRTH"], errors="coerce").abs() / 365.0).round(1)
    else:
        out["age_years"] = np.nan

    if "AMT_INCOME_TOTAL" in raw.columns:
        out["amt_income_total"] = pd.to_numeric(raw["AMT_INCOME_TOTAL"], errors="coerce")
    else:
        out["amt_income_total"] = np.nan

    if "AMT_REQ_CREDIT_BUREAU_MON" in raw.columns:
        out["credit_inquiry_1m"] = pd.to_numeric(raw["AMT_REQ_CREDIT_BUREAU_MON"], errors="coerce")
    else:
        out["credit_inquiry_1m"] = np.nan

    if "AMT_REQ_CREDIT_BUREAU_WEEK" in raw.columns:
        out["credit_inquiry_week"] = pd.to_numeric(raw["AMT_REQ_CREDIT_BUREAU_WEEK"], errors="coerce")
    else:
        out["credit_inquiry_week"] = np.nan

    for col, key in [("EXT_SOURCE_2", "ext_source_2"), ("EXT_SOURCE_3", "ext_source_3")]:
        if col in raw.columns:
            out[key] = pd.to_numeric(raw[col], errors="coerce")
        else:
            out[key] = np.nan

    if "active_loans_count" in raw.columns:
        out["active_loans_count"] = pd.to_numeric(raw["active_loans_count"], errors="coerce")
    else:
        out["active_loans_count"] = np.nan

    amt_credit_col = _first_col(raw, ["AMT_CREDIT"])
    if amt_credit_col and "amt_income_total" in out.columns:
        inc = out["amt_income_total"].replace(0, np.nan)
        out["credit_income_ratio"] = pd.to_numeric(raw[amt_credit_col], errors="coerce") / inc
    else:
        out["credit_income_ratio"] = np.nan

    cc_util_col = _first_col(
        raw,
        ["CC_UTILIZATION", "cc_utilization", "CC_AVG_BALANCE", "cc_avg_balance"],
    )
    if cc_util_col:
        out["cc_utilization"] = pd.to_numeric(raw[cc_util_col], errors="coerce")
    else:
        cc_cols = [c for c in raw.columns if str(c).lower().startswith("cc_")]
        if cc_cols:
            out["cc_utilization"] = pd.to_numeric(raw[cc_cols[0]], errors="coerce")
        else:
            out["cc_utilization"] = np.nan

    overdue_cols = [c for c in raw.columns if "OVERDUE" in c.upper() or "DPD" in c.upper()]
    if overdue_cols:
        od = raw[overdue_cols].apply(pd.to_numeric, errors="coerce")
        out["loan_overdue_max_6m"] = od.max(axis=1)
    else:
        out["loan_overdue_max_6m"] = 0.0

    if "DAYS_LAST_PHONE_CHANGE" in raw.columns:
        out["phone_change_days"] = pd.to_numeric(raw["DAYS_LAST_PHONE_CHANGE"], errors="coerce").abs()
    else:
        out["phone_change_days"] = np.nan

    prev_col = _first_col(raw, ["prev_refused", "prev_refused_count"])
    if prev_col:
        out["prev_refused_count"] = pd.to_numeric(raw[prev_col], errors="coerce").fillna(0)
    else:
        out["prev_refused_count"] = 0.0

    return out


def load_hc_raw_parquet(path: str = "data/raw/home_credit_train_min.parquet") -> pd.DataFrame:
    import os

    if not os.path.isfile(path):
        raise FileNotFoundError(path)
    return pd.read_parquet(path)
