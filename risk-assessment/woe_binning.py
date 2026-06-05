"""
分箱 + WOE + IV；输出供 train_scoring_model 与 Java 在线查表。
"""
from __future__ import annotations

import math
from typing import Any

import numpy as np
import pandas as pd

from feature_engineering_hc import HC_WOE_DEFAULT_FEATURES, FEATURE_SOURCE


def _woe_iv(good: float, bad: float, g_total: float, b_total: float) -> tuple[float, float]:
    g_dist = (good + 0.5) / (g_total + 1.0)
    b_dist = (bad + 0.5) / (b_total + 1.0)
    woe = math.log(g_dist / b_dist)
    iv = (g_dist - b_dist) * woe
    return woe, iv


def iv_numeric(series: pd.Series, y: pd.Series, n_bins: int = 5) -> float:
    s = series.copy()
    mask = s.notna()
    if mask.sum() < 30:
        return 0.0
    try:
        bins = pd.qcut(s[mask], q=min(n_bins, s[mask].nunique()), duplicates="drop")
    except Exception:
        return 0.0
    return _iv_from_groups(bins, y[mask])


def iv_categorical(series: pd.Series, y: pd.Series) -> float:
    s = series.copy().astype(str)
    s = s.where(series.notna(), "__MISSING__")
    return _iv_from_groups(s, y)


def _iv_from_groups(groups: pd.Series, y: pd.Series) -> float:
    g_total = float((y == 0).sum())
    b_total = float((y == 1).sum())
    if g_total < 1 or b_total < 1:
        return 0.0
    iv = 0.0
    for _, idx in groups.groupby(groups).groups.items():
        yy = y.loc[idx]
        good = float((yy == 0).sum())
        bad = float((yy == 1).sum())
        _, part = _woe_iv(good, bad, g_total, b_total)
        iv += part
    return float(iv)


def fit_feature_woe(
    series: pd.Series,
    y: pd.Series,
    feature_key: str,
    n_bins: int = 5,
) -> dict[str, Any]:
    source = FEATURE_SOURCE.get(feature_key, "external")
    s = pd.to_numeric(series, errors="coerce")
    uniq = s.dropna().nunique()
    if uniq <= 2:
        return fit_binary_woe(s, y, source=source)
    return fit_numeric_woe(s, y, n_bins=n_bins, source=source)


def fit_numeric_woe(
    series: pd.Series,
    y: pd.Series,
    n_bins: int = 5,
    source: str = "external",
) -> dict[str, Any]:
    s = pd.to_numeric(series, errors="coerce")
    valid = s.notna()
    g_total = float((y == 0).sum())
    b_total = float((y == 1).sum())

    cuts: list[float] = []
    woe_list: list[float] = []

    if valid.sum() >= 30 and s[valid].nunique() > 1:
        try:
            _, edges = pd.qcut(
                s[valid], q=min(n_bins, s[valid].nunique()), duplicates="drop", retbins=True
            )
            edges = sorted(set(float(x) for x in edges))
            if len(edges) < 2:
                edges = [float(s[valid].min()), float(s[valid].max()) + 1e-6]
            cats = pd.cut(s[valid], bins=edges, include_lowest=True)
            for cat in cats.unique():
                idx = cats == cat
                yy = y[valid][idx]
                good = float((yy == 0).sum())
                bad = float((yy == 1).sum())
                woe, _ = _woe_iv(good, bad, g_total, b_total)
                right = float(cat.right) if hasattr(cat, "right") else float(cat)
                cuts.append(right)
                woe_list.append(round(woe, 6))
            pairs = sorted(zip(cuts, woe_list), key=lambda x: x[0])
            cuts = [p[0] for p in pairs]
            woe_list = [p[1] for p in pairs]
        except Exception:
            cuts = [float(s[valid].min()), float(s[valid].max()) + 1e-6]
            woe_list = [0.0, 0.0]
    else:
        cuts = [0.0, 1.0]
        woe_list = [0.0, 0.0]

    miss_mask = ~valid
    if miss_mask.any():
        yy = y[miss_mask]
        good = float((yy == 0).sum())
        bad = float((yy == 1).sum())
        mwoe, _ = _woe_iv(good, bad, g_total, b_total)
    else:
        mwoe = 0.0

    return {
        "source": source,
        "type": "numeric",
        "cuts": cuts,
        "woe": woe_list,
        "missing_bin": {"woe": round(mwoe, 6), "label": "缺失"},
    }


def fit_binary_woe(
    series: pd.Series,
    y: pd.Series,
    source: str = "application",
) -> dict[str, Any]:
    s = pd.to_numeric(series, errors="coerce")
    cats = {}
    g_total = float((y == 0).sum())
    b_total = float((y == 1).sum())
    for val in (0.0, 1.0):
        idx = s == val
        if idx.sum() == 0:
            woe = 0.0
        else:
            yy = y[idx]
            good = float((yy == 0).sum())
            bad = float((yy == 1).sum())
            woe, _ = _woe_iv(good, bad, g_total, b_total)
        label = "是" if val == 1.0 else "否"
        cats[str(int(val))] = {"woe": round(woe, 6), "label": label}

    miss = s.isna()
    if miss.any():
        yy = y[miss]
        good = float((yy == 0).sum())
        bad = float((yy == 1).sum())
        mwoe, _ = _woe_iv(good, bad, g_total, b_total)
    else:
        mwoe = 0.0

    return {
        "source": source,
        "type": "categorical",
        "categories": cats,
        "missing_bin": {"woe": round(mwoe, 6), "label": "缺失"},
    }


def transform_row_woe(raw, feat_def: dict[str, Any]) -> float:
    if raw is None or (isinstance(raw, float) and np.isnan(raw)):
        return float(feat_def.get("missing_bin", {}).get("woe", 0.0))

    ftype = feat_def.get("type", "numeric")
    if ftype == "categorical":
        if raw in (0, 1, 0.0, 1.0):
            key = str(int(raw))
        else:
            key = str(raw)
        cat = feat_def.get("categories", {})
        if key in cat:
            return float(cat[key]["woe"])
        return float(feat_def.get("missing_bin", {}).get("woe", 0.0))

    cuts = feat_def.get("cuts") or []
    woe = feat_def.get("woe") or []
    if not cuts or not woe:
        return 0.0
    x = float(raw)
    for i, edge in enumerate(cuts):
        if x <= edge:
            return float(woe[min(i, len(woe) - 1)])
    return float(woe[-1])


def transform_frame_woe(df: pd.DataFrame, feature_defs: dict[str, dict]) -> pd.DataFrame:
    out = pd.DataFrame(index=df.index)
    for key, fdef in feature_defs.items():
        if key not in df.columns:
            out[key] = fdef.get("missing_bin", {}).get("woe", 0.0)
            continue
        out[key] = df[key].apply(lambda v, fd=fdef: transform_row_woe(v, fd))
    return out


def select_features(
    df: pd.DataFrame,
    candidates: list[str],
    y: pd.Series,
    min_iv: float = 0.02,
    max_features: int = 12,
    corr_threshold: float = 0.3,
) -> tuple[list[str], list[dict]]:
    rows = []
    for col in candidates:
        if col not in df.columns:
            continue
        s = df[col]
        if s.notna().sum() < 50:
            continue
        miss_rate = 1.0 - s.notna().mean()
        if miss_rate > 0.5:
            continue
        if s.nunique(dropna=True) <= 1:
            continue
        if s.nunique(dropna=True) <= 5:
            iv = iv_categorical(s, y)
        else:
            iv = iv_numeric(s, y)
        rows.append({"feature": col, "iv": round(iv, 6), "missing_rate": round(miss_rate, 4)})

    rows.sort(key=lambda r: r["iv"], reverse=True)
    selected: list[str] = []
    report: list[dict] = []
    iv_map = {r["feature"]: r["iv"] for r in rows}

    for r in rows:
        if r["iv"] < min_iv:
            report.append({**r, "selected": False, "reason": "IV<0.02"})
            continue
        if len(selected) >= max_features:
            report.append({**r, "selected": False, "reason": "已达上限"})
            continue
        redundant = False
        for kept in selected:
            a = pd.to_numeric(df[r["feature"]], errors="coerce")
            b = pd.to_numeric(df[kept], errors="coerce")
            corr = a.corr(b)
            if corr is not None and not np.isnan(corr) and abs(corr) > corr_threshold:
                if r["iv"] <= iv_map.get(kept, 0):
                    redundant = True
                    report.append(
                        {**r, "selected": False, "reason": f"与{kept}相关>{corr_threshold}"}
                    )
                    break
        if redundant:
            continue
        selected.append(r["feature"])
        report.append({**r, "selected": True, "reason": "入模"})

    if not selected:
        selected = [c for c in HC_WOE_DEFAULT_FEATURES if c in df.columns][:max_features]

    return selected, report
