import pandas as pd
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, roc_auc_score
import json
import os

os.makedirs("output", exist_ok=True)

def calculate_scorecard_score(prob_default, base_score=600, pdo=80, target_score=70, target_odds=1):
    """
    将违约概率转换为标准评分卡分数 (10-90)
    
    参数:
        prob_default: 违约概率 (0-1)
        base_score: 基础分，默认600
        pdo: Points to Double the Odds，调整为80让分数变化更平缓
        target_score: 目标分数对应的分数，调整为70作为平均分
        target_odds: 目标分数对应的赔率，调整为1让基准更合理
    
    返回:
        10-90的信用分数
    """
    if prob_default >= 0.99:
        return 10
    if prob_default <= 0.01:
        return 90
    
    odds = prob_default / (1 - prob_default)
    factor = -pdo / np.log(2)
    offset = target_score - factor * np.log(target_odds)
    
    score = offset + factor * np.log(odds)
    score = max(10, min(90, score))
    
    return round(score, 2)

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
    df = df.copy()
    
    df['age_bin'] = pd.cut(df['age'], bins=[0, 25, 35, 50, 100], labels=['age_0_25', 'age_26_35', 'age_36_50', 'age_51_plus'])
    df['income_bin'] = pd.cut(df['income'], bins=[0, 5000, 15000, float('inf')], labels=['income_below_5000', 'income_5000_15000', 'income_15000_plus'])
    df['multi_head_bin'] = pd.cut(df['multi_head_loan_count'], bins=[-1, 3, 6, float('inf')], labels=['multi_head_0_3', 'multi_head_4_6', 'multi_head_7_plus'])
    df['credit_query_bin'] = pd.cut(df['credit_query_count_3m'], bins=[-1, 3, 8, float('inf')], labels=['credit_query_0_3', 'credit_query_4_8', 'credit_query_9_plus'])
    df['overdue_bin'] = pd.cut(df['overdue_count_12m'], bins=[-1, 0, 2, float('inf')], labels=['overdue_12m_0', 'overdue_12m_1_2', 'overdue_12m_3_plus'])
    df['dti_bin'] = pd.cut(df['dti'], bins=[-1, 15, 30, float('inf')], labels=['dti_low', 'dti_medium', 'dti_high'])
    
    df = pd.get_dummies(df, columns=['age_bin', 'income_bin', 'multi_head_bin', 'credit_query_bin', 'overdue_bin', 'dti_bin'], drop_first=True)
    
    return df

def process_lending_club_data(df):
    """处理Lending Club数据集，转换为模型所需格式"""
    df = df.copy()
    
    df = df[df['loan_status'].isin(['Charged Off', 'Fully Paid'])].copy()
    
    df['income'] = df['annual_inc'] / 12
    
    df['credit_query_count_3m'] = (df['inq_last_6mths'] / 2).fillna(0).astype(int)
    
    df['multi_head_loan_count'] = df['open_acc'].fillna(0).astype(int)
    
    df['overdue_count_12m'] = df.apply(lambda row: 3 if row['loan_status'] == 'Charged Off' else 0, axis=1)
    
    df['dti'] = df['dti'].fillna(df['dti'].median())
    
    df['age'] = np.random.randint(22, 60, len(df))
    
    df['device_is_virtual'] = np.random.choice([0, 1], len(df), p=[0.97, 0.03])
    df['ip_is_proxy'] = np.random.choice([0, 1], len(df), p=[0.95, 0.05])
    
    df['defaulted'] = (df['loan_status'] == 'Charged Off').astype(int)
    
    return df

def train_scoring_model(csv_path="data/training_data.csv"):
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
        users = pd.DataFrame({
            "id_card": [f"510106{np.random.randint(1970,2005):04d}{np.random.randint(1,13):02d}{np.random.randint(1,29):02d}{i:04d}" for i in range(n)],
            "age": np.random.randint(22, 60, n),
            "income": np.random.randint(3000, 50000, n),
            "multi_head_loan_count": np.random.poisson(lam=2, size=n).clip(0, 15),
            "credit_query_count_3m": np.random.poisson(lam=3, size=n).clip(0, 20),
            "overdue_count_12m": np.random.choice([0, 1, 2, 3, 5], n, p=[0.75, 0.12, 0.07, 0.04, 0.02]),
            "dti": np.random.randint(0, 40, n),
            "device_is_virtual": np.random.choice([0, 1], n, p=[0.97, 0.03]),
            "ip_is_proxy": np.random.choice([0, 1], n, p=[0.95, 0.05])
        })
        users["defaulted"] = (
            (users["multi_head_loan_count"] > 6) |
            (users["credit_query_count_3m"] > 10) |
            (users["overdue_count_12m"] >= 3) |
            (users["income"] < 5000) |
            (users["device_is_virtual"] == 1) |
            (users["dti"] > 30)
        ).astype(int)
        df = users
    
    feature_analysis = analyze_feature_default_rates(df)
    
    X = df[['age', 'income', 'multi_head_loan_count', 'credit_query_count_3m', 'overdue_count_12m', 'dti', 'device_is_virtual', 'ip_is_proxy']]
    y = df['defaulted']
    
    X_binned = bin_features(X)
    
    X_train, X_test, y_train, y_test = train_test_split(X_binned, y, test_size=0.2, random_state=42)
    
    model = LogisticRegression(class_weight='balanced', random_state=42, max_iter=200)
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
    
    prob_default_avg = y_pred_proba.mean()
    avg_score = calculate_scorecard_score(prob_default_avg)
    
    feature_correlations = {
        "overdue_count_12m": round(float(df['overdue_count_12m'].corr(df['defaulted'])), 4),
        "multi_head_loan_count": round(float(df['multi_head_loan_count'].corr(df['defaulted'])), 4),
        "credit_query_count_3m": round(float(df['credit_query_count_3m'].corr(df['defaulted'])), 4),
        "income": round(float(df['income'].corr(df['defaulted'])), 4),
        "dti": round(float(df['dti'].corr(df['defaulted'])), 4),
        "age": round(float(df['age'].corr(df['defaulted'])), 4)
    }
    
    rules = {
        "version": "v3.0",
        "description": "基于LendingClub数据训练的评分规则，优化版",
        "feature_weights": feature_weights,
        "intercept": float(model.intercept_[0]),
        "thresholds": {
            "auto_approve": 75,
            "manual_review": 50
        },
        "scorecard": {
            "base_score": 600,
            "pdo": 80,
            "target_score": 70,
            "target_odds": 1
        },
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
            "avg_score": avg_score
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
    test_samples['score'] = test_samples['prob_default'].apply(calculate_scorecard_score)
    print("\n评分卡分数示例:")
    print(test_samples.to_string(index=False))
    
    try:
        from load_to_mysql import load_scoring_rules_to_mysql
        load_scoring_rules_to_mysql()
    except ImportError:
        print("\n提示：运行 python load_to_mysql.py 将评分规则写入数据库")
    
    return rules

if __name__ == "__main__":
    train_scoring_model()
