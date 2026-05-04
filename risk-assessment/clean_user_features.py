import pandas as pd
import re
import os

os.makedirs("data/cleaned", exist_ok=True)

def clean_user_features():
    df = pd.read_csv("data/raw/raw_user_features.csv", encoding="utf-8-sig")
    
    print(f"原始数据行数: {len(df)}")
    
    df = df.drop(columns=["invalid_field", "risk_level", "latest_loan_time"], errors="ignore")
    
    initial_count = len(df)
    df = df.drop_duplicates(subset="id_card", keep="first")
    print(f"去重后行数: {len(df)} (删除 {initial_count - len(df)} 条重复)")
    
    def is_valid_id_card(id_card):
        if pd.isna(id_card):
            return False
        id_card = str(id_card).strip()
        if len(id_card) != 18:
            return False
        return bool(re.match(r'^\d{17}[\dXx]$', id_card))
    
    df = df[df["id_card"].apply(is_valid_id_card)]
    print(f"有效身份证数据: {len(df)} 条")
    
    df["credit_score"] = df["credit_score"].fillna(0)
    df["overdue_count_12m"] = df["overdue_count_12m"].fillna(0).astype(int)
    df["credit_query_count_3m"] = df["credit_query_count_3m"].fillna(0).astype(int)
    df["multi_head_loan_count"] = df["multi_head_loan_count"].fillna(0).astype(int)
    df["multi_head_loan_total_amount"] = df["multi_head_loan_total_amount"].fillna(0)
    df["device_is_virtual"] = df["device_is_virtual"].fillna(0).astype(int)
    df["ip_is_proxy"] = df["ip_is_proxy"].fillna(0).astype(int)
    df["device_change_count_30d"] = df["device_change_count_30d"].fillna(0).astype(int)
    
    df["credit_score"] = df["credit_score"].clip(300, 900)
    df["overdue_count_12m"] = df["overdue_count_12m"].clip(0, 100)
    df["credit_query_count_3m"] = df["credit_query_count_3m"].clip(0, 100)
    df["multi_head_loan_count"] = df["multi_head_loan_count"].clip(0, 50)
    
    df["data_source"] = "EXTERNAL_API"
    df["updated_at"] = pd.Timestamp.now()
    
    df = df[[
        "id_card", "credit_score", "overdue_count_12m", 
        "credit_query_count_3m", "multi_head_loan_count",
        "multi_head_loan_total_amount", "device_is_virtual",
        "device_change_count_30d", "ip_is_proxy",
        "data_source", "updated_at"
    ]]
    
    df.to_csv("data/cleaned/cleaned_user_features.csv", index=False, encoding="utf-8-sig")
    print(f"清洗后用户特征数据: {len(df)} 条")
    
    return df

if __name__ == "__main__":
    clean_user_features()