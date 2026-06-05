"""
离线复现 Java v7 WOE+PDO 算分与收入验真，用于校准 sql/测试用例.md 预期结果。
"""
from __future__ import annotations

import json
import math
from datetime import datetime

import pandas as pd

from train_scoring_model import calculate_scorecard_score
from woe_binning import transform_row_woe

CSV_PATH = "data/cleaned/cleaned_user_features.csv"
RULES_PATH = "output/scoring_rules.json"

INCOME_RANGES = {
    "3000以下": (0, 3000),
    "3000-5000": (3000, 5000),
    "5000-8000": (5000, 8000),
    "8000-15000": (8000, 15000),
    "15000以上": (15000, float("inf")),
}

MAP_INCOME = {
    "3000以下": 2000,
    "3000-8000": 5500,
    "8000-15000": 11500,
    "15000以上": 20000,
}


def id_card_to_birthday(id_card: str) -> str:
    s = str(id_card).strip()
    if len(s) < 14:
        return "1990-01-01"
    return f"{s[6:10]}-{s[10:12]}-{s[12:14]}"


def map_education_tier(education: str) -> str:
    if education in ("博士", "硕士"):
        return "high"
    if education == "本科":
        return "mid"
    return "low"


def job_stable(job_type: str) -> float:
    if job_type in ("公务员", "企事业单位"):
        return 1.0
    return 0.0


def verify_income(monthly_income: str, amt_income_total: float) -> dict:
    backend = amt_income_total / 12.0 if amt_income_total else 0.0
    lo, hi = INCOME_RANGES.get(monthly_income, (0, float("inf")))
    if backend <= 0:
        return {"pass": True, "reject": False, "manual": False, "reason": "无后台收入"}
    if lo <= backend <= hi:
        return {"pass": True, "reject": False, "manual": False, "reason": "收入在区间内"}
    if backend >= lo:
        return {"pass": True, "reject": False, "manual": False, "reason": "后台高于区间下沿"}
    ratio = (lo - backend) / backend
    if ratio > 0.5:
        return {
            "pass": False,
            "reject": True,
            "manual": False,
            "reason": f"收入虚报>{ratio*100:.0f}%",
        }
    if ratio > 0.15:
        return {
            "pass": False,
            "reject": False,
            "manual": True,
            "reason": f"收入偏差{ratio*100:.0f}%",
        }
    return {"pass": True, "reject": False, "manual": False, "reason": "偏差15%内"}


def build_raw_features(row: pd.Series, app: dict) -> dict:
    birthday = app.get("birthday") or id_card_to_birthday(row["id_card"])
    birth_year = int(birthday[:4])
    age = datetime.now().year - birth_year
    tier = map_education_tier(app.get("education", "高中及以下"))
    gender = app.get("gender", int(row.get("gender_male", 0)))

    return {
        "gender_male": float(gender),
        "edu_mid": 1.0 if tier == "mid" else 0.0,
        "age_years": float(age),
        "phone_change_days": float(row.get("phone_change_days", 0)),
        "prev_refused_count": float(row.get("prev_refused_count", 0)),
        "ext_source_2": float(row["ext_source_2"]),
        "ext_source_3": float(row["ext_source_3"]),
    }


def score_woe(raw: dict, rules: dict) -> tuple[float, str]:
    features = rules["features"]
    coefs = rules["coefficients"]
    intercept = float(rules["intercept"])
    z = intercept
    for key, w in coefs.items():
        woe = transform_row_woe(raw.get(key), features[key])
        z += float(w) * woe
    prob = 1.0 / (1.0 + math.exp(-z))
    score = calculate_scorecard_score(prob, rules.get("scorecard"))
    th = rules["thresholds"]
    auto_ap = float(th["auto_approve"])
    man_rev = float(th["manual_review"])
    if score >= auto_ap:
        decision = "APPROVE"
    elif score >= man_rev:
        decision = "MANUAL_REVIEW"
    else:
        decision = "REJECT"
    return round(score, 1), decision


DEMO_PERSONAS = [
    {
        "label": "A2方小巢",
        "sk_id_curr": 200141,
        "app": {
            "education": "大专",
            "gender": 0,
            "monthlyIncome": "8000-15000",
            "marriage": "未婚",
            "jobType": "自由职业",
        },
        "blacklist": "L2",
    },
    {
        "label": "A3张小梁",
        "sk_id_curr": 218467,
        "app": {
            "education": "高中及以下",
            "gender": 0,
            "monthlyIncome": "8000-15000",
            "marriage": "已婚",
            "jobType": "企事业单位",
        },
        "blacklist": "NAME_ONLY",
    },
    {
        "label": "李四",
        "sk_id_curr": 120728,
        "app": {
            "education": "高中及以下",
            "gender": 1,
            "monthlyIncome": "3000以下",
            "marriage": "未婚",
            "jobType": "体力劳动者",
        },
    },
    {
        "label": "王五",
        "sk_id_curr": 140552,
        "app": {
            "education": "高中及以下",
            "gender": 1,
            "monthlyIncome": "8000-15000",
            "marriage": "未婚",
            "jobType": "体力劳动者",
        },
    },
    {
        "label": "赵六",
        "sk_id_curr": 201057,
        "app": {
            "education": "本科",
            "gender": 0,
            "monthlyIncome": "15000以上",
            "marriage": "已婚",
            "jobType": "企事业单位",
        },
    },
    {
        "label": "孙七",  # L2 黑名单（孙*,110112）+ 模型 APPROVE ~798 + 规则强制补材料
        "sk_id_curr": 125887,
        "blacklist": "L2",
        "app": {
            "education": "本科",
            "gender": 0,
            "monthlyIncome": "15000以上",
            "marriage": "已婚",
            "jobType": "企事业单位",
        },
    },
    {
        "label": "周八",
        "sk_id_curr": 220352,
        "app": {
            "education": "高中及以下",
            "gender": 0,
            "monthlyIncome": "15000以上",
            "marriage": "已婚",
            "jobType": "体力劳动者",
        },
    },
    {
        "label": "吴九",
        "sk_id_curr": 105579,
        "app": {
            "education": "高中及以下",
            "gender": 0,
            "monthlyIncome": "15000以上",
            "marriage": "已婚",
            "jobType": "体力劳动者",
        },
    },
    {
        "label": "郑十",
        "sk_id_curr": 104716,
        "app": {
            "education": "高中及以下",
            "gender": 1,
            "monthlyIncome": "15000以上",
            "marriage": "已婚",
            "jobType": "企事业单位",
        },
    },
]


def main():
    df = pd.read_csv(CSV_PATH, encoding="utf-8-sig")
    with open(RULES_PATH, encoding="utf-8") as f:
        rules = json.load(f)

    print(f"模型: {rules.get('version')}  阈值: {rules['thresholds']['auto_approve']}/{rules['thresholds']['manual_review']}\n")
    print(f"{'角色':<10} {'id_card':<22} {'birthday':<12} {'分数':>6} {'决策':<14} {'收入验真'}")
    print("-" * 90)

    results = []
    for p in DEMO_PERSONAS:
        row = df.loc[df["sk_id_curr"] == p["sk_id_curr"]].iloc[0]
        id_card = str(row["id_card"])
        birthday = id_card_to_birthday(id_card)
        app = {**p["app"], "birthday": birthday}
        raw = build_raw_features(row, app)
        score, decision = score_woe(raw, rules)
        income = verify_income(app["monthlyIncome"], float(row["AMT_INCOME_TOTAL"]))
        final_status = decision
        if income["reject"]:
            final_status = "INCOME_REJECT"
        elif p.get("blacklist") == "L2" or income.get("manual"):
            final_status = "FORCE_MANUAL"
        income_note = income["reason"]
        print(
            f"{p['label']:<10} {id_card:<22} {birthday:<12} {score:>6.1f} {decision:<14} {income_note}"
        )
        results.append(
            {
                **p,
                "id_card": id_card,
                "birthday": birthday,
                "score": score,
                "decision": decision,
                "income": income,
                "ext_source_2": float(row["ext_source_2"]),
                "ext_source_3": float(row["ext_source_3"]),
                "amt_income_total": float(row["AMT_INCOME_TOTAL"]),
                "has_default_history": int(row["has_default_history"]),
            }
        )

    with open("output/demo_user_scores.json", "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print("\n已写入 output/demo_user_scores.json")


if __name__ == "__main__":
    main()
