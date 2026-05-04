import pandas as pd
import re
import os

os.makedirs("data/cleaned", exist_ok=True)

def clean_blacklist():
    df = pd.read_csv("data/raw/raw_blacklist.csv", encoding="utf-8-sig")
    
    print(f"原始数据行数: {len(df)}")
    
    df = df.drop(columns=["extra_field", "invalid_field"], errors="ignore")
    
    initial_count = len(df)
    df = df.drop_duplicates(subset="id_card", keep="first")
    print(f"去重后行数: {len(df)} (删除 {initial_count - len(df)} 条重复)")
    
    def is_valid_id_card(id_card):
        if pd.isna(id_card):
            return False
        id_card = str(id_card).strip()
        if len(id_card) != 18:
            return False
        if not re.match(r'^\d{17}[\dXx]$', id_card):
            return False
        return True
    
    invalid_ids = df[~df["id_card"].apply(is_valid_id_card)]
    print(f"身份证格式错误: {len(invalid_ids)} 条")
    df = df[df["id_card"].apply(is_valid_id_card)]
    
    df["name"] = df["name"].fillna("未知")
    df["phone"] = df["phone"].fillna("未知")
    
    valid_sources = ["EXTERNAL_COURT", "EXTERNAL_FRAUD", "INTERNAL_OVERDUE"]
    df["source"] = df["source"].apply(lambda x: x if x in valid_sources else "UNKNOWN")
    
    df["created_at"] = pd.Timestamp.now()
    df["expire_at"] = None
    
    df = df[["id_card", "phone", "reason", "source", "created_at", "expire_at"]]
    
    df.to_csv("data/cleaned/cleaned_blacklist.csv", index=False, encoding="utf-8-sig")
    print(f"清洗后黑名单数据: {len(df)} 条")
    
    return df

if __name__ == "__main__":
    clean_blacklist()