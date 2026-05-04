import datetime
import math

def calculate_age(birthday_str: str) -> int:
    """根据出生日期计算年龄"""
    try:
        birthday = datetime.datetime.strptime(birthday_str, "%Y-%m-%d")
        today = datetime.datetime.now()
        age = today.year - birthday.year - ((today.month, today.day) < (birthday.month, birthday.day))
        return max(0, age)
    except:
        return 0

def get_income_value(income_level: str) -> int:
    """根据收入等级获取映射值"""
    income_mapping = {
        "3000以下": 3000,
        "3000-8000": 5500,
        "8000-15000": 11500,
        "15000以上": 20000
    }
    return income_mapping.get(income_level, 3000)

def bin_age(age):
    """年龄分箱"""
    if age < 20:
        return "青年(20以下)"
    elif age < 30:
        return "青年(20-29)"
    elif age < 40:
        return "中年(30-39)"
    elif age < 50:
        return "中年(40-49)"
    else:
        return "中老年(50以上)"

def bin_income(income_str):
    """收入分箱"""
    income_mapping = {
        "3000以下": "低",
        "3000-8000": "中低",
        "8000-15000": "中高",
        "15000以上": "高"
    }
    return income_mapping.get(income_str, "未知")

def calculate_woe(positive_ratio, negative_ratio):
    """计算WoE值（Weight of Evidence）"""
    if positive_ratio == 0 or negative_ratio == 0:
        return 0
    return math.log(positive_ratio / negative_ratio)

def calculate_iv(woe, positive_ratio, negative_ratio):
    """计算IV值（Information Value）"""
    return (positive_ratio - negative_ratio) * woe