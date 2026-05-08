# 风控评分系统 - Risk Assessment System

互联网个人贷款风控评分系统 - 离线数据处理版本，用于生成风控规则和评分卡。

## 项目概述

本项目是一个完整的风控评分系统数据处理流程，包含数据获取、清洗、模型训练和数据入库等环节。系统基于 Python 实现，主要用于处理黑名单数据和用户特征数据，生成评分规则供 Java 后端服务使用。

## 项目结构

```
risk-assessment/
├── data/                           # 数据目录
│   ├── raw/                        # 原始数据
│   │   ├── blacklist.csv           # 黑名单原始数据（失信被执行人信息）
│   │   └── user_features.csv       # 用户特征原始数据（Home Credit 数据集）
│   ├── cleaned/                    # 清洗后数据
│   │   ├── cleaned_blacklist.csv   # 清洗后的黑名单（200条有地区编码记录）
│   │   └── cleaned_user_features.csv # 清洗后的用户特征（按地区抽样500条）
│   ├── grouped_table.json          # 特征分组统计结果
│   ├── LCDataDictionary.xlsx       # 数据字典说明文档
│   ├── Receiver_Operating_Characteristic.png  # ROC曲线图表
│   └── training_data.csv           # 模型训练数据集
├── output/                         # 输出目录
│   └── scoring_rules.json          # 训练好的评分规则（JSON格式）
├── .env                            # 环境变量配置文件
├── clean_blacklist.py              # 黑名单数据清洗脚本
├── clean_user_features.py          # 用户特征数据清洗脚本
├── Dockerfile                      # Docker 容器配置（可选）
├── get_data.py                     # 数据获取脚本
├── load_to_mysql.py                # 数据入库脚本（MySQL）
├── requirements.txt                # Python 依赖包列表
└── train_scoring_model.py          # 评分模型训练脚本
```

## 快速开始

### 环境要求

- Python 3.9+
- MySQL 8.0+（可选，用于数据存储）
- pandas 1.5+
- scikit-learn 1.0+
- mysql-connector-python 8.0+

### 安装依赖

```bash
cd risk-assessment
pip install -r requirements.txt
```

### 配置环境变量

复制并修改 `.env` 文件配置数据库连接（如需入库）：

```env
# 数据库连接配置
DB_HOST=localhost
DB_PORT=3306
DB_USER=admin
DB_PASSWORD=admin
DB_NAME=credit_data_db
```

## 数据处理流程

### 1. 数据获取（可选）

从远程数据源获取示例数据：

```bash
python get_data.py
```

**输出**：下载原始数据到 `data/raw/` 目录

### 2. 数据清洗

```bash
# 清洗黑名单数据（保留200条有地区编码的记录）
python clean_blacklist.py

# 清洗用户特征数据（按地区110112和310000各抽取250条，共500条）
python clean_user_features.py
```

**输出**：
- `data/cleaned/cleaned_blacklist.csv` - 清洗后的黑名单数据
- `data/cleaned/cleaned_user_features.csv` - 清洗后的用户特征数据

### 3. 训练评分模型

```bash
python train_scoring_model.py
```

**输出**：`output/scoring_rules.json` - 训练好的评分规则

### 4. 数据入库（可选）

将清洗后的数据和评分规则写入 MySQL 数据库：

```bash
python load_to_mysql.py
```

**执行步骤**：
1. 创建数据库（如果不存在）
2. 创建数据表（blacklist、user_external_features、scoring_rules）
3. 写入黑名单数据
4. 写入用户特征数据
5. 写入评分规则

## 核心脚本说明

| 脚本 | 功能 | 输入 | 输出 |
|------|------|------|------|
| `get_data.py` | 从远程数据源获取示例数据 | 无 | `data/raw/*.csv` |
| `clean_blacklist.py` | 清洗黑名单数据，提取关键字段 | `data/raw/blacklist.csv` | `data/cleaned/cleaned_blacklist.csv` |
| `clean_user_features.py` | 清洗用户特征，生成身份证号，按地区抽样 | `data/raw/user_features.csv` | `data/cleaned/cleaned_user_features.csv` |
| `train_scoring_model.py` | 训练逻辑回归模型，生成评分卡 | `data/training_data.csv` | `output/scoring_rules.json` |
| `load_to_mysql.py` | 将数据和规则入库到 MySQL | `data/cleaned/*.csv`, `output/scoring_rules.json` | MySQL 数据库表 |

## 数据说明

### 黑名单数据字段（cleaned_blacklist.csv）

| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 被执行人姓名（支持通配符如"李*梅"） |
| area_code | string | 地区编码（6位数字，如110112） |
| birth_year | int | 出生年份 |
| case_no | string | 执行案号 |
| court_name | string | 执行法院名称 |
| duty_status | string | 被执行人履行情况 |
| behavior_details | string | 失信行为详情 |
| risk_level | string | 风险等级（HIGH/MEDIUM/LOW） |
| created_at | datetime | 数据创建时间 |
| expire_at | datetime | 过期时间（NULL=永久有效） |

### 用户特征数据字段（cleaned_user_features.csv）

| 字段 | 类型 | 说明 |
|------|------|------|
| id_card | string | 身份证号（生成的唯一标识） |
| area_code | string | 地区编码 |
| birth_year | int | 出生年份 |
| age | int | 年龄 |
| employment_years | float | 工作年限 |
| AMT_INCOME_TOTAL | float | 年收入总额 |
| credit_query_week | int | 近1周征信查询次数 |
| credit_query_month | int | 近1月征信查询次数 |
| phone_change_days | int | 手机换号天数 |
| active_loans_count | int | 活跃贷款数量 |
| ext_source_2 | float | 第三方评分A |
| ext_source_3 | float | 第三方评分B |
| has_car | int | 是否有车（0=否，1=是） |
| occupation_type | string | 职业类型 |
| education | string | 学历 |
| has_default_history | int | 是否有违约记录（0=否，1=是） |
| prev_refused_count | int | 历史被拒次数 |

### 评分规则输出格式（scoring_rules.json）

```json
{
  "version": "v1.0",
  "feature_weights": {
    "ext_source_2": 0.8,
    "ext_source_3": 0.6,
    "age": 0.3,
    "active_loans_count": -0.5
  },
  "scorecard": {
    "ext_source_2": {"0-0.3": 0, "0.3-0.6": 20, "0.6-1.0": 40}
  },
  "thresholds": {
    "auto_approve": 720,
    "manual_review": 580
  },
  "intercept": 0.5,
  "model_metrics": {
    "accuracy": 0.78,
    "auc": 0.85
  }
}
```

## 评分规则说明

### 评分卡结构
- **feature_weights**: 逻辑回归模型学习到的特征权重
- **scorecard**: 分箱评分规则，将特征值映射到评分
- **thresholds**: 决策阈值（自动通过/人工审核/拒绝）
- **intercept**: 逻辑回归截距项

### 决策规则
| 评分区间 | 决策 | 说明 |
|----------|------|------|
| ≥ 720 | 自动通过 | 信用良好 |
| 580 - 719 | 人工审核 | 需要人工复核 |
| < 580 | 自动拒绝 | 信用风险较高 |

## 黑名单匹配规则

系统采用三级匹配规则：

| 匹配等级 | 命中因子 | 风险等级 | 决策 |
|----------|----------|----------|------|
| 一级 | 仅姓名命中 | 低风险 | 通过（标记） |
| 二级 | 姓名 + 地域 | 中风险 | 人工审核 |
| 三级 | 姓名 + 地域 + 出生年份 | 高风险 | 直接拒绝 |

## 数据核验规则

收入偏差检测采用分级处理：

| 偏差范围 | 处理方式 | 说明 |
|----------|----------|------|
| 实际收入在自填区间内 | 自动通过 | 数据一致 |
| 偏差 ≤ 15% | 自动通过 | 小幅偏差，取后台值 |
| 15% < 偏差 ≤ 50% | 人工审核 | 中等偏差需核实 |
| 偏差 > 50% | 自动拒绝 | 严重虚报 |

## 数据库表结构

### blacklist（黑名单表）
```sql
CREATE TABLE blacklist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    area_code VARCHAR(20),
    birth_year INT,
    case_no VARCHAR(50),
    court_name VARCHAR(100),
    duty_status VARCHAR(50),
    behavior_details VARCHAR(500),
    risk_level VARCHAR(10),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    expire_at DATETIME NULL,
    KEY idx_name (name),
    KEY idx_area_code (area_code)
);
```

### user_external_features（用户外部特征表）
```sql
CREATE TABLE user_external_features (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sk_id_curr BIGINT NOT NULL UNIQUE,
    days_birth INT DEFAULT 0,
    days_employed INT DEFAULT 0,
    amt_income_total DECIMAL(15,2) DEFAULT 0,
    credit_bureau_week INT DEFAULT 0,
    credit_bureau_mon INT DEFAULT 0,
    days_last_phone_change INT DEFAULT 0,
    active_loans_count INT DEFAULT 0,
    ext_source_2 DECIMAL(10,6) DEFAULT 0,
    ext_source_3 DECIMAL(10,6) DEFAULT 0,
    flag_own_car TINYINT(1) DEFAULT 0,
    occupation_type VARCHAR(50),
    education_type VARCHAR(50),
    target TINYINT(1) DEFAULT 0,
    prev_refused_count INT DEFAULT 0,
    data_source VARCHAR(50),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### scoring_rules（评分规则表）
```sql
CREATE TABLE scoring_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version VARCHAR(20) NOT NULL UNIQUE,
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
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

## 使用示例

### 完整流程执行

```bash
# 1. 获取数据
python get_data.py

# 2. 清洗数据
python clean_blacklist.py
python clean_user_features.py

# 3. 训练模型
python train_scoring_model.py

# 4. 入库（需要配置数据库）
python load_to_mysql.py
```

### 仅训练模型

```bash
python train_scoring_model.py
```

### 仅数据入库

```bash
python load_to_mysql.py
```

## 代码风格

- 遵循 PEP 8 编码规范
- 使用类型提示（Type Hints）
- 函数和变量命名清晰
- 包含必要的注释说明

## License

MIT License
