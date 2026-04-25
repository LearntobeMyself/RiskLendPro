import datetime

def calculate_age(birthday_str):
    """根据出生日期计算年龄"""
    try:
        birthday = datetime.datetime.strptime(birthday_str, "%Y-%m-%d")
        today = datetime.datetime.now()
        age = today.year - birthday.year - ((today.month, today.day) < (birthday.month, birthday.day))
        return age
    except:
        return 0

def get_income_value(income_level):
    """根据收入等级获取映射值"""
    income_mapping = {
        "3000以下": 2000,
        "3000-8000": 5500,
        "8000-15000": 11500,
        "15000以上": 20000
    }
    return income_mapping.get(income_level, 2000)
