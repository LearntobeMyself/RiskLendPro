# Python 风控评分系统完整设计

## 一、项目概述

### 1.1 设计目标

本设计为互联网个人贷款风控系统提供完整的**离线数据处理、模型训练和评分服务解决方案**。系统采用**离线与在线分离架构**：Python负责离线数据处理和模型训练，Java负责在线实时决策。

| 设计原则      | 说明                     |
| --------- | ---------------------- |
| 准确性       | 准确评估用户信用风险，区分高风险和低风险客户 |
| 稳定性       | 模型在不同时期和不同人群中表现稳定      |
| 可解释性      | 评分结果可解释，便于业务理解和审批决策    |
| 可扩展性      | 支持模型的迭代和优化             |
| 数据与规则分离   | 用户数据存表，评分权重存JSON配置     |
| 硬规则与软评分分离 | 一票否决项走规则引擎，风险概率走模型打分   |

### 1.2 核心架构原则

```
Python 的职责：离线数据处理 + 模型训练
    ═══════════ 接口：数据库表 + JSON 规则文件 ═══════════
Java 的职责：  在线实时决策 + 业务逻辑
```

### 1.3 应用场景

- **授信审批**：评估用户信用风险，确定授信额度
- **风险监控**：定期监控用户信用状况变化
- **营销决策**：基于信用评分进行精准营销

***

## 二、系统架构

### 2.1 架构全景图

```
┌─────────────────────────────────────────────────────────────────┐
│                     Python 离线层（定时任务）                       │
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │ 黑名单同步任务     │  │ 外部特征同步任务   │  │ 模型训练任务  │  │
│  │ (每日 3:00)       │  │ (每日 2:00)       │  │ (每月/按需)   │  │
│  │                  │  │                  │  │              │  │
│  │ 从外部 API 拉取   │  │ 从外部 API 拉取   │  │ 加载历史数据  │  │
│  │ 清洗去重格式化    │  │ 多头借贷/征信查询  │  │ 训练评分模型  │  │
│  │ 同步内部违约用户  │  │ 清洗格式化        │  │ 导出权重JSON  │  │
│  └────────┬─────────┘  └────────┬─────────┘  └──────┬───────┘  │
│           │                     │                    │          │
└───────────┼─────────────────────┼────────────────────┼──────────┘
            │ 写入                 │ 写入               │ 更新
            ↓                     ↓                    ↓
┌─────────────────────────────────────────────────┐
│                       本地数据存储层                     │
│                                                         │
│  ┌──────────────┐  ┌──────────────────┐  ┌────────────┐  │
│  │ blacklist    │  │ user_external    │  │scoring_rules│ │
│  │ (黑名单表)   │  │ _features        │  │ (评分规则表) │  │
│  └──────────────┘  └──────────────────┘  └────────────┘  │
└──────────────────────┬──────────────────────────────────┘
                       │ 读取
                       ↓
┌─────────────────────────────────────────────────┐
│                     Java 在线决策引擎                    │
│                                                         │
│  贷款申请进入 → 黑名单初筛 → 特征组装 → 模型打分 → 策略决策   │
└─────────────────────────────────────────────────┘
```

### 2.2 目录结构

```
python-offline/
├── generate_mock_data.py      # 步骤1：生成模拟外部API数据
├── clean_blacklist.py         # 步骤2：清洗黑名单数据
├── clean_user_features.py     # 步骤3：清洗用户行为特征数据
├── load_to_mysql.py           # 步骤4：将清洗后数据写入MySQL
├── train_scoring_model.py     # 模型训练（分析CSV文件）
├── data/                      # CSV数据文件存放目录
│   ├── raw/                   # 原始数据（模拟外部API）
│   │   ├── raw_blacklist.csv          # 原始黑名单数据
│   │   └── raw_user_features.csv      # 原始用户行为特征数据
│   ├── cleaned/               # 清洗后数据
│   │   ├── cleaned_blacklist.csv      # 清洗后的黑名单
│   │   └── cleaned_user_features.csv  # 清洗后的用户特征
│   └── training_data.csv      # 老师提供的历史训练数据CSV
├── notebooks/                 # Jupyter 探索
│   └── data_exploration.ipynb # 数据分析
└── output/
    └── scoring_rules.json     # 产出的评分规则文件
```

**目录说明：**

| 目录/文件                                    | 说明                          |
| ---------------------------------------- | --------------------------- |
| `data/raw/`                              | 原始数据目录，存放模拟外部API返回的"乱七八糟"数据 |
| `data/raw/raw_blacklist.csv`             | 原始黑名单数据（含重复、脏数据）            |
| `data/raw/raw_user_features.csv`         | 原始用户行为特征数据（含多头借贷等）          |
| `data/cleaned/`                          | 清洗后数据目录，存放经过清洗去重的数据         |
| `data/cleaned/cleaned_blacklist.csv`     | 清洗后的黑名单数据                   |
| `data/cleaned/cleaned_user_features.csv` | 清洗后的用户特征数据                  |

### 2.3 三层决策体系

| 层级 | 组件    | 数据来源                                    | 决策方式   |
| -- | ----- | --------------------------------------- | ------ |
| L1 | 黑名单初筛 | blacklist 表                             | 命中即拒绝  |
| L2 | 特征组装  | user\_info + user\_external\_features 表 | 特征向量拼接 |
| L3 | 模型打分  | scoring\_rules 表                        | 线性加权评分 |

***

## 三、数据库设计

### 3.1 表结构清单

| 表名                       | 职责       | 更新方          | 更新频率  |
| ------------------------ | -------- | ------------ | ----- |
| `blacklist`              | 一票否决名单   | Python 同步    | 每日    |
| `user_external_features` | 用户外部特征数据 | Python 同步    | 每日/每周 |
| `scoring_rules`          | 评分规则配置   | Python 训练后写入 | 每月/按需 |

### 3.2 黑名单表（索引：id\_card）

```sql
CREATE TABLE blacklist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    id_card VARCHAR(18) NOT NULL COMMENT '身份证号（唯一索引）',
    phone VARCHAR(20) COMMENT '手机号',
    reason VARCHAR(200) NOT NULL COMMENT '拉黑原因',
    source VARCHAR(50) NOT NULL COMMENT '来源：EXTERNAL_COURT/EXTERNAL_FRAUD/INTERNAL_OVERDUE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '拉黑时间',
    expire_at DATETIME NULL COMMENT '过期时间（NULL=永久）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_id_card (id_card) COMMENT '身份证号唯一索引，用于快速查询黑名单'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='黑名单表——一票否决';
```

### 3.3 用户外部特征表（索引：id\_card）

```sql
CREATE TABLE user_external_features (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    id_card VARCHAR(18) NOT NULL COMMENT '身份证号（唯一索引）',
    credit_score INT COMMENT '央行征信分（模拟）',
    overdue_count_12m INT DEFAULT 0 COMMENT '近12月逾期次数',
    credit_query_count_3m INT DEFAULT 0 COMMENT '近3月征信被查询次数',
    multi_head_loan_count INT DEFAULT 0 COMMENT '当前多头借贷平台数',
    multi_head_loan_total_amount DECIMAL(15,2) DEFAULT 0 COMMENT '多头借贷总金额',
    device_is_virtual TINYINT(1) DEFAULT 0 COMMENT '是否虚拟设备 0=否 1=是',
    device_change_count_30d INT DEFAULT 0 COMMENT '近30天更换设备次数',
    ip_is_proxy TINYINT(1) DEFAULT 0 COMMENT '是否使用代理IP 0=否 1=是',
    data_source VARCHAR(50) COMMENT '数据来源',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_id_card (id_card) COMMENT '身份证号唯一索引，用于快速查询用户外部特征'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户外部特征表——Python定时同步';
```

### 3.4 评分规则表（存储于MySQL）

与仓库 [`risk-assessment/load_to_mysql.py`](risk-assessment/load_to_mysql.py) 中 DDL 一致：`load_to_mysql` 会先 **`DROP TABLE IF EXISTS scoring_rules`** 再建表（仅影响本表）。除全量 `rule_content` 外，将 Python 产出的 JSON **拆列** 存储，便于查询；Java 优先用解析列拼装规则树，缺列时再读 `rule_content`。

```sql
CREATE TABLE scoring_rules (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    version VARCHAR(20) NOT NULL COMMENT '规则版本号',
    rule_content JSON NOT NULL COMMENT '完整规则JSON备份',
    feature_weights JSON NOT NULL COMMENT 'LR特征权重 rules.feature_weights',
    scorecard JSON NOT NULL COMMENT '评分卡 rules.scorecard',
    application_rule_bonus JSON NULL COMMENT '申请表策略加成',
    feature_scores JSON NULL COMMENT '旧版逐项规则 feature_scores',
    feature_derivation JSON NULL COMMENT '特征推导说明 feature_derivation',
    intercept DECIMAL(16,8) NOT NULL COMMENT 'LR截距',
    threshold_auto_approve DECIMAL(10,2) NOT NULL COMMENT '自动通过阈值（PDO量表如350–950）',
    threshold_manual_review DECIMAL(10,2) NOT NULL COMMENT '人工审核阈值',
    is_active TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否激活',
    trained_at DATETIME NULL COMMENT '训练时间',
    training_data_count INT NULL COMMENT '训练数据量',
    accuracy DECIMAL(10,6) NULL COMMENT '模型准确率等指标',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_version (version),
    KEY idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分规则——解析列+全量JSON';
```

***

## 四、Python 离线任务设计

### 4.1 黑名单同步任务

**文件：** `sync_blacklist.py`
**频率：** 每日凌晨 3:00

```python
def sync_blacklist():
    external_court = mock_call_court_api()
    external_fraud = mock_call_fraud_api()
    internal_overdue = query_internal_overdue_users(min_overdue=3)
    
    all_blacklist = deduplicate_and_merge(external_court, external_fraud, internal_overdue)
    
    for record in all_blacklist:
        upsert_into_blacklist(record)
    
    print(f"黑名单同步完成，共 {len(all_blacklist)} 条")
```

### 4.2 外部特征同步任务

**文件：** `sync_external_features.py`
**频率：** 每日凌晨 2:00

```python
def sync_external_features():
    active_users = get_active_users()
    
    for user in active_users:
        credit_data = mock_credit_api(user.id_card)
        multi_head_data = mock_multi_head_api(user.id_card)
        device_data = mock_device_api(user.id_card)
        
        upsert_user_features(
            user_id=user.id,
            id_card=user.id_card,
            credit_score=credit_data.get('score'),
            overdue_count_12m=credit_data.get('overdue_count'),
            credit_query_count_3m=credit_data.get('query_count'),
            multi_head_loan_count=multi_head_data.get('platform_count'),
            multi_head_loan_total_amount=multi_head_data.get('total_amount'),
            device_is_virtual=device_data.get('is_virtual', 0),
            ip_is_proxy=device_data.get('is_proxy', 0),
            data_source='EXTERNAL_SYNC'
        )
```

### 4.3 模型训练任务（分析CSV文件）

**文件：** `train_scoring_model.py`
**频率：** 每月/按需

```python
def train_scoring_model(csv_path="data/training_data.csv"):
    """
    模型训练任务
    CSV文件路径：python-offline/data/training_data.csv
    """
    import pandas as pd
    import numpy as np
    from sklearn.linear_model import LogisticRegression
    import json
    
    # 加载CSV训练数据
    df = pd.read_csv(csv_path)
    
    # 特征和标签
    X = df[['age', 'income', 'multi_head_count', 'credit_query_count', 
            'overdue_12m', 'device_is_virtual']]
    y = df['defaulted']
    
    # 特征分箱处理
    X_binned = bin_features(X)
    
    # 训练逻辑回归模型
    model = LogisticRegression()
    model.fit(X_binned, y)
    
    # 导出评分规则
    rules = {
        "feature_weights": dict(zip(X_binned.columns, model.coef_[0])),
        "intercept": float(model.intercept_[0]),
        "thresholds": {"auto_approve": 80, "manual_review": 60}
    }
    
    # 保存到JSON文件
    with open("output/scoring_rules.json", "w") as f:
        json.dump(rules, f, ensure_ascii=False, indent=2)
    
    # 写入MySQL数据库
    insert_into_scoring_rules(version="v2.3", rules=rules, is_active=True)
    
    print(f"模型训练完成，训练数据量：{len(df)}条")
    print(f"模型准确率：{model.score(X_binned, y):.4f}")
```

***

## 五、核心评分模型

### 5.1 评分规则

采用加权评分模型，总分 100 分：

| 评分维度 | 权重  | 评分规则                             |
| ---- | --- | -------------------------------- |
| 基础画像 | 30分 | 学历(10分) + 婚姻状况(10分) + 年龄(10分)    |
| 经济实力 | 40分 | 月收入(20分) + 资产情况(15分) + 职业稳定性(5分) |
| 外部征信 | 30分 | 逾期历史 + 多头借贷 + 查询次数               |

### 5.2 WoE 编码映射表

```python
woe_mappings = {
    "education": {"博士": 2.5, "硕士": 2.0, "本科": 1.5, "大专": 1.0, "其他": 0.5},
    "marriage": {"已婚": 1.2, "未婚": 0.8, "其他": 0.5},
    "jobType": {"企事业单位": 1.5, "私营企业": 1.0, "外资/合资": 1.2, "其他": 0.5}
}
```

### 5.3 IV 值特征筛选

| 特征            | IV值  | 重要性  |
| ------------- | ---- | ---- |
| monthlyIncome | 0.42 | ⭐⭐⭐⭐ |
| education     | 0.35 | ⭐⭐⭐  |
| hasHouse      | 0.28 | ⭐⭐⭐  |
| hasCar        | 0.22 | ⭐⭐   |
| jobType       | 0.18 | ⭐⭐   |

### 5.4 详细评分标准

#### 5.4.1 基础画像评分 (30分)

| 变量     | 特征值    | 得分 |
| ------ | ------ | -- |
| 学历     | 博士/硕士  | 10 |
| <br /> | 本科     | 8  |
| <br /> | 大专     | 5  |
| <br /> | 其他     | 2  |
| 婚姻状况   | 已婚     | 10 |
| <br /> | 未婚     | 7  |
| <br /> | 其他     | 3  |
| 年龄     | 25-45岁 | 10 |
| <br /> | 18-24岁 | 5  |
| <br /> | >45岁   | 7  |

#### 5.4.2 经济实力评分 (40分)

| 变量     | 特征值    | 得分 |
| ------ | ------ | -- |
| 月收入    | >15k   | 20 |
| <br /> | 8k-15k | 15 |
| <br /> | 3k-8k  | 10 |
| <br /> | <3k    | 5  |
| 资产情况   | 有房有车   | 20 |
| <br /> | 有房     | 15 |
| <br /> | 有车     | 5  |
| <br /> | 无      | 0  |

#### 5.4.3 外部征信评分 (30分)

| 变量     | 特征值 | 得分    |
| ------ | --- | ----- |
| 多头借贷   | >3次 | -20   |
| <br /> | ≤3次 | 0     |
| 逾期历史   | >0次 | -15/次 |
| <br /> | 0次  | 0     |
| 查询次数   | >5次 | -10   |
| <br /> | ≤5次 | 0     |

### 5.5 线性回归模拟算法

```python
weights = {
    "education": {"博士": 0.15, "硕士": 0.12, "本科": 0.08, "大专": 0.04, "其他": 0.01},
    "marriage": {"已婚": 0.08, "未婚": 0.05, "其他": 0.02},
    "hasHouse": {True: 0.12, False: 0},
    "hasCar": {True: 0.06, False: 0},
    "monthlyIncome": {"15000以上": 0.15, "8000-15000": 0.10, "3000-8000": 0.05, "3000以下": 0.01},
    "overdue_count": lambda x: max(0, -0.05 * x),
    "loan_count": lambda x: max(0, -0.03 * x),
    "recent_query_count": lambda x: max(0, -0.02 * x)
}

score = 50  # 基础分
score += weights["education"].get(education, 0) * 100
# ... 其他特征
```

### 5.6 评分卡刻度转换

| 参数          | 值   | 说明           |
| ----------- | --- | ------------ |
| PD0         | 0.5 | 基准违约概率（50%）  |
| PDO         | 20  | 违约概率降低一半所需分数 |
| base\_score | 600 | 基准分          |

### 5.7 策略决策规则

| 信用分   | 决策             | 说明          |
| ----- | -------------- | ----------- |
| >= 80 | APPROVE        | 自动通过，计算授信额度 |
| 60-79 | MANUAL\_REVIEW | 转人工审核       |
| < 60  | REJECT         | 自动拒绝        |

### 5.8 一票否决项

| 规则    | 条件        | 处理方式 |
| ----- | --------- | ---- |
| 黑名单命中 | 身份证号命中黑名单 | 直接拒绝 |
| 年龄准入  | 年龄 < 18 岁 | 直接拒绝 |
| 身份一致性 | 姓名与身份证不符  | 直接拒绝 |
| 设备异常  | 检测为模拟器    | 直接拒绝 |

***

## 六、额度计算模型

### 6.1 额度计算规则

| 评分范围    | 基础额度倍数   | 最高额度 |
| ------- | -------- | ---- |
| 80-100分 | 月收入 × 12 | 50万  |
| 70-79分  | 月收入 × 10 | 30万  |
| 60-69分  | 月收入 × 8  | 20万  |
| <60分    | 0        | 0    |

### 6.2 月收入映射表

| 月收入等级      | 映射值 (元) |
| ---------- | ------- |
| 3000以下     | 2000    |
| 3000-8000  | 5500    |
| 8000-15000 | 11500   |
| 15000以上    | 20000   |

### 6.3 资产加成规则

| 资产情况 | 加成额度 (元) |
| ---- | -------- |
| 有房有车 | 80000    |
| 有房   | 50000    |
| 有车   | 20000    |
| 无    | 0        |

### 6.4 负债扣减规则

| 指标   | 扣减比例            |
| ---- | --------------- |
| 多头借贷 | 每增加1家平台，额度减少10% |
| 逾期记录 | 每有1次逾期，额度减少15%  |

### 6.5 额度计算公式

**最终额度 = min(基础额度 + 资产加成 - 负债扣减, 最高额度)**

***

## 七、数据融合比对逻辑

### 7.1 收入真实性核验

```python
def check_income_authenticity(user_income, crawler_income):
    user_income_value = income_mapping.get(user_income, 2000)
    discrepancy = abs(user_income_value - crawler_income) / max(user_income_value, crawler_income)
    
    if discrepancy > 0.3:
        return {"status": "WARNING", "reason": "收入造假", "penalty": 30}
    else:
        return {"status": "SUCCESS", "reason": "收入一致", "penalty": 0}
```

### 7.2 归属地交叉验证

```python
def check_location_consistency(idCard, phone):
    idCard_province = extract_province_from_idCard(idCard)
    phone_province = extract_province_from_phone(phone)
    
    if idCard_province == phone_province:
        return {"status": "SUCCESS", "reason": "归属地匹配"}
    else:
        return {"status": "WARNING", "reason": f"归属地不一致"}
```

***

## 八、数据生成与清洗流程

### 8.1 步骤1：生成模拟外部API数据（generate\_mock\_data.py）

```python
import pandas as pd
import numpy as np
import os

np.random.seed(42)

# 创建目录
os.makedirs("data/raw", exist_ok=True)
os.makedirs("data/cleaned", exist_ok=True)

# ==================== 模拟外部API ====================

def mock_call_court_api():
    """模拟调用司法失信人API - 返回"乱七八糟"的原始数据"""
    blacklist = []
    for i in range(50):
        # 模拟脏数据：重复记录、空值、格式错误
        blacklist.append({
            "id_card": f"1101011980{np.random.randint(1,13):02d}{np.random.randint(1,29):02d}{i:04d}",
            "name": f"失信人{i}" if np.random.random() > 0.1 else "",  # 10%空值
            "phone": f"138{np.random.randint(1000,10000):04d}{np.random.randint(1000,10000):04d}",
            "reason": "法院失信被执行人，已被限制高消费",
            "source": "EXTERNAL_COURT",
            "extra_field": "junk_data"  # 多余字段
        })
    # 添加重复记录（模拟API返回重复数据）
    for i in range(10):
        blacklist.append(blacklist[i])
    return blacklist

def mock_call_fraud_api():
    """模拟调用反欺诈联盟API"""
    blacklist = []
    for i in range(30):
        blacklist.append({
            "id_card": f"3101011985{np.random.randint(1,13):02d}{np.random.randint(1,29):02d}{i:04d}",
            "name": f"欺诈用户{i}",
            "phone": f"139{np.random.randint(1000,10000):04d}{np.random.randint(1000,10000):04d}" if np.random.random() > 0.05 else None,  # 5%空值
            "reason": "涉嫌欺诈，提供虚假身份信息",
            "source": "EXTERNAL_FRAUD"
        })
    return blacklist

def mock_credit_api(id_card):
    """模拟调用征信API"""
    seed = hash(id_card) % 1000
    return {
        "id_card": id_card,
        "credit_score": np.random.randint(300, 900),
        "overdue_count": min(seed % 5, 3),
        "query_count": min(seed % 10, 8),
        "update_time": f"2024-{np.random.randint(1,13):02d}-{np.random.randint(1,29):02d}"
    }

def mock_multi_head_api(id_card):
    """模拟调用多头借贷API"""
    seed = hash(id_card) % 1000
    return {
        "id_card": id_card,
        "platform_count": min(seed % 8, 10),
        "total_amount": np.random.randint(0, 500000),
        "latest_loan_time": f"2024-{np.random.randint(1,13):02d}-{np.random.randint(1,29):02d}",
        "risk_level": np.random.choice(["LOW", "MEDIUM", "HIGH"], p=[0.6, 0.3, 0.1])
    }

def mock_device_api(id_card):
    """模拟调用设备指纹API"""
    seed = hash(id_card) % 100
    return {
        "id_card": id_card,
        "is_virtual": 1 if seed < 3 else 0,
        "is_proxy": 1 if seed < 5 else 0,
        "device_count": np.random.randint(1, 5),
        "location_change_count": np.random.randint(0, 10)
    }

# ==================== 生成原始黑名单数据 ====================

court_blacklist = mock_call_court_api()
fraud_blacklist = mock_call_fraud_api()

# 添加内部逾期用户
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

# ==================== 生成原始用户特征数据 ====================

user_features = []
for _, user in users.iterrows():
    credit_data = mock_credit_api(user["id_card"])
    multi_head_data = mock_multi_head_api(user["id_card"])
    device_data = mock_device_api(user["id_card"])
    
    # 模拟数据格式不一致
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
        # 添加一些脏数据
        "invalid_field": np.random.choice([None, "garbage"], p=[0.9, 0.1])
    })

raw_user_features_df = pd.DataFrame(user_features)
raw_user_features_df.to_csv("data/raw/raw_user_features.csv", index=False, encoding="utf-8-sig")
print(f"原始用户特征数据生成完毕，共 {len(raw_user_features_df)} 条")
```

### 8.2 步骤2：清洗黑名单数据（clean\_blacklist.py）

```python
import pandas as pd
import re

def clean_blacklist():
    # 读取原始数据
    df = pd.read_csv("data/raw/raw_blacklist.csv", encoding="utf-8-sig")
    
    print(f"原始数据行数: {len(df)}")
    
    # 1. 删除多余字段
    df = df.drop(columns=["extra_field", "invalid_field"], errors="ignore")
    
    # 2. 去重（按身份证号）
    initial_count = len(df)
    df = df.drop_duplicates(subset="id_card", keep="first")
    print(f"去重后行数: {len(df)} (删除 {initial_count - len(df)} 条重复)")
    
    # 3. 验证身份证号格式
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
    
    # 4. 填充缺失的姓名
    df["name"] = df["name"].fillna("未知")
    
    # 5. 填充缺失的手机号（用默认值）
    df["phone"] = df["phone"].fillna("未知")
    
    # 6. 确保source字段有效
    valid_sources = ["EXTERNAL_COURT", "EXTERNAL_FRAUD", "INTERNAL_OVERDUE"]
    df["source"] = df["source"].apply(lambda x: x if x in valid_sources else "UNKNOWN")
    
    # 7. 格式化日期字段
    df["created_at"] = pd.Timestamp.now()
    df["expire_at"] = None  # 永久有效
    
    # 8. 保留需要的字段
    df = df[["id_card", "phone", "reason", "source", "created_at", "expire_at"]]
    
    # 保存清洗后数据
    df.to_csv("data/cleaned/cleaned_blacklist.csv", index=False, encoding="utf-8-sig")
    print(f"清洗后黑名单数据: {len(df)} 条")
    
    return df

if __name__ == "__main__":
    clean_blacklist()
```

### 8.3 步骤3：清洗用户行为特征数据（clean\_user\_features.py）

```python
import pandas as pd
import re

def clean_user_features():
    # 读取原始数据
    df = pd.read_csv("data/raw/raw_user_features.csv", encoding="utf-8-sig")
    
    print(f"原始数据行数: {len(df)}")
    
    # 1. 删除多余字段
    df = df.drop(columns=["invalid_field", "risk_level", "latest_loan_time"], errors="ignore")
    
    # 2. 去重（按身份证号）
    initial_count = len(df)
    df = df.drop_duplicates(subset="id_card", keep="first")
    print(f"去重后行数: {len(df)} (删除 {initial_count - len(df)} 条重复)")
    
    # 3. 验证身份证号格式
    def is_valid_id_card(id_card):
        if pd.isna(id_card):
            return False
        id_card = str(id_card).strip()
        return len(id_card) == 18 and re.match(r'^\d{17}[\dXx]$', id_card)
    
    df = df[df["id_card"].apply(is_valid_id_card)]
    print(f"有效身份证数据: {len(df)} 条")
    
    # 4. 处理缺失值
    df["credit_score"] = df["credit_score"].fillna(0)
    df["overdue_count_12m"] = df["overdue_count_12m"].fillna(0).astype(int)
    df["credit_query_count_3m"] = df["credit_query_count_3m"].fillna(0).astype(int)
    df["multi_head_loan_count"] = df["multi_head_loan_count"].fillna(0).astype(int)
    df["multi_head_loan_total_amount"] = df["multi_head_loan_total_amount"].fillna(0)
    df["device_is_virtual"] = df["device_is_virtual"].fillna(0).astype(int)
    df["ip_is_proxy"] = df["ip_is_proxy"].fillna(0).astype(int)
    df["device_change_count_30d"] = df["device_change_count_30d"].fillna(0).astype(int)
    
    # 5. 数据范围校验
    df["credit_score"] = df["credit_score"].clip(300, 900)
    df["overdue_count_12m"] = df["overdue_count_12m"].clip(0, 100)
    df["credit_query_count_3m"] = df["credit_query_count_3m"].clip(0, 100)
    df["multi_head_loan_count"] = df["multi_head_loan_count"].clip(0, 50)
    
    # 6. 添加数据来源标记
    df["data_source"] = "EXTERNAL_API"
    
    # 7. 添加更新时间
    df["updated_at"] = pd.Timestamp.now()
    
    # 8. 保留需要的字段
    df = df[[
        "id_card", "credit_score", "overdue_count_12m", 
        "credit_query_count_3m", "multi_head_loan_count",
        "multi_head_loan_total_amount", "device_is_virtual",
        "device_change_count_30d", "ip_is_proxy",
        "data_source", "updated_at"
    ]]
    
    # 保存清洗后数据
    df.to_csv("data/cleaned/cleaned_user_features.csv", index=False, encoding="utf-8-sig")
    print(f"清洗后用户特征数据: {len(df)} 条")
    
    return df

if __name__ == "__main__":
    clean_user_features()
```

### 8.4 步骤4：自动建库建表并写入数据（load\_to\_mysql.py）

```python
import pandas as pd
import mysql.connector
from mysql.connector import Error
from dotenv import load_dotenv
import os
import json

load_dotenv()

# 数据库配置
DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", 3306)),
    "user": os.getenv("DB_USER", "admin"),
    "password": os.getenv("DB_PASSWORD", "admin"),
    "database": os.getenv("DB_NAME", "credit_data_db")
}

def create_database_if_not_exists():
    """如果数据库不存在则创建"""
    try:
        conn = mysql.connector.connect(
            host=DB_CONFIG["host"],
            port=DB_CONFIG["port"],
            user=DB_CONFIG["user"],
            password=DB_CONFIG["password"]
        )
        
        if conn.is_connected():
            cursor = conn.cursor()
            cursor.execute(f"CREATE DATABASE IF NOT EXISTS {DB_CONFIG['database']}")
            print(f"数据库 {DB_CONFIG['database']} 检查/创建完成")
            cursor.close()
            conn.close()
    except Error as e:
        print(f"创建数据库失败: {e}")

def create_tables_if_not_exists():
    """如果表不存在则创建"""
    conn = None
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
        # 创建黑名单表
        create_blacklist_table = """
            CREATE TABLE IF NOT EXISTS blacklist (
                id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                id_card VARCHAR(18) NOT NULL COMMENT '身份证号',
                phone VARCHAR(20) COMMENT '手机号',
                reason VARCHAR(200) NOT NULL COMMENT '拉黑原因',
                source VARCHAR(50) NOT NULL COMMENT '来源',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '拉黑时间',
                expire_at DATETIME NULL COMMENT '过期时间',
                UNIQUE KEY uk_id_card (id_card)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='黑名单表';
        """
        cursor.execute(create_blacklist_table)
        
        # 创建用户外部特征表
        create_features_table = """
            CREATE TABLE IF NOT EXISTS user_external_features (
                id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                id_card VARCHAR(18) NOT NULL COMMENT '身份证号',
                credit_score INT COMMENT '央行征信分',
                overdue_count_12m INT DEFAULT 0 COMMENT '近12月逾期次数',
                credit_query_count_3m INT DEFAULT 0 COMMENT '近3月征信查询次数',
                multi_head_loan_count INT DEFAULT 0 COMMENT '多头借贷平台数',
                multi_head_loan_total_amount DECIMAL(15,2) DEFAULT 0 COMMENT '多头借贷总金额',
                device_is_virtual TINYINT(1) DEFAULT 0 COMMENT '是否虚拟设备',
                device_change_count_30d INT DEFAULT 0 COMMENT '近30天更换设备次数',
                ip_is_proxy TINYINT(1) DEFAULT 0 COMMENT '是否代理IP',
                data_source VARCHAR(50) COMMENT '数据来源',
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY uk_id_card (id_card)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户外部特征表';
        """
        cursor.execute(create_features_table)
        
        cursor.execute("DROP TABLE IF EXISTS scoring_rules")
        create_rules_table = """
            CREATE TABLE scoring_rules (
                id BIGINT NOT NULL AUTO_INCREMENT,
                version VARCHAR(20) NOT NULL,
                rule_content JSON NOT NULL,
                feature_weights JSON NOT NULL,
                scorecard JSON NOT NULL,
                application_rule_bonus JSON NULL,
                feature_scores JSON NULL,
                feature_derivation JSON NULL,
                intercept DECIMAL(16,8) NOT NULL,
                threshold_auto_approve DECIMAL(10,2) NOT NULL,
                threshold_manual_review DECIMAL(10,2) NOT NULL,
                is_active TINYINT(1) NOT NULL DEFAULT 0,
                trained_at DATETIME NULL,
                training_data_count INT NULL,
                accuracy DECIMAL(10,6) NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (id),
                UNIQUE KEY uk_version (version),
                KEY idx_is_active (is_active)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        """
        cursor.execute(create_rules_table)
        
        conn.commit()
        print("所有表检查/创建完成")
        
    except Error as e:
        print(f"创建表失败: {e}")
        if conn:
            conn.rollback()
    finally:
        if conn and conn.is_connected():
            cursor.close()
            conn.close()

def get_db_connection():
    """获取数据库连接"""
    return mysql.connector.connect(**DB_CONFIG)

def load_blacklist_to_mysql():
    """加载清洗后的黑名单数据到MySQL"""
    df = pd.read_csv("data/cleaned/cleaned_blacklist.csv", encoding="utf-8-sig")
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        for _, row in df.iterrows():
            # UPSERT：存在则更新，不存在则插入
            sql = """
                INSERT INTO blacklist (id_card, phone, reason, source, created_at, expire_at)
                VALUES (%s, %s, %s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                    phone = VALUES(phone),
                    reason = VALUES(reason),
                    source = VALUES(source),
                    expire_at = VALUES(expire_at)
            """
            cursor.execute(sql, (
                row["id_card"],
                row["phone"],
                row["reason"],
                row["source"],
                row["created_at"],
                row["expire_at"]
            ))
        
        conn.commit()
        print(f"成功写入黑名单数据: {len(df)} 条")
    
    except Exception as e:
        print(f"写入黑名单数据失败: {e}")
        conn.rollback()
    finally:
        cursor.close()
        conn.close()

def load_user_features_to_mysql():
    """加载清洗后的用户特征数据到MySQL"""
    df = pd.read_csv("data/cleaned/cleaned_user_features.csv", encoding="utf-8-sig")
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        for _, row in df.iterrows():
            sql = """
                INSERT INTO user_external_features (
                    id_card, credit_score, overdue_count_12m, 
                    credit_query_count_3m, multi_head_loan_count,
                    multi_head_loan_total_amount, device_is_virtual,
                    device_change_count_30d, ip_is_proxy,
                    data_source, updated_at
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                    credit_score = VALUES(credit_score),
                    overdue_count_12m = VALUES(overdue_count_12m),
                    credit_query_count_3m = VALUES(credit_query_count_3m),
                    multi_head_loan_count = VALUES(multi_head_loan_count),
                    multi_head_loan_total_amount = VALUES(multi_head_loan_total_amount),
                    device_is_virtual = VALUES(device_is_virtual),
                    device_change_count_30d = VALUES(device_change_count_30d),
                    ip_is_proxy = VALUES(ip_is_proxy),
                    data_source = VALUES(data_source),
                    updated_at = VALUES(updated_at)
            """
            cursor.execute(sql, (
                row["id_card"],
                row["credit_score"],
                row["overdue_count_12m"],
                row["credit_query_count_3m"],
                row["multi_head_loan_count"],
                row["multi_head_loan_total_amount"],
                row["device_is_virtual"],
                row["device_change_count_30d"],
                row["ip_is_proxy"],
                row["data_source"],
                row["updated_at"]
            ))
        
        conn.commit()
        print(f"成功写入用户特征数据: {len(df)} 条")
    
    except Exception as e:
        print(f"写入用户特征数据失败: {e}")
        conn.rollback()
    finally:
        cursor.close()
        conn.close()

def load_scoring_rules_to_mysql():
    """加载评分规则：写入 rule_content + feature_weights/scorecard 等解析列（实现见仓库 load_to_mysql.py）。"""
    ...

if __name__ == "__main__":
    print("=" * 50)
    print("步骤1: 创建数据库（如果不存在）")
    create_database_if_not_exists()
    
    print("\n步骤2: 创建表（如果不存在）")
    create_tables_if_not_exists()
    
    print("\n步骤3: 写入黑名单数据")
    load_blacklist_to_mysql()
    
    print("\n步骤4: 写入用户特征数据")
    load_user_features_to_mysql()
    
    print("\n步骤5: 写入评分规则数据")
    load_scoring_rules_to_mysql()
    
    print("\n" + "=" * 50)
    print("所有数据写入完成！")
```

### 8.5 执行流程说明

| 步骤 | 脚本                       | 输入                               | 输出                                                            | 说明                  |
| -- | ------------------------ | -------------------------------- | ------------------------------------------------------------- | ------------------- |
| 1  | `generate_mock_data.py`  | 无                                | `data/raw/raw_blacklist.csv` `data/raw/raw_user_features.csv` | 模拟外部API返回的"乱七八糟"数据  |
| 2  | `clean_blacklist.py`     | `data/raw/raw_blacklist.csv`     | `data/cleaned/cleaned_blacklist.csv`                          | 清洗黑名单：去重、格式校验、缺失值处理 |
| 3  | `clean_user_features.py` | `data/raw/raw_user_features.csv` | `data/cleaned/cleaned_user_features.csv`                      | 清洗用户特征：去重、范围校验、类型转换 |
| 4  | `load_to_mysql.py`       | `data/cleaned/*.csv`             | MySQL数据库                                                      | 将清洗后数据写入数据库         |

**执行顺序：**

```bash
python generate_mock_data.py   # 生成原始数据
python clean_blacklist.py      # 清洗黑名单
python clean_user_features.py  # 清洗用户特征
python load_to_mysql.py        # 写入数据库
```

***

## 九、部署说明

### 9.1 依赖配置

```
pandas>=2.0.0
scikit-learn>=1.3.0
mysql-connector-python>=8.0.33
python-dotenv>=1.0.0
fastapi>=0.100.0
uvicorn>=0.22.0
```

### 9.2 配置文件（.env）

```env
DB_HOST=47.109.109.231
DB_PORT=3306
DB_USER=admin
DB_PASSWORD=admin
DB_NAME=credit_data_db
```

### 9.3 定时任务配置

```bash
# 每日凌晨 2:00 同步外部特征
0 2 * * * python /path/to/sync_external_features.py

# 每日凌晨 3:00 同步黑名单
0 3 * * * python /path/to/sync_blacklist.py

# 每月1日凌晨 4:00 训练模型
0 4 1 * * python /path/to/train_scoring_model.py
```

***

## 十、模型验证

### 10.1 评估指标

| 指标  | 目标值    | 说明     |
| --- | ------ | ------ |
| AUC | > 0.75 | 模型区分能力 |
| KS  | > 0.4  | 模型区分能力 |
| 准确率 | > 80%  | 预测准确率  |
| PSI | < 0.1  | 模型稳定性  |

### 10.2 测试用例

| 场景      | 预期结果                   |
| ------- | ---------------------- |
| 完美信用用户  | 评分95+，决策APPROVE，额度32万  |
| 收入造假用户  | 差距>30%，扣30分，风险标签"收入存疑" |
| 命中黑名单用户 | 直接拒绝，评分0               |

***

## 十一、监控与维护

### 11.1 监控指标

| 指标       | 监控频率 | 预警阈值  |
| -------- | ---- | ----- |
| 模型准确率    | 每日   | < 75% |
| 黑名单同步成功率 | 每日   | < 95% |
| 拒绝率异常波动  | 每日   | > 20% |

### 11.2 维护计划

| 维护类型  | 频率 | 内容          |
| ----- | -- | ----------- |
| 模型重训练 | 每月 | 使用新数据重新训练模型 |
| 规则验证  | 每周 | 验证评分规则准确性   |

***

## 十二、总结

本设计实现了一个**离线与在线分离**的风控评分系统：

1. **Python 离线层**：黑名单同步、外部数据同步、模型训练、模拟数据生成
2. **Java 在线层**：黑名单初筛 → 特征组装 → 模型打分 → 策略决策
3. **数据与规则分离**：用户数据存表，评分权重存 JSON 配置
4. **三层决策体系**：硬规则一票否决 → 统计模型量化风险 → 策略阈值分流
5. **CSV数据驱动**：基于历史CSV文件训练评分规则
6. **可解释性强**：支持WoE编码、IV值筛选、评分明细输出

系统通过数据库表作为Python和Java之间的接口，实现了清晰的职责分离和高效的数据传递。
