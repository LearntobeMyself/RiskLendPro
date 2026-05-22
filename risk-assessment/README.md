# Python 风控评分系统

基于工业级设计的风控评分系统，采用离线与在线分离架构。

## 项目结构

```
risk-assessment/
├── generate_mock_data.py      # 生成模拟外部API数据（模拟）
├── clean_blacklist.py         # 清洗黑名单数据
├── clean_user_features.py     # 清洗用户特征数据
├── load_to_mysql.py           # 自动建库建表并写入数据
├── train_scoring_model.py     # 模型训练（分析CSV文件）
├── app/
│   ├── api/
│   │   ├── models.py          # Pydantic数据模型
│   │   └── routes.py          # FastAPI路由
│   ├── core/
│   │   ├── credit_scorecard.py # 信用评分卡
│   │   ├── fraud_engine.py    # 反欺诈引擎（模拟）
│   │   ├── fusion.py          # 数据融合
│   │   ├── identity_engine.py # 身份引擎（模拟）
│   │   ├── limit_engine.py    # 额度引擎
│   │   └── pipeline.py        # 评分管道
│   ├── services/
│   │   └── crawler.py         # 征信数据爬取服务（模拟）
│   └── utils/
│       └── __init__.py        # 工具函数
├── data/
│   ├── raw/                   # 原始数据（模拟外部API）
│   │   ├── raw_blacklist.csv  # 原始黑名单（模拟）
│   │   └── raw_user_features.csv  # 原始用户特征（模拟）
│   ├── cleaned/               # 清洗后数据
│   │   ├── cleaned_blacklist.csv
│   │   └── cleaned_user_features.csv
│   └── training_data.csv      # 历史训练数据（可选）
├── output/
│   └── scoring_rules.json     # 评分规则文件
├── main.py                    # 应用入口
└── requirements.txt           # 依赖项
```

## 配置说明

创建 `.env` 文件：

```env
DB_HOST=47.109.109.231
DB_PORT=3306
DB_USER=admin
DB_PASSWORD=admin
DB_NAME=credit_data_db
```

## 使用流程

### 1. 生成模拟数据

```bash
python generate_mock_data.py
```

生成原始数据文件到 `data/raw/` 目录。

### 2. 清洗数据

```bash
python clean_blacklist.py
python clean_user_features.py
```

清洗后的数据保存到 `data/cleaned/` 目录。

### 3. 写入数据库

```bash
python load_to_mysql.py
```

自动创建数据库和表，并将清洗后的数据写入MySQL。

### 4. 训练模型

```bash
python train_scoring_model.py
```

分析CSV训练数据，训练逻辑回归模型，产出评分规则。

### 5. 启动API服务

```bash
python main.py
```

## API接口

### POST /predict

授信评估接口

### GET /health

健康检查

## 数据库表结构

| 表名                       | 说明         |
| ------------------------ | ---------- |
| `blacklist`              | 黑名单表（一票否决） |
| `user_external_features` | 用户外部特征表    |
| `scoring_rules`          | 评分规则配置表    |

## 依赖安装

```bash
pip install -r requirements.txt
```

## 启动运行步骤

### 1. 创建并激活环境

```
# 删除旧环境（如果存在）
conda remove -n risk-assessment --all -y

# 创建新环境
conda create -n risk-assessment 
python=3.9 -y
conda activate risk-assessment

# 安装依赖
cd 
d:\javacode\RiskLendPro\risk-assessment
pip install -r requirements.txt
```

### 2. 执行数据处理流程

```
# 步骤1: 生成模拟数据（自动创建data文件
夹）
python generate_mock_data.py
# 输出: 原始黑名单数据生成完毕... 原始用
户特征数据生成完毕...

# 步骤2: 清洗数据
python clean_blacklist.py
# 输出: 原始数据行数: xxx → 去重后行数: 
xxx → 清洗后黑名单数据: xxx 条

python clean_user_features.py
# 输出: 原始数据行数: xxx → 去重后行数: 
xxx → 清洗后用户特征数据: xxx 条

# 步骤3: 写入数据库（自动创建数据库和表）
python load_to_mysql.py
# 输出: 数据库检查/创建完成 → 所有表检查/
创建完成 → 写入数据成功

# 步骤4: 训练模型
python train_scoring_model.py
# 输出: 模型训练完成 → 测试准确率: xxx 
→ 评分规则已保存

# 步骤5: 启动API服务
python main.py
# 输出: INFO:     Started server 
process [xxxx] → Uvicorn running on 
http://127.0.0.1:8000
```

### 3. 验证运行成功

检查服务是否启动：

- 打开浏览器访问： <http://localhost:8000/health>
- 返回 {"status": "healthy"} 表示服务正常
  检查数据库数据：

```
# 使用MySQL客户端连接
mysql -h 47.109.109.231 -u admin -p
# 密码: admin

# 查询数据
USE credit_data_db;
SELECT COUNT(*) FROM 
blacklist;      # 查看黑名单数量
SELECT COUNT(*) FROM 
user_external_features;  # 查看用户特
征数量
SELECT * FROM 
scoring_rules;  
```

