import pandas as pd
import numpy as np
import re
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, roc_auc_score
from sklearn.preprocessing import StandardScaler
import json
import os

os.makedirs("output", exist_ok=True)

# 与 Java CreditScoreEngineImpl.buildHcLogisticFeatureMap 键名 100% 一致
HC_FEATURE_COLS = [
    "days_birth",
    "days_employed",
    "amt_income_total",
    "ext_source_2",
    "ext_source_3",
    "amt_req_credit_bureau_mon",
    "amt_req_credit_bureau_week",
    "active_loans_count",
]

THIRD_PARTY_FEATURE_COLS = [
    "ext_source_2",
    "ext_source_3",
    "amt_req_credit_bureau_mon",
    "amt_req_credit_bureau_week",
    "active_loans_count",
]


def edu_tier_from_lc_grade(series):
    """LC 申请时点 grade → 与 Java 学历档位对齐的三档（系数由 CSV 训练得出）。"""

    def one(v):
        if pd.isna(v):
            return "low"
        s = str(v).strip().upper()
        if s in ("A", "B"):
            return "high"
        if s == "C":
            return "mid"
        return "low"

    return series.map(one)


def job_stable_from_lc_emp_length(series):
    """LC emp_length：10+ 年或解析数字≥5 → 稳定就业代理（对齐 Java 公务员/企事业单位）。"""

    def one(val):
        if pd.isna(val):
            return 0
        s = str(val).lower()
        if "10+" in s:
            return 1
        m = re.search(r"(\d+)", s)
        if m:
            try:
                if int(m.group(1)) >= 5:
                    return 1
            except ValueError:
                pass
        return 0

    return series.map(one)


def house_owner_from_lc_home_ownership(series):
    """LC home_ownership：自有/按揭 → 1，其余 → 0（对齐 Java hasHouse）。"""

    def one(v):
        if pd.isna(v):
            return 0
        return 1 if str(v).strip().upper() in ("OWN", "MORTGAGE") else 0

    return series.map(one)

def calculate_scorecard_score(prob_default, scorecard=None):
    """
    将违约概率映射为信用分，与 Java CreditScoreEngineImpl.scorecardFromProb 一致。

    - scale=inverse_prob_0_100：round(100*(1-p)) 再 clamp（旧版）。
    - scale=odds_pdo：PDO+log-odds 映射，低 p 区间对 Δp 更敏感；再 clamp 到 min/max。
    """
    sc = scorecard or {}
    scale = sc.get("scale", "odds_pdo")
    if scale == "inverse_prob_0_100":
        min_s = int(sc.get("min_score", 0))
        max_s = int(sc.get("max_score", 100))
    else:
        min_s = int(sc.get("min_score", 350))
        max_s = int(sc.get("max_score", 950))

    eps = 1e-9
    p = float(np.clip(prob_default, eps, 1.0 - eps))

    if scale == "inverse_prob_0_100":
        score = round(100.0 * (1.0 - p))
        return float(max(min_s, min(max_s, score)))

    pdo = float(sc.get("pdo", 80))
    target_score = float(sc.get("target_score", 650))
    target_odds = float(sc.get("target_odds", 1))
    odds = p / (1.0 - p)
    factor = -pdo / np.log(2)
    offset = target_score - factor * np.log(target_odds)
    score = offset + factor * np.log(odds)
    return float(max(min_s, min(max_s, round(score, 2))))


def scores_odds_pdo_batch(probs, scorecard):
    """校验集向量映射；返回 (raw, clipped)。"""
    sc = scorecard or {}
    min_s = int(sc.get("min_score", 350))
    max_s = int(sc.get("max_score", 950))
    pdo = float(sc.get("pdo", 80))
    target_score = float(sc.get("target_score", 650))
    target_odds = float(sc.get("target_odds", 1))
    eps = 1e-9
    p = np.clip(np.asarray(probs, dtype=np.float64), eps, 1.0 - eps)
    odds = p / (1.0 - p)
    factor = -pdo / np.log(2)
    offset = target_score - factor * np.log(target_odds)
    raw = offset + factor * np.log(odds)
    clipped = np.clip(np.round(raw, 2), min_s, max_s)
    return raw, clipped


def calibrate_odds_pdo_scorecard(probs, min_s=350, max_s=950, span_frac=0.82):
    """
    用校验集违约概率标定 PDO：约 5%–95% 分位的 ln(odds) 跨度映射到量表跨度的 span_frac，
    中位 odds 锚到 target_score=(min_s+max_s)/2。
    """
    eps = 1e-9
    p = np.clip(np.asarray(probs, dtype=np.float64), eps, 1.0 - eps)
    odds = p / (1.0 - p)
    o5 = float(np.quantile(odds, 0.05))
    o95 = float(np.quantile(odds, 0.95))
    o50 = float(np.quantile(odds, 0.50))
    log_den = np.log(o95 + eps) - np.log(o5 + eps)
    if log_den < 1e-6:
        log_den = 1e-6
    span = float(max_s - min_s)
    factor = -(span * float(span_frac)) / log_den
    pdo = float(-factor * np.log(2.0))
    target_odds = max(o50, eps)
    target_score = (min_s + max_s) / 2.0
    return {
        "scale": "odds_pdo",
        "min_score": min_s,
        "max_score": max_s,
        "pdo": round(pdo, 6),
        "target_score": round(target_score, 4),
        "target_odds": round(target_odds, 10),
        "_calibration": {
            "span_frac": span_frac,
            "odds_p05": round(o5, 8),
            "odds_p50": round(o50, 8),
            "odds_p95": round(o95, 8),
            "note": "PDO 由校验集 odds 分位跨度反推；Java 仅消费 scale/pdo/target_*/min/max",
        },
    }


def analyze_feature_default_rates(df):
    """分析各特征与违约率的关系，用于生成评分规则"""
    print("\n=== 特征违约率分析 ===")
    
    results = {}
    
    age_bins = pd.cut(df['age'], bins=[0, 25, 30, 40, 50, 100])
    age_stats = df.groupby(age_bins)['defaulted'].agg(['mean', 'count']).reset_index()
    print("\n年龄分组违约率:")
    print(age_stats)
    results['age'] = []
    for _, row in age_stats.iterrows():
        age_bin = str(row['age'])
        default_rate = row['mean']
        count = row['count']
        if default_rate < 0.1:
            score = 10
        elif default_rate < 0.2:
            score = 5
        elif default_rate < 0.3:
            score = 0
        elif default_rate < 0.4:
            score = -5
        else:
            score = -10
        results['age'].append({
            'bin': age_bin,
            'default_rate': default_rate,
            'count': count,
            'score': score
        })
    
    income_bins = pd.cut(df['income'], bins=[0, 5000, 10000, 15000, float('inf')])
    income_stats = df.groupby(income_bins)['defaulted'].agg(['mean', 'count']).reset_index()
    print("\n收入分组违约率:")
    print(income_stats)
    results['income'] = []
    for _, row in income_stats.iterrows():
        income_bin = str(row['income'])
        default_rate = row['mean']
        count = row['count']
        if default_rate < 0.1:
            score = 15
        elif default_rate < 0.2:
            score = 5
        elif default_rate < 0.3:
            score = -5
        else:
            score = -15
        results['income'].append({
            'bin': income_bin,
            'default_rate': default_rate,
            'count': count,
            'score': score
        })
    
    multi_head_bins = pd.cut(df['multi_head_loan_count'], bins=[-1, 3, 6, 10, float('inf')])
    multi_head_stats = df.groupby(multi_head_bins)['defaulted'].agg(['mean', 'count']).reset_index()
    print("\n多头借贷分组违约率:")
    print(multi_head_stats)
    results['multi_head'] = []
    for _, row in multi_head_stats.iterrows():
        bin_name = str(row['multi_head_loan_count'])
        default_rate = row['mean']
        count = row['count']
        if default_rate < 0.15:
            score = 0
        elif default_rate < 0.3:
            score = -10
        elif default_rate < 0.5:
            score = -20
        else:
            score = -30
        results['multi_head'].append({
            'bin': bin_name,
            'default_rate': default_rate,
            'count': count,
            'score': score
        })
    
    query_bins = pd.cut(df['credit_query_count_3m'], bins=[-1, 3, 6, 10, float('inf')])
    query_stats = df.groupby(query_bins)['defaulted'].agg(['mean', 'count']).reset_index()
    print("\n征信查询分组违约率:")
    print(query_stats)
    results['credit_query'] = []
    for _, row in query_stats.iterrows():
        bin_name = str(row['credit_query_count_3m'])
        default_rate = row['mean']
        count = row['count']
        if default_rate < 0.15:
            score = 0
        elif default_rate < 0.3:
            score = -5
        elif default_rate < 0.5:
            score = -10
        else:
            score = -15
        results['credit_query'].append({
            'bin': bin_name,
            'default_rate': default_rate,
            'count': count,
            'score': score
        })
    
    overdue_stats = df.groupby('overdue_count_12m')['defaulted'].agg(['mean', 'count']).reset_index()
    print("\n逾期次数统计:")
    print(overdue_stats)
    results['overdue'] = []
    for _, row in overdue_stats.iterrows():
        count = int(row['overdue_count_12m'])
        default_rate = row['mean']
        total_count = row['count']
        if count == 0:
            score = 0
        elif count <= 1:
            score = -15
        elif count <= 2:
            score = -25
        else:
            score = -40
        results['overdue'].append({
            'count': count,
            'default_rate': default_rate,
            'total_count': total_count,
            'score': score
        })
    
    dti_bins = pd.cut(df['dti'], bins=[-1, 15, 30, float('inf')])
    dti_stats = df.groupby(dti_bins)['defaulted'].agg(['mean', 'count']).reset_index()
    print("\nDTI分组违约率:")
    print(dti_stats)
    results['dti'] = []
    for _, row in dti_stats.iterrows():
        bin_name = str(row['dti'])
        default_rate = row['mean']
        count = row['count']
        if default_rate < 0.15:
            score = 5
        elif default_rate < 0.3:
            score = 0
        else:
            score = -15
        results['dti'].append({
            'bin': bin_name,
            'default_rate': default_rate,
            'count': count,
            'score': score
        })
    
    return results

def bin_features(df):
    """
    与 Java CreditScoreEngineImpl 约定一致的分箱 + one-hot（drop_first）。
    方案 A：分箱后对已与 dummy 重复的原始连续列做删除，避免同一信号被计数两次，
    导致系数过大、概率饱和、评分总贴在 10/90。
    保留 device_is_virtual / ip_is_proxy（未参与上述分箱）。
    """
    df = df.copy()

    df['age_bin'] = pd.cut(df['age'], bins=[0, 25, 35, 50, 100], labels=['age_0_25', 'age_26_35', 'age_36_50', 'age_51_plus'])
    df['income_bin'] = pd.cut(df['income'], bins=[0, 5000, 15000, float('inf')], labels=['income_below_5000', 'income_5000_15000', 'income_15000_plus'])
    df['multi_head_bin'] = pd.cut(df['multi_head_loan_count'], bins=[-1, 3, 6, float('inf')], labels=['multi_head_0_3', 'multi_head_4_6', 'multi_head_7_plus'])
    df['credit_query_bin'] = pd.cut(df['credit_query_count_3m'], bins=[-1, 3, 8, float('inf')], labels=['credit_query_0_3', 'credit_query_4_8', 'credit_query_9_plus'])
    df['overdue_bin'] = pd.cut(df['overdue_count_12m'], bins=[-1, 0, 2, float('inf')], labels=['overdue_12m_0', 'overdue_12m_1_2', 'overdue_12m_3_plus'])
    df['dti_bin'] = pd.cut(df['dti'], bins=[-1, 15, 30, float('inf')], labels=['dti_low', 'dti_medium', 'dti_high'])

    df = pd.get_dummies(df, columns=['age_bin', 'income_bin', 'multi_head_bin', 'credit_query_bin', 'overdue_bin', 'dti_bin'], drop_first=True)

    if "edu_tier" in df.columns:
        df["edu_tier"] = pd.Categorical(df["edu_tier"], categories=["low", "mid", "high"], ordered=True)
        df = pd.get_dummies(df, columns=["edu_tier"], prefix="edu_tier", drop_first=True)

    redundant_raw = ['age', 'income', 'multi_head_loan_count', 'credit_query_count_3m', 'overdue_count_12m', 'dti']
    to_drop = [c for c in redundant_raw if c in df.columns]
    if to_drop:
        df = df.drop(columns=to_drop)

    return df

def process_lending_club_data(df):
    """处理Lending Club数据集，转换为模型所需格式"""
    df = df.copy()
    
    df = df[df['loan_status'].isin(['Charged Off', 'Fully Paid'])].copy()
    
    df['income'] = df['annual_inc'] / 12
    
    df['credit_query_count_3m'] = (df['inq_last_6mths'] / 2).fillna(0).astype(int)
    
    df['multi_head_loan_count'] = df['open_acc'].fillna(0).astype(int)

    # 申请时点逾期次数：禁止用 loan_status 反推（与 defaulted 等价 → 标签泄漏）
    delinq_col = None
    for name in ("delinq_2yrs", "delinq_2yr"):
        if name in df.columns:
            delinq_col = name
            break
    if delinq_col is not None:
        df["overdue_count_12m"] = (
            pd.to_numeric(df[delinq_col], errors="coerce").fillna(0).clip(0, 30).astype(int)
        )
        print(f"Lending Club：使用申请时点字段 {delinq_col} → overdue_count_12m")
    else:
        df["overdue_count_12m"] = 0
        print(
            "警告：未找到 delinq_2yrs / delinq_2yr，overdue_count_12m 置 0。"
            "请勿用 loan_status 构造逾期特征。"
        )

    df['dti'] = df['dti'].fillna(df['dti'].median())

    rng = np.random.default_rng(42)
    df["age"] = rng.integers(22, 60, size=len(df))
    df["device_is_virtual"] = rng.choice([0, 1], size=len(df), p=[0.97, 0.03])
    df["ip_is_proxy"] = rng.choice([0, 1], size=len(df), p=[0.95, 0.05])

    # 申请表代理特征：仅用 LC 列推导（与 scoring_rules.feature_derivation 一致）
    if "grade" in df.columns:
        df["edu_tier"] = edu_tier_from_lc_grade(df["grade"])
    else:
        df["edu_tier"] = "low"
        print("警告：LC 缺少 grade，edu_tier 暂置为 low")

    if "home_ownership" in df.columns:
        df["house_owner"] = house_owner_from_lc_home_ownership(df["home_ownership"]).astype(int)
    else:
        df["house_owner"] = 0
        print("警告：LC 缺少 home_ownership，house_owner 置 0")

    if "emp_length" in df.columns:
        df["job_stable"] = job_stable_from_lc_emp_length(df["emp_length"]).astype(int)
    else:
        df["job_stable"] = 0
        print("警告：LC 缺少 emp_length，job_stable 置 0")

    df["has_car_stated"] = 0

    df['defaulted'] = (df['loan_status'] == 'Charged Off').astype(int)
    
    return df


def process_hc_raw(df):
    """Home Credit 原始宽表 → 统一特征名（与 user_external_features / Java 一致）。"""
    out = pd.DataFrame()
    out["days_birth"] = pd.to_numeric(df["DAYS_BIRTH"], errors="coerce")
    out["days_employed"] = pd.to_numeric(df["DAYS_EMPLOYED"], errors="coerce")
    out["amt_income_total"] = pd.to_numeric(df["AMT_INCOME_TOTAL"], errors="coerce")
    out["ext_source_2"] = pd.to_numeric(df["EXT_SOURCE_2"], errors="coerce")
    out["ext_source_3"] = pd.to_numeric(df["EXT_SOURCE_3"], errors="coerce")
    out["amt_req_credit_bureau_mon"] = pd.to_numeric(
        df["AMT_REQ_CREDIT_BUREAU_MON"], errors="coerce"
    )
    out["amt_req_credit_bureau_week"] = pd.to_numeric(
        df["AMT_REQ_CREDIT_BUREAU_WEEK"], errors="coerce"
    )
    if "active_loans_count" in df.columns:
        out["active_loans_count"] = pd.to_numeric(df["active_loans_count"], errors="coerce")
    else:
        out["active_loans_count"] = 0
    out["defaulted"] = pd.to_numeric(df["TARGET"], errors="coerce").fillna(0).astype(int)
    return out


def process_hc_cleaned(df):
    """clean_user_features.py 输出 → 统一特征名。"""
    out = pd.DataFrame()
    out["days_birth"] = -pd.to_numeric(df["age"], errors="coerce").fillna(30) * 365
    out["days_employed"] = -pd.to_numeric(df["employment_years"], errors="coerce").fillna(0) * 365
    out["amt_income_total"] = pd.to_numeric(df["AMT_INCOME_TOTAL"], errors="coerce")
    out["ext_source_2"] = pd.to_numeric(df["ext_source_2"], errors="coerce")
    out["ext_source_3"] = pd.to_numeric(df["ext_source_3"], errors="coerce")
    out["amt_req_credit_bureau_mon"] = pd.to_numeric(df["credit_query_month"], errors="coerce")
    out["amt_req_credit_bureau_week"] = pd.to_numeric(df["credit_query_week"], errors="coerce")
    out["active_loans_count"] = pd.to_numeric(df["active_loans_count"], errors="coerce")
    out["defaulted"] = pd.to_numeric(df["has_default_history"], errors="coerce").fillna(0).astype(int)
    return out


def load_hc_training_data():
    """优先 parquet 全量；其次 HC 原始 CSV；缺失时合成样本。"""
    parquet_path = "data/raw/home_credit_train_min.parquet"
    if os.path.isfile(parquet_path):
        try:
            raw = pd.read_parquet(parquet_path)
            if "DAYS_BIRTH" in raw.columns and "TARGET" in raw.columns:
                df = process_hc_raw(raw)
                print(f"HC 训练数据: {parquet_path} ({len(df)} 行)")
                return df
        except Exception as e:
            print(f"读取 parquet 失败: {e}，尝试 CSV…")

    candidates = [
        ("data/raw/home_credit_train_ready.csv", "raw"),
        ("data/cleaned/cleaned_user_features.csv", "cleaned"),
    ]
    for path, kind in candidates:
        if not os.path.isfile(path):
            continue
        for enc in ("utf-8-sig", "gbk", "gb18030"):
            try:
                raw = pd.read_csv(path, encoding=enc, low_memory=False)
                if kind == "raw" and "DAYS_BIRTH" in raw.columns and "TARGET" in raw.columns:
                    df = process_hc_raw(raw)
                    print(f"HC 训练数据: {path} ({len(df)} 行)")
                    return df
                if kind == "cleaned" and "ext_source_2" in raw.columns:
                    df = process_hc_cleaned(raw)
                    print(f"HC 训练数据(清洗): {path} ({len(df)} 行)")
                    return df
            except Exception:
                continue

    print("未找到 HC 数据，使用合成 HC 特征训练（字段与线上一致）")
    n = 8000
    rng = np.random.default_rng(42)
    age = rng.integers(22, 60, size=n)
    df = pd.DataFrame(
        {
            "days_birth": -age * 365,
            "days_employed": -rng.uniform(0, 20, size=n) * 365,
            "amt_income_total": rng.integers(80000, 600000, size=n),
            "ext_source_2": rng.beta(2, 5, size=n),
            "ext_source_3": rng.beta(2, 5, size=n),
            "amt_req_credit_bureau_mon": rng.poisson(2, size=n),
            "amt_req_credit_bureau_week": rng.poisson(1, size=n),
            "active_loans_count": rng.poisson(1.5, size=n),
        }
    )
    risk = (
        (df["ext_source_2"] < 0.2)
        | (df["amt_req_credit_bureau_mon"] > 5)
        | (df["active_loans_count"] > 4)
        | (df["amt_income_total"] < 120000)
    )
    df["defaulted"] = (risk.astype(int) + rng.random(n) < 0.35).astype(int)
    return df


def train_hc_scoring_model(df):
    """Home Credit 全链路 LR：申请表代理 + 第三方征信特征联合训练。"""
    for col in HC_FEATURE_COLS:
        if col not in df.columns:
            df[col] = 0
    for col in THIRD_PARTY_FEATURE_COLS:
        df[col] = df[col].fillna(df[col].mean() if df[col].notna().any() else 0)

    X_raw = df[HC_FEATURE_COLS].astype(float)
    y = df["defaulted"].astype(int)

    try:
        X_train, X_test, y_train, y_test = train_test_split(
            X_raw, y, test_size=0.2, random_state=42, stratify=y
        )
    except ValueError:
        X_train, X_test, y_train, y_test = train_test_split(
            X_raw, y, test_size=0.2, random_state=42
        )

    scaler = StandardScaler()
    X_train_s = scaler.fit_transform(X_train)
    X_test_s = scaler.transform(X_test)

    model = LogisticRegression(
        class_weight="balanced", random_state=42, max_iter=500, C=0.5, solver="lbfgs"
    )
    model.fit(X_train_s, y_train)

    y_pred_proba = model.predict_proba(X_test_s)[:, 1]
    accuracy = accuracy_score(y_test, model.predict(X_test_s))
    auc = roc_auc_score(y_test, y_pred_proba)

    feature_weights = {k: float(v) for k, v in zip(HC_FEATURE_COLS, model.coef_[0])}
    feature_scaler = {}
    for i, col in enumerate(HC_FEATURE_COLS):
        feature_scaler[col] = {
            "mean": float(scaler.mean_[i]),
            "scale": float(scaler.scale_[i]) if scaler.scale_[i] != 0 else 1.0,
        }

    print("\n=== HC 模型权重（含第三方 ext_source_2/3）===")
    for k, w in sorted(feature_weights.items(), key=lambda x: abs(x[1]), reverse=True):
        tag = " [第三方]" if k in THIRD_PARTY_FEATURE_COLS else " [申请/收入]"
        print(f"  {k}: {w:.4f}{tag}")

    scorecard_cfg = calibrate_odds_pdo_scorecard(y_pred_proba, min_s=350, max_s=950, span_frac=0.82)
    _, clipped_lr = scores_odds_pdo_batch(y_pred_proba, scorecard_cfg)
    auto_ap = float(np.quantile(clipped_lr, 0.82))
    man_rev = float(np.quantile(clipped_lr, 0.48))

    feature_derivation = {
        "days_birth": "训练/推理：申请表生日 → 负天数（与 HC DAYS_BIRTH 一致）",
        "days_employed": "训练/推理：user_external_features.days_employed（入职天数，负值）",
        "amt_income_total": "训练/推理：年总收入；申请表月收入×12 或第三方后台收入",
        "ext_source_2": "仅来自第三方征信 user_external_features.ext_source_2",
        "ext_source_3": "仅来自第三方征信 user_external_features.ext_source_3",
        "amt_req_credit_bureau_mon": "第三方：近1月征信查询次数",
        "amt_req_credit_bureau_week": "第三方：近1周征信查询次数",
        "active_loans_count": "第三方：活跃贷款数",
    }

    rules = {
        "version": "v6.0-hc",
        "model_type": "hc_lr_standardized",
        "description": "Home Credit 全链路：申请表+第三方征信标准化后 LR",
        "feature_derivation": feature_derivation,
        "feature_weights": feature_weights,
        "feature_scaler": feature_scaler,
        "intercept": float(model.intercept_[0]),
        "thresholds": {
            "auto_approve": round(auto_ap),
            "manual_review": round(man_rev),
        },
        "scorecard": scorecard_cfg,
        "application_rule_bonus": {"enabled": False},
        "training_data": {
            "total_count": len(df),
            "default_rate": float(y.mean()),
            "data_source": "Home Credit (HC)",
            "third_party_features": THIRD_PARTY_FEATURE_COLS,
        },
        "model_metrics": {
            "accuracy": float(accuracy),
            "auc": float(auc),
        },
    }

    with open("output/scoring_rules.json", "w", encoding="utf-8") as f:
        json.dump(rules, f, ensure_ascii=False, indent=2)

    print(f"\n训练完成 AUC={auc:.4f}，规则已写入 output/scoring_rules.json")
    return rules


def load_hc_training_data_woe():
    """从 parquet 构建 WOE 候选特征宽表。"""
    from feature_engineering_hc import build_hc_training_features, load_hc_raw_parquet

    parquet_path = "data/raw/home_credit_train_min.parquet"
    if os.path.isfile(parquet_path):
        try:
            raw = load_hc_raw_parquet(parquet_path)
            if "TARGET" in raw.columns:
                df = build_hc_training_features(raw)
                print(f"HC WOE 训练数据: {parquet_path} ({len(df)} 行, {df.shape[1]-1} 候选特征)")
                return df
        except Exception as e:
            print(f"读取 parquet 失败: {e}")

    print("未找到 HC parquet，WOE 训练回退到 8 维 HC 管道")
    return load_hc_training_data()


def train_hc_woe_scoring_model(df):
    """Home Credit：IV 筛选 → 分箱 WOE → LR → PDO 350-950。"""
    from feature_engineering_hc import HC_CANDIDATE_FEATURES, FEATURE_SOURCE
    from woe_binning import (
        fit_feature_woe,
        select_features,
        transform_frame_woe,
        transform_row_woe,
    )

    candidates = [c for c in HC_CANDIDATE_FEATURES if c in df.columns]
    y = df["defaulted"].astype(int)

    try:
        train_idx, test_idx = train_test_split(
            df.index, test_size=0.2, random_state=42, stratify=y
        )
    except ValueError:
        train_idx, test_idx = train_test_split(df.index, test_size=0.2, random_state=42)

    train_df = df.loc[train_idx]
    test_df = df.loc[test_idx]
    y_train = y.loc[train_idx]
    y_test = y.loc[test_idx]

    selected, iv_report = select_features(train_df, candidates, y_train, max_features=12)
    print(f"\n=== IV 筛选：候选 {len(candidates)} → 入模 {len(selected)} ===")
    for row in iv_report:
        mark = "✓" if row.get("selected") else " "
        print(f"  [{mark}] {row['feature']}: IV={row['iv']}, miss={row.get('missing_rate', '-')}, {row.get('reason', '')}")

    feature_defs = {}
    for key in selected:
        feature_defs[key] = fit_feature_woe(train_df[key], y_train, key)

    X_train = transform_frame_woe(train_df, feature_defs)
    X_test = transform_frame_woe(test_df, feature_defs)

    model = LogisticRegression(
        class_weight="balanced", random_state=42, max_iter=500, C=0.5, solver="lbfgs"
    )
    model.fit(X_train, y_train)

    y_pred_proba = model.predict_proba(X_test)[:, 1]
    accuracy = accuracy_score(y_test, model.predict(X_test))
    auc = roc_auc_score(y_test, y_pred_proba)

    coefficients = {k: float(v) for k, v in zip(selected, model.coef_[0])}

    scorecard_cfg = calibrate_odds_pdo_scorecard(y_pred_proba, min_s=350, max_s=950, span_frac=0.82)
    _, clipped_lr = scores_odds_pdo_batch(y_pred_proba, scorecard_cfg)
    auto_ap = float(np.quantile(clipped_lr, 0.82))
    man_rev = float(np.quantile(clipped_lr, 0.48))

    feature_derivation = {
        k: f"来源={FEATURE_SOURCE.get(k, 'external')}; WOE+LR 入模"
        for k in selected
    }

    rules = {
        "version": "v7.0-hc-woe",
        "model_type": "hc_woe_lr",
        "description": "Home Credit：IV筛选+分箱WOE+LR+PDO(350-950)",
        "feature_derivation": feature_derivation,
        "features": feature_defs,
        "coefficients": coefficients,
        "intercept": float(model.intercept_[0]),
        "thresholds": {
            "auto_approve": round(auto_ap),
            "manual_review": round(man_rev),
            "_note": "测试集 PDO 分位；量表 350-950",
        },
        "scorecard": scorecard_cfg,
        "application_rule_bonus": {"enabled": False},
        "feature_selection_report": {
            "candidates": len(candidates),
            "selected": len(selected),
            "selected_features": selected,
            "iv_table": iv_report,
        },
        "training_data": {
            "total_count": len(df),
            "default_rate": float(y.mean()),
            "data_source": "Home Credit (HC)",
        },
        "model_metrics": {
            "accuracy": float(accuracy),
            "auc": float(auc),
        },
    }

    with open("output/scoring_rules.json", "w", encoding="utf-8") as f:
        json.dump(rules, f, ensure_ascii=False, indent=2)

    print(f"\nWOE 训练完成 AUC={auc:.4f}，阈值 auto={round(auto_ap)} manual={round(man_rev)}")
    print("规则已写入 output/scoring_rules.json (v7.0-hc-woe)")
    return rules


def train_scoring_model(csv_path="data/training_data.csv", use_hc=True, use_woe=True):
    """默认使用 Home Credit WOE 评分卡（v7.0-hc-woe）。"""
    if use_hc:
        if use_woe:
            df = load_hc_training_data_woe()
            if "gender_male" in df.columns:
                rules = train_hc_woe_scoring_model(df)
            else:
                rules = train_hc_scoring_model(df)
        else:
            df = load_hc_training_data()
            rules = train_hc_scoring_model(df)
        try:
            from load_to_mysql import load_scoring_rules_to_mysql

            load_scoring_rules_to_mysql()
        except ImportError:
            print("提示：运行 python load_to_mysql.py 写入评分规则与外部特征")
        return rules

    try:
        df = pd.read_csv(csv_path, encoding="utf-8-sig", low_memory=False)
        print(f"成功加载训练数据: {len(df)} 行")
        print(f"数据列名: {list(df.columns)[:10]}...")
        
        if 'loan_status' in df.columns and 'annual_inc' in df.columns:
            print("检测到Lending Club数据集格式，进行数据转换...")
            df = process_lending_club_data(df)
            print(f"转换后数据行数: {len(df)}")
            print(f"违约样本比例: {df['defaulted'].mean():.2%}")
        elif 'age' not in df.columns or 'income' not in df.columns:
            print("警告：训练数据列名不匹配，将使用模拟数据进行训练")
            raise FileNotFoundError("列名不匹配")
    
    except FileNotFoundError:
        print(f"使用模拟数据进行训练")
        n = 1000
        rng = np.random.default_rng(42)
        users = pd.DataFrame({
            "id_card": [f"510106{rng.integers(1970, 2005):04d}{rng.integers(1, 13):02d}{rng.integers(1, 29):02d}{i:04d}" for i in range(n)],
            "age": rng.integers(22, 60, size=n),
            "income": rng.integers(3000, 50000, size=n),
            "multi_head_loan_count": rng.poisson(lam=2, size=n).clip(0, 15),
            "credit_query_count_3m": rng.poisson(lam=3, size=n).clip(0, 20),
            "overdue_count_12m": rng.choice([0, 1, 2, 3, 5], size=n, p=[0.75, 0.12, 0.07, 0.04, 0.02]),
            "dti": rng.integers(0, 40, size=n),
            "device_is_virtual": rng.choice([0, 1], size=n, p=[0.97, 0.03]),
            "ip_is_proxy": rng.choice([0, 1], size=n, p=[0.95, 0.05])
        })
        users["edu_tier"] = rng.choice(["low", "mid", "high"], size=n, p=[0.25, 0.45, 0.3])
        users["house_owner"] = rng.choice([0, 1], size=n, p=[0.35, 0.65]).astype(int)
        users["job_stable"] = rng.choice([0, 1], size=n, p=[0.4, 0.6]).astype(int)
        users["has_car_stated"] = rng.choice([0, 1], size=n, p=[0.45, 0.55]).astype(int)
        users["marriage_married"] = rng.choice([0, 1], size=n, p=[0.42, 0.58]).astype(int)
        users["defaulted"] = (
            (users["multi_head_loan_count"] > 6) |
            (users["credit_query_count_3m"] > 10) |
            (users["overdue_count_12m"] >= 3) |
            (users["income"] < 5000) |
            (users["device_is_virtual"] == 1) |
            (users["dti"] > 30) |
            (users["edu_tier"] == "low") |
            (users["house_owner"] == 0)
        ).astype(int)
        df = users
    
    feature_analysis = analyze_feature_default_rates(df)

    base_numeric = [
        "age",
        "income",
        "multi_head_loan_count",
        "credit_query_count_3m",
        "overdue_count_12m",
        "dti",
        "device_is_virtual",
        "ip_is_proxy",
    ]
    app_numeric = ["edu_tier", "house_owner", "job_stable", "has_car_stated"]
    if "marriage_married" in df.columns:
        app_numeric.append("marriage_married")
    feature_cols = [c for c in base_numeric + app_numeric if c in df.columns]
    X = df[feature_cols]
    y = df['defaulted']
    
    X_binned = bin_features(X)
    
    X_train, X_test, y_train, y_test = train_test_split(X_binned, y, test_size=0.2, random_state=42)
    
    # C<1 增强 L2，抑制系数爆炸与 sigmoid 饱和。若需概率校准可在此基础上套
    # CalibratedClassifierCV(method="sigmoid", cv=3)，并另行持久化校准参数供推理使用。
    model = LogisticRegression(
        class_weight="balanced", random_state=42, max_iter=400, C=0.3, solver="lbfgs"
    )
    model.fit(X_train, y_train)
    
    y_pred = model.predict(X_test)
    y_pred_proba = model.predict_proba(X_test)[:, 1]
    
    accuracy = accuracy_score(y_test, y_pred)
    auc = roc_auc_score(y_test, y_pred_proba)
    
    print(f"\n模型训练完成")
    print(f"训练数据量: {len(df)} 条")
    print(f"测试准确率: {accuracy:.4f}")
    print(f"测试AUC: {auc:.4f}")
    
    feature_weights = dict(zip(X_binned.columns, model.coef_[0]))
    
    print(f"\n模型权重:")
    for feature, weight in sorted(feature_weights.items(), key=lambda x: abs(x[1]), reverse=True):
        print(f"{feature}: {weight:.4f}")

    qs = np.quantile(y_pred_proba, [0.05, 0.25, 0.5, 0.75, 0.95])
    print(f"\n测试集违约概率分位数(5/25/50/75/95%): {qs}")

    scorecard_cfg = calibrate_odds_pdo_scorecard(y_pred_proba, min_s=350, max_s=950, span_frac=0.82)
    raw_lr, clipped_lr = scores_odds_pdo_batch(y_pred_proba, scorecard_cfg)
    lo = float(scorecard_cfg["min_score"])
    hi = float(scorecard_cfg["max_score"])
    pct_floor = float(np.mean(raw_lr < lo))
    pct_ceil = float(np.mean(raw_lr > hi))
    print(
        f"PDO 标定: pdo={scorecard_cfg['pdo']}, target_odds={scorecard_cfg['target_odds']}, "
        f"夹紧 raw<min={pct_floor:.2%}, raw>max={pct_ceil:.2%}"
    )

    auto_ap = float(np.quantile(clipped_lr, 0.82))
    man_rev = float(np.quantile(clipped_lr, 0.48))
    thresholds_cfg = {
        "auto_approve": round(auto_ap),
        "manual_review": round(man_rev),
        "_note": "测试集 LR+PDO 分数分位导出（非旧 80/50 线性换算）；可按业务再调",
    }

    prob_default_avg = y_pred_proba.mean()
    avg_score = calculate_scorecard_score(prob_default_avg, scorecard_cfg)
    
    feature_correlations = {
        "overdue_count_12m": round(float(df['overdue_count_12m'].corr(df['defaulted'])), 4),
        "multi_head_loan_count": round(float(df['multi_head_loan_count'].corr(df['defaulted'])), 4),
        "credit_query_count_3m": round(float(df['credit_query_count_3m'].corr(df['defaulted'])), 4),
        "income": round(float(df['income'].corr(df['defaulted'])), 4),
        "dti": round(float(df['dti'].corr(df['defaulted'])), 4),
        "age": round(float(df['age'].corr(df['defaulted'])), 4)
    }
    
    feature_derivation = {
        "edu_tier": (
            "训练(LC): grade A,B→high; C→mid; D/E/F/G/缺失→low。推理(Java): 博士/硕士→high，本科→mid，高中及以下→low。"
            "共享 edu_tier_mid / edu_tier_high 系数。"
        ),
        "house_owner": (
            "训练(LC): home_ownership∈{OWN,MORTGAGE}→1，否则→0。推理(Java): hasHouse==true→1。"
        ),
        "job_stable": (
            "训练(LC): emp_length 含 10+ 或解析年限≥5→1，否则→0。推理(Java): jobType∈{公务员,企事业单位}→1，否则→0。"
        ),
        "has_car_stated": (
            "训练(LC): 无车字段，列恒为 0；系数主要来自模拟数据或未来含车 CSV。推理(Java): hasCar==true→1。"
        ),
        "marriage_married": (
            "训练(LC): 通常无该列则不进入矩阵；模拟路径含 0/1。推理(Java): 已婚→1。若无权重则贡献为 0。"
        ),
    }

    # 路径 A：LR+PDO 分之上的策略加减分（单位：350–950 量表上的分）
    application_rule_bonus = {
        "enabled": True,
        "cap_absolute_sum": 55,
        "notes": (
            "主分数为 PDO+log-odds；bonus 与 LR 系数无关。"
            "cap/bonus 按量表跨度设定，勿与旧 0–100 线性倍乘混用。"
        ),
        "married_equals_bonus": {"match": "已婚", "bonus": 18},
        "has_car_bonus": 12,
        "age_rules": [
            {"lte": 25, "bonus": -22, "reason": "年龄≤25 低龄违约率偏高"},
            {"gte": 26, "lte": 35, "bonus": 0, "reason": "26–35 基准档"},
            {"gte": 36, "lte": 50, "bonus": 16, "reason": "36–50 风险相对较低"},
            {"gte": 51, "bonus": 10, "reason": "51+"},
        ],
    }

    rules = {
        "version": "v5.0",
        "description": "PDO+log-odds 映射至 350–950（校验集标定）；策略 bonus 同量表",
        "feature_derivation": feature_derivation,
        "feature_weights": feature_weights,
        "intercept": float(model.intercept_[0]),
        "thresholds": thresholds_cfg,
        "scorecard": scorecard_cfg,
        "application_rule_bonus": application_rule_bonus,
        "feature_scores": {
            "age": {
                "description": "年龄评分",
                "groups": [
                    {"range": [22, 25], "score": -10, "reason": "年轻，还款能力不稳定"},
                    {"range": [25, 30], "score": -5, "reason": "较年轻"},
                    {"range": [30, 40], "score": 8, "reason": "黄金年龄段，还款能力强"},
                    {"range": [40, 50], "score": 5, "reason": "稳定年龄段"},
                    {"range": [50, 60], "score": -3, "reason": "年龄较大"}
                ]
            },
            "income": {
                "description": "月收入评分",
                "groups": [
                    {"range": [0, 3000], "score": -15, "reason": "收入较低"},
                    {"range": [3000, 8000], "score": -5, "reason": "收入一般"},
                    {"range": [8000, 15000], "score": 5, "reason": "收入中等"},
                    {"range": [15000, 999999], "score": 15, "reason": "收入较高"}
                ]
            },
            "multi_head_loan_count": {
                "description": "多头借贷评分",
                "groups": [
                    {"range": [0, 3], "score": 0, "reason": "正常"},
                    {"range": [3, 6], "score": -10, "reason": "中等多头"},
                    {"range": [6, 10], "score": -20, "reason": "高多头"},
                    {"range": [10, 999], "score": -30, "reason": "极高多头"}
                ]
            },
            "credit_query_count_3m": {
                "description": "征信查询评分",
                "groups": [
                    {"range": [0, 3], "score": 0, "reason": "正常"},
                    {"range": [3, 6], "score": -5, "reason": "查询较多"},
                    {"range": [6, 10], "score": -10, "reason": "查询频繁"},
                    {"range": [10, 999], "score": -15, "reason": "异常频繁"}
                ]
            },
            "overdue_count_12m": {
                "description": "逾期评分",
                "groups": [
                    {"range": [0, 1], "score": 0, "reason": "无逾期"},
                    {"range": [1, 2], "score": -15, "reason": "有逾期记录"},
                    {"range": [2, 3], "score": -25, "reason": "多次逾期"},
                    {"range": [3, 99], "score": -40, "reason": "严重逾期"}
                ]
            },
            "dti": {
                "description": "负债收入比评分",
                "groups": [
                    {"range": [0, 15], "score": 5, "reason": "负债低"},
                    {"range": [15, 30], "score": 0, "reason": "负债正常"},
                    {"range": [30, 100], "score": -15, "reason": "负债较高"}
                ]
            },
            "job_type": {
                "description": "工作类型评分",
                "scores": {
                    "公务员": 15,
                    "企事业单位": 10,
                    "私营企业": 0,
                    "其他": -5
                }
            },
            "education": {
                "description": "学历评分",
                "scores": {
                    "博士": 15,
                    "硕士": 10,
                    "本科": 5,
                    "高中及以下": -5
                }
            },
            "has_house": {
                "description": "房产评分",
                "has": 10,
                "none": 0
            },
            "has_car": {
                "description": "车产评分",
                "has": 5,
                "none": 0
            },
            "marriage": {
                "description": "婚姻评分",
                "married": 5,
                "single": 0
            }
        },
        "training_data": {
            "total_count": len(df),
            "default_rate": float(df['defaulted'].mean()),
            "avg_age": float(df['age'].mean()),
            "avg_income": float(df['income'].mean()),
            "avg_multi_head": float(df['multi_head_loan_count'].mean()),
            "avg_credit_query": float(df['credit_query_count_3m'].mean()),
            "feature_correlations": feature_correlations
        },
        "model_metrics": {
            "accuracy": accuracy,
            "auc": auc,
            "avg_prob_default": round(float(prob_default_avg), 4),
            "avg_score": avg_score,
            "scorecard_lr_clip_pct_below_min": round(pct_floor, 4),
            "scorecard_lr_clip_pct_above_max": round(pct_ceil, 4),
            "score_quantiles_test_lr_only": [
                round(float(x), 2)
                for x in np.quantile(clipped_lr, [0.05, 0.25, 0.5, 0.75, 0.95]).tolist()
            ],
        }
    }
    
    with open("output/scoring_rules.json", "w", encoding="utf-8") as f:
        json.dump(rules, f, ensure_ascii=False, indent=2)
    
    print("\n评分规则已保存到 output/scoring_rules.json")
    print(f"平均违约概率: {prob_default_avg:.4f}")
    print(f"对应评分卡分数: {avg_score}")
    
    test_samples = pd.DataFrame({
        'prob_default': [0.001, 0.01, 0.05, 0.1, 0.2, 0.3, 0.5],
        'desc': ['极低风险', '低风险', '中低风险', '中等风险', '中高风险', '高风险', '极高风险']
    })
    test_samples["score"] = test_samples["prob_default"].apply(
        lambda p: calculate_scorecard_score(p, scorecard_cfg)
    )
    print("\n评分卡分数示例:")
    print(test_samples.to_string(index=False))
    
    try:
        from load_to_mysql import load_scoring_rules_to_mysql, load_sample_external_features_to_mysql
        load_scoring_rules_to_mysql()
        load_sample_external_features_to_mysql()
    except ImportError:
        print("\n提示：运行 python load_to_mysql.py 将评分规则与联调示例外部特征写入数据库")
    
    return rules


# =============================================================================
# 3.3 用户外部特征表（索引：id_card）——联调示例
# 数据源码：load_to_mysql.SAMPLE_EXTERNAL_FEATURES_ROWS；写入：
#   load_to_mysql.load_sample_external_features_to_mysql()
# train_scoring_model 训练结束时会尝试自动 upsert；亦可 python load_to_mysql.py（步骤4b）。
# 注册/风控手机号示例：13800148001 起（见 UserRegisterRequest）。
#
# 可选 DTI：ALTER TABLE user_external_features ADD COLUMN dti INT ... 后在 load_to_mysql 扩展 ROWS。
# =============================================================================

try:
    from load_to_mysql import build_sample_external_features_sql

    SAMPLE_EXTERNAL_FEATURES_SQL = build_sample_external_features_sql()
except ImportError:
    SAMPLE_EXTERNAL_FEATURES_SQL = ""


if __name__ == "__main__":
    train_scoring_model()
