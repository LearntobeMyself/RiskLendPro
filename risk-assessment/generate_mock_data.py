import pandas as pd
import numpy as np
import os

np.random.seed(42)

os.makedirs("data/raw", exist_ok=True)
os.makedirs("data/cleaned", exist_ok=True)
os.makedirs("output", exist_ok=True)

def mock_call_court_api():
    blacklist = []
    for i in range(50):
        blacklist.append({
            "id_card": f"1101011980{np.random.randint(1,13):02d}{np.random.randint(1,29):02d}{i:04d}",
            "name": f"失信人{i}" if np.random.random() > 0.1 else "",
            "phone": f"138{np.random.randint(1000,10000):04d}{np.random.randint(1000,10000):04d}",
            "reason": "法院失信被执行人，已被限制高消费",
            "source": "EXTERNAL_COURT",
            "extra_field": "junk_data"
        })
    for i in range(10):
        blacklist.append(blacklist[i])
    return blacklist

def mock_call_fraud_api():
    blacklist = []
    for i in range(30):
        blacklist.append({
            "id_card": f"3101011985{np.random.randint(1,13):02d}{np.random.randint(1,29):02d}{i:04d}",
            "name": f"欺诈用户{i}",
            "phone": f"139{np.random.randint(1000,10000):04d}{np.random.randint(1000,10000):04d}" if np.random.random() > 0.05 else None,
            "reason": "涉嫌欺诈，提供虚假身份信息",
            "source": "EXTERNAL_FRAUD"
        })
    return blacklist

def mock_credit_api(id_card):
    seed = hash(id_card) % 1000
    return {
        "id_card": id_card,
        "credit_score": np.random.randint(300, 900),
        "overdue_count": min(seed % 5, 3),
        "query_count": min(seed % 10, 8),
        "update_time": f"2024-{np.random.randint(1,13):02d}-{np.random.randint(1,29):02d}"
    }

def mock_multi_head_api(id_card):
    seed = hash(id_card) % 1000
    return {
        "id_card": id_card,
        "platform_count": min(seed % 8, 10),
        "total_amount": np.random.randint(0, 500000),
        "latest_loan_time": f"2024-{np.random.randint(1,13):02d}-{np.random.randint(1,29):02d}",
        "risk_level": np.random.choice(["LOW", "MEDIUM", "HIGH"], p=[0.6, 0.3, 0.1])
    }

def mock_device_api(id_card):
    seed = hash(id_card) % 100
    return {
        "id_card": id_card,
        "is_virtual": 1 if seed < 3 else 0,
        "is_proxy": 1 if seed < 5 else 0,
        "device_count": np.random.randint(1, 5),
        "location_change_count": np.random.randint(0, 10)
    }

court_blacklist = mock_call_court_api()
fraud_blacklist = mock_call_fraud_api()

n = 1000
users = pd.DataFrame({
    "id_card": [f"510106{np.random.randint(1970,2005):04d}{np.random.randint(1,13):02d}{np.random.randint(1,29):02d}{i:04d}" for i in range(n)],
    "name": [f"模拟用户{i}" for i in range(n)],
    "defaulted": np.random.choice([0, 1], n, p=[0.8, 0.2])
})

defaulted_users = users[users["defaulted"] == 1].sample(20)
for _, user in defaulted_users.iterrows():
    court_blacklist.append({
        "id_card": user["id_card"],
        "name": user["name"],
        "phone": f"137{np.random.randint(1000,10000):04d}{np.random.randint(1000,10000):04d}",
        "reason": "内部逾期，违约风险高",
        "source": "INTERNAL_OVERDUE"
    })

raw_blacklist_df = pd.DataFrame(court_blacklist + fraud_blacklist)
raw_blacklist_df.to_csv("data/raw/raw_blacklist.csv", index=False, encoding="utf-8-sig")
print(f"原始黑名单数据生成完毕，共 {len(raw_blacklist_df)} 条（含重复）")

user_features = []
for _, user in users.iterrows():
    credit_data = mock_credit_api(user["id_card"])
    multi_head_data = mock_multi_head_api(user["id_card"])
    device_data = mock_device_api(user["id_card"])
    
    user_features.append({
        "id_card": user["id_card"],
        "name": user["name"],
        "credit_score": credit_data["credit_score"],
        "overdue_count_12m": credit_data["overdue_count"],
        "credit_query_count_3m": credit_data["query_count"],
        "multi_head_loan_count": multi_head_data["platform_count"],
        "multi_head_loan_total_amount": multi_head_data["total_amount"],
        "device_is_virtual": device_data["is_virtual"],
        "ip_is_proxy": device_data["is_proxy"],
        "device_change_count_30d": device_data["device_count"],
        "invalid_field": np.random.choice([None, "garbage"], p=[0.9, 0.1])
    })

raw_user_features_df = pd.DataFrame(user_features)
raw_user_features_df.to_csv("data/raw/raw_user_features.csv", index=False, encoding="utf-8-sig")
print(f"原始用户特征数据生成完毕，共 {len(raw_user_features_df)} 条")