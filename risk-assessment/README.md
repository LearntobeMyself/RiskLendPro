# Python 风控离线流水线

基于 Home Credit 数据的 **离线 ETL + 模型训练 + MySQL 入库** 脚本集。  
在线评分由 Java 后端读取 `credit_data_db`，本目录不再包含 FastAPI 服务。

详细联调步骤见 [`../参考文件/运行说明.md`](../参考文件/运行说明.md)。

## 项目结构

```
risk-assessment/
├── clean_blacklist.py           # 清洗黑名单 → cleaned_blacklist.csv
├── clean_user_features.py       # 从 parquet 抽样 → cleaned_user_features.csv
├── clean_behavior_features.py   # B 卡行为特征清洗
├── feature_engineering_hc.py    # HC 宽表特征工程
├── woe_binning.py               # WOE 分箱与变换
├── train_scoring_model.py       # A 卡 LR+PDO 训练 → scoring_rules.json
├── train_b_card_model.py        # B 卡行为模型训练 → b_scoring_rules.json
├── load_to_mysql.py             # 建表并写入 credit_data_db
├── score_demo_users.py          # 离线复现 Java 算分（校准测试用例）
├── data/
│   ├── raw/
│   │   ├── blacklist.csv                    # 原始黑名单（温州公开数据）
│   │   └── home_credit_train_min.parquet    # HC 训练宽表
│   └── cleaned/
│       ├── cleaned_blacklist.csv
│       ├── cleaned_user_features.csv
│       └── cleaned_behavior_features.csv
├── output/
│   ├── scoring_rules.json       # A 卡规则（入库 + Java 读取）
│   ├── scoring_rules.md         # A 卡规则解读（与 scoring_rules.json 同步）
│   ├── b_scoring_rules.json     # B 卡规则
│   ├── b_scoring_rules.md       # B 卡规则解读（与 b_scoring_rules.json 同步）
│   └── demo_user_scores.json    # score_demo_users 产出（测试文档引用）
├── requirements.txt
└── .env                         # 本地 MySQL 配置（勿提交）
```

## 环境配置

创建 `.env`：

```env
DB_HOST=localhost
DB_PORT=3306
DB_USER=admin
DB_PASSWORD=admin
DB_NAME=credit_data_db
```

```bash
conda create -n risk-assessment python=3.9 -y
conda activate risk-assessment
cd risk-assessment
pip install -r requirements.txt
```

确认存在 `data/raw/home_credit_train_min.parquet`。

## 执行流程

### 1. 清洗数据

```bash
python clean_blacklist.py
python clean_user_features.py
python clean_behavior_features.py   # B 卡需要时执行
```

### 2. 训练模型

```bash
python train_scoring_model.py       # A 卡 → output/scoring_rules.json
python train_b_card_model.py        # B 卡 → output/b_scoring_rules.json
```

### 3. 写入 MySQL

```bash
python load_to_mysql.py
```

**注意**：会 `DROP` 后重建 `blacklist`、`user_external_features` 等表，生产环境慎用。

### 4. 可选：校准测试用例分数

```bash
python score_demo_users.py
```

产出 `output/demo_user_scores.json`，供 [`../sql/测试用例.md`](../sql/测试用例.md) 对照。

## 入库表（credit_data_db）

| 表名 | 说明 |
|------|------|
| `blacklist` | 失信被执行人黑名单 |
| `user_external_features` | 用户外部特征（A 卡） |
| `scoring_rules` | A 卡评分规则 |
| `user_behavior_features` | B 卡行为特征 |
| `behavior_scoring_rules` | B 卡评分规则 |

也可通过 Java 接口 `POST /sync/blacklist`（永久 API Token）增量写入黑名单。

## 相关文档

- [`../参考文件/csv数据来源和数据库表设计.md`](../参考文件/csv数据来源和数据库表设计.md)
- [`../参考文件/HomeCredit评分卡使用说明.md`](../参考文件/HomeCredit评分卡使用说明.md)
