import pandas as pd
import re
import os

os.makedirs("data/cleaned", exist_ok=True)

COURT_TO_AREA_CODE = {
    "北京市通州区人民法院": "110112",
    "北京市朝阳区人民法院": "110105",
    "北京市东城区人民法院": "110101",
    "北京市西城区人民法院": "110102",
    "北京市海淀区人民法院": "110108",
    "北京市丰台区人民法院": "110106",
    "天津市滨海新区人民法院": "120116",
    "天津市南开区人民法院": "120104",
    "天津自由贸易试验区人民法院": "120116",
    "天津市和平区人民法院": "120101",
    "天津市河西区人民法院": "120103",
}

def get_area_code(court_name):
    if pd.isna(court_name):
        return "UNKNOWN"
    court_name = str(court_name).strip()
    for key, value in COURT_TO_AREA_CODE.items():
        if key in court_name or court_name in key:
            return value
    if "北京" in court_name:
        return "110000"
    elif "天津" in court_name:
        return "120000"
    elif "上海" in court_name:
        return "310000"
    elif "重庆" in court_name:
        return "500000"
    return "UNKNOWN"

def extract_birth_year(birth_date):
    if pd.isna(birth_date):
        return None
    birth_date = str(birth_date).strip()
    match = re.match(r'^(\d{4})', birth_date)
    if match:
        return int(match.group(1))
    return None

def clean_blacklist():
    encodings = ['utf-8-sig', 'gbk', 'gb2312', 'gb18030']
    df = None
    for encoding in encodings:
        try:
            df = pd.read_csv("data/raw/blacklist.csv", encoding=encoding)
            print(f"成功使用编码 {encoding} 读取文件")
            break
        except Exception as e:
            print(f"尝试编码 {encoding} 失败: {e}")
    
    if df is None:
        raise ValueError("无法读取文件，请检查文件编码格式")
    
    print(f"原始数据行数: {len(df)}")
    
    cleaned_df = pd.DataFrame()
    
    cleaned_df["name"] = df["被执行人姓名/名称"].fillna("未知")
    
    cleaned_df["area_code"] = df["执行法院"].apply(get_area_code)
    
    cleaned_df["birth_year"] = df["出生日期"].apply(extract_birth_year)
    
    cleaned_df["case_no"] = df["案号"].fillna("")
    
    cleaned_df["court_name"] = df["执行法院"].fillna("未知")
    
    cleaned_df["duty_status"] = df["被执行人的履行情况"].fillna("未知")
    
    cleaned_df["behavior_details"] = df["失信被执行人行为情况"].fillna("未知")
    
    cleaned_df["created_at"] = pd.Timestamp.now().strftime("%Y-%m-%d %H:%M:%S")
    cleaned_df["expire_at"] = None
    
    initial_count = len(cleaned_df)
    cleaned_df = cleaned_df[cleaned_df["name"] != "未知"]
    print(f"删除无姓名记录: {initial_count - len(cleaned_df)} 条")
    
    initial_count = len(cleaned_df)
    cleaned_df = cleaned_df.drop_duplicates(subset=["name", "area_code", "birth_year"], keep="first")
    print(f"去重后行数: {len(cleaned_df)} (删除 {initial_count - len(cleaned_df)} 条重复)")
    
    def get_risk_level(row):
        if row["duty_status"] == "全部未履行":
            return "HIGH"
        elif row["duty_status"] == "部分未履行":
            return "MEDIUM"
        else:
            return "LOW"
    
    cleaned_df["risk_level"] = cleaned_df.apply(get_risk_level, axis=1)
    
    output_path = "data/cleaned/cleaned_blacklist.csv"
    cleaned_df.to_csv(output_path, index=False, encoding="utf-8-sig")
    print(f"清洗后黑名单数据: {len(cleaned_df)} 条")
    print(f"数据已保存到: {output_path}")
    
    print("\n=== 统计信息 ===")
    print(f"风险等级分布:")
    print(cleaned_df["risk_level"].value_counts())
    print(f"\n地区分布:")
    print(cleaned_df["area_code"].value_counts().head(10))
    print(f"\n履行情况分布:")
    print(cleaned_df["duty_status"].value_counts())
    
    return cleaned_df

if __name__ == "__main__":
    clean_blacklist()