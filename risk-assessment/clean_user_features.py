import pandas as pd
import os
import random
from datetime import datetime

os.makedirs("data/cleaned", exist_ok=True)

PARQUET_PATH = "data/raw/home_credit_train_min.parquet"


def load_hc_raw_frame():
    """从 Home Credit parquet 宽表读取。"""
    if not os.path.isfile(PARQUET_PATH):
        raise FileNotFoundError(f"未找到训练数据: {PARQUET_PATH}")
    df = pd.read_parquet(PARQUET_PATH)
    print(f"从 parquet 读取: {PARQUET_PATH} ({len(df)} 行)")
    return df


def generate_id_card(area_code, birth_year, index):
    """根据地区编码和出生年份生成身份证号（演示用）。"""
    year = str(birth_year)
    month = f"{random.randint(1, 12):02d}"
    day = f"{random.randint(1, 28):02d}"
    seq = f"{index % 10000:04d}"
    return f"{area_code}{year}{month}{day}{seq}"


def clean_user_features():
    df = load_hc_raw_frame()
    print(f"原始数据行数: {len(df)}")

    initial_count = len(df)
    df = df.drop_duplicates(subset="SK_ID_CURR", keep="first")
    print(f"去重后行数: {len(df)} (删除 {initial_count - len(df)} 条重复)")

    cleaned_df = pd.DataFrame()
    cleaned_df["sk_id_curr"] = pd.to_numeric(df["SK_ID_CURR"], errors="coerce").astype("Int64")

    current_year = datetime.now().year
    cleaned_df["days_birth"] = pd.to_numeric(df["DAYS_BIRTH"], errors="coerce").fillna(0).astype(int)
    cleaned_df["birth_year"] = current_year - (abs(cleaned_df["days_birth"]) / 365).astype(int)
    cleaned_df["age"] = (abs(cleaned_df["days_birth"]) / 365).round().astype(int)

    cleaned_df["days_employed"] = pd.to_numeric(df["DAYS_EMPLOYED"], errors="coerce").fillna(0).astype(int)
    employment_days = abs(cleaned_df["days_employed"])
    cleaned_df["employment_years"] = (employment_days / 365).round(1)

    cleaned_df["AMT_INCOME_TOTAL"] = df["AMT_INCOME_TOTAL"].fillna(0)

    cleaned_df["credit_query_week"] = df["AMT_REQ_CREDIT_BUREAU_WEEK"].fillna(0).astype(int)
    cleaned_df["credit_query_month"] = df["AMT_REQ_CREDIT_BUREAU_MON"].fillna(0).astype(int)

    cleaned_df["phone_change_days"] = abs(df["DAYS_LAST_PHONE_CHANGE"].fillna(0)).astype(int)

    if "active_loans_count" in df.columns:
        cleaned_df["active_loans_count"] = df["active_loans_count"].fillna(0).astype(int)
    else:
        cleaned_df["active_loans_count"] = 0

    cleaned_df["ext_source_2"] = df["EXT_SOURCE_2"].fillna(0).round(4)
    cleaned_df["ext_source_3"] = df["EXT_SOURCE_3"].fillna(0).round(4)

    cleaned_df["has_car"] = df["FLAG_OWN_CAR"].fillna("N").apply(lambda x: 1 if str(x).upper() == "Y" else 0)

    cleaned_df["occupation_type"] = df["OCCUPATION_TYPE"].fillna("未知")

    edu_mapping = {
        "Academic degree": "博士",
        "Higher education": "本科",
        "Incomplete higher": "大专",
        "Secondary / secondary special": "高中及以下",
        "Lower secondary": "高中及以下",
    }
    cleaned_df["education"] = df["NAME_EDUCATION_TYPE"].fillna("未知").map(edu_mapping).fillna("高中及以下")

    cleaned_df["has_default_history"] = df["TARGET"].fillna(0).astype(int)

    if "prev_refused" in df.columns:
        cleaned_df["prev_refused_count"] = df["prev_refused"].fillna(0).astype(int)
    elif "prev_refused_count" in df.columns:
        cleaned_df["prev_refused_count"] = df["prev_refused_count"].fillna(0).astype(int)
    else:
        cleaned_df["prev_refused_count"] = 0

    cleaned_df["credit_query_week"] = cleaned_df["credit_query_week"].clip(0, 50)
    cleaned_df["credit_query_month"] = cleaned_df["credit_query_month"].clip(0, 100)
    cleaned_df["active_loans_count"] = cleaned_df["active_loans_count"].clip(0, 50)
    cleaned_df["prev_refused_count"] = cleaned_df["prev_refused_count"].clip(0, 20)

    selected_area_codes = ["110112", "310000"]
    samples_per_area = 250
    rng = random.Random(42)

    dfs_by_area = []
    pool = cleaned_df.dropna(subset=["sk_id_curr"]).copy()
    if len(pool) < samples_per_area:
        raise ValueError(f"可用样本不足 {samples_per_area} 条，当前 {len(pool)} 条")

    for area_code in selected_area_codes:
        indices = rng.sample(range(len(pool)), min(samples_per_area, len(pool)))
        sample_df = pool.iloc[indices].copy()
        sample_df["area_code"] = area_code
        dfs_by_area.append(sample_df)
        pool = pool.drop(pool.index[indices])

    final_df = pd.concat(dfs_by_area, ignore_index=True)

    final_df["id_card"] = final_df.apply(
        lambda row: generate_id_card(row["area_code"], row["birth_year"], final_df.index.get_loc(row.name)),
        axis=1,
    )

    final_df = final_df[
        [
            "sk_id_curr",
            "id_card",
            "area_code",
            "birth_year",
            "age",
            "days_birth",
            "days_employed",
            "employment_years",
            "AMT_INCOME_TOTAL",
            "credit_query_week",
            "credit_query_month",
            "phone_change_days",
            "active_loans_count",
            "ext_source_2",
            "ext_source_3",
            "has_car",
            "occupation_type",
            "education",
            "has_default_history",
            "prev_refused_count",
        ]
    ]

    output_path = "data/cleaned/cleaned_user_features.csv"
    final_df.to_csv(output_path, index=False, encoding="utf-8-sig")
    print(f"清洗后用户特征数据: {len(final_df)} 条")
    print(f"数据已保存到: {output_path}")

    print("\n=== 统计信息 ===")
    print(f"年龄分布: {final_df['age'].min()} - {final_df['age'].max()} 岁")
    print(f"平均年龄: {final_df['age'].mean():.1f} 岁")
    print(f"历史违约率: {(final_df['has_default_history'].mean() * 100):.1f}%")
    print(f"有车比例: {(final_df['has_car'].mean() * 100):.1f}%")
    print(f"\n地区分布:")
    print(final_df["area_code"].value_counts())
    print(f"\nsk_id_curr 示例（联调时 id_card 可填同值）: {final_df['sk_id_curr'].head(3).tolist()}")

    return final_df


if __name__ == "__main__":
    clean_user_features()
