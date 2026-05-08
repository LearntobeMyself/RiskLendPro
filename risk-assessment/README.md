# 风控评分系统 - Risk Assessment System

互联网个人贷款风控评分系统 - 离线数据处理版本，用于生成风控规则和评分卡。

## 项目结构

```
risk-assessment/
├── data/                   # 数据目录
│   ├── raw/                # 原始数据
│   │   ├── blacklist.csv           # 黑名单原始数据
│   │   └── user_features.csv       # 用户特征原始数据
│   ├── cleaned/            # 清洗后数据
│   │   ├── cleaned_blacklist.csv   # 清洗后的黑名单
│   │   └── cleaned_user_features.csv # 清洗后的用户特征
│   └── training_data.csv   # 模型训练数据
├── output/                 # 输出目录
│   └── scoring_rules.json  # 训练好的评分规则
├── .env                    # 环境变量配置
├── clean_blacklist.py      # 黑名单数据清洗脚本
├── clean_user_features.py  # 用户特征数据清洗脚本
├── Dockerfile              # Docker 配置（可选）
├── get_data.py             # 数据获取脚本
├── load_to_mysql.py        # 数据入库脚本
├── requirements.txt        # Python 依赖
└── train_scoring_model.py  # 评分模型训练脚本
```

## 快速开始

### 环境要求

- Python 3.9+
- MySQL 8.0+（可选，用于数据存储）

### 安装依赖

```bash
pip install -r requirements.txt
```

### 配置环境变量（可选）

复制 `.env` 文件并修改数据库连接配置（如果需要入库）：

```env
DB_HOST=localhost
DB_PORT=3306
DB_USER=your_username
DB_PASSWORD=your_password
DB_NAME=credit_data_db
```

## 数据处理流程

### 1. 数据获取（可选）

从远程数据源获取示例数据：

```bash
python get_data.py
```

### 2. 数据清洗

```bash
# 清洗黑名单数据（保留200条有地区编码的记录）
python clean_blacklist.py

# 清洗用户特征数据
python clean_user_features.py
```

### 3. 训练评分模型

```bash
python train_scoring_model.py
```

输出文件：`output/scoring_rules.json`

### 4. 数据入库（可选）

将清洗后的数据和评分规则写入 MySQL：

```bash
python load_to_mysql.py
```

## 核心脚本说明

| 脚本 | 功能 |
|------|------|
| `clean_blacklist.py` | 清洗黑名单数据，提取姓名、地区编码、出生年份等信息 |
| `clean_user_features.py` | 清洗用户特征数据，生成用于评分的特征 |
| `train_scoring_model.py` | 训练逻辑回归模型，生成评分卡规则 |
| `load_to_mysql.py` | 将数据和规则入库到 MySQL |
| `get_data.py` | 从远程数据源获取示例数据 |

## 数据说明

### 黑名单数据字段
- `name`: 被执行人姓名
- `area_code`: 地区编码
- `birth_year`: 出生年份
- `case_no`: 案号
- `court_name`: 执行法院
- `duty_status`: 履行情况
- `risk_level`: 风险等级 (HIGH/MEDIUM/LOW)

### 用户特征数据字段
- `SK_ID_CURR`: 用户唯一标识
- `age`: 年龄
- `employment_years`: 工作年限
- `AMT_INCOME_TOTAL`: 收入总额
- `ext_source_2/3`: 第三方评分
- `has_car`: 是否有车
- `occupation_type`: 职业类型
- `education`: 学历
- `has_default_history`: 历史违约记录

## 评分规则输出

评分规则存储在 `output/scoring_rules.json`，包含：
- `feature_weights`: 逻辑回归特征权重
- `scorecard`: 评分卡规则
- `thresholds`: 阈值配置（自动通过/人工审核）
- `intercept`: 逻辑回归截距项
- `model_metrics`: 模型评估指标（准确率、AUC等）

## License

MIT License