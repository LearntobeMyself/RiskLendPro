# RiskLendPro 风控评分系统 — 项目总览

**文档版本**：v4.0（Home Credit A/B 卡 + 扣子黑名单同步）  
**适用系统**：RiskLendPro 贷前/贷后风控

---

## 一、项目概述

### 1.1 项目定位

**RiskLendPro** 是基于 **Java + Python** 的风控评分系统，采用 **离线训练 + 在线评分** 分离架构：

- **Python**：Home Credit 数据清洗、A/B 卡模型训练、MySQL 批量入库
- **Java**：Spring Boot 在线风控、贷款业务、管理员审批
- **扣子平台**：定时抓取最新失信被执行人名单，清洗后调用 Java API 增量入库

### 1.2 核心目标


| 目标    | 说明                                        |
| ----- | ----------------------------------------- |
| 自动化决策 | A 卡 LR+PDO 自动通过/拒绝/人工；黑名单分级拦截             |
| 数据驱动  | Home Credit parquet 训练，8 维 A 卡 + B 卡行为模型  |
| 可解释性  | `scoreDetails` 分项贡献、`blacklistCheck` 命中级别 |
| 名单时效  | 扣子 Cron + `/sync/blacklist` 增量更新失信名单      |


### 1.3 技术架构

```text
┌─────────────────────────────────────────────────────────────────┐
│                    离线处理层（risk-assessment/）                 │
├─────────────────────────────────────────────────────────────────┤
│  parquet/CSV → clean_* → train_*_model → JSON → load_to_mysql   │
└────────────────────────────┬────────────────────────────────────┘
                             │ 写入 credit_data_db
┌────────────────────────────┼────────────────────────────────────┐
│  扣子 Cron 工作流           │                                    │
│  温州公开 CSV → 清洗 → POST /api/v1/sync/blacklist ──────────────┤
└────────────────────────────┴────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    在线服务层（Spring Boot）                      │
├─────────────────────────────────────────────────────────────────┤
│  读规则/特征 → 黑名单 → 验真 → A卡评分 → 决策/人工/B卡贷后        │
│  RiskLendPro（业务库） + credit_data_db（征信库）                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 二、数据层设计

### 2.1 数据源


| 数据                              | 来源                      | 用途      |
| ------------------------------- | ----------------------- | ------- |
| `home_credit_train_min.parquet` | Kaggle HC 社区精简版（~108MB） | A/B 卡训练 |
| `blacklist.csv`                 | 温州失信被执行人公开数据            | 黑名单全量清洗 |
| 扣子定时抓取                          | 同上公开数据源                 | 黑名单增量同步 |


**已废弃**：Lending Club `loan.csv` 训练链路，不得与 HC 推理混用。

### 2.2 双数据库


| 库                | 主要表                                                                                                        | 说明                  |
| ---------------- | ---------------------------------------------------------------------------------------------------------- | ------------------- |
| `RiskLendPro`    | `user`, `risk_assessment`, `loan`, `repayment_`*                                                           | 业务主库                |
| `credit_data_db` | `user_external_features`, `scoring_rules`, `blacklist`, `user_behavior_features`, `behavior_scoring_rules` | Python/扣子写入，Java 读取 |


### 2.3 黑名单入库双通道


| 通道  | 场景    | 入口                                               |
| --- | ----- | ------------------------------------------------ |
| 全量  | 开发、重导 | `clean_blacklist.py` + `load_to_mysql.py`        |
| 增量  | 生产定时  | 扣子 → `POST /api/v1/sync/blacklist`（永久 API Token） |


详见 `[csv数据来源和数据库表设计.md](csv数据来源和数据库表设计.md)`。

---

## 三、评分规则体系

### 3.1 A 卡（贷前）


| 项    | 说明                                              |
| ---- | ----------------------------------------------- |
| 模型   | 逻辑回归 + StandardScaler + PDO                     |
| 特征   | 8 维（3 申请 + 5 第三方）                               |
| 分数   | 350–950                                         |
| 阈值   | 约 788 自动通过 / 642 人工（以 `scoring_rules.json` 为准）  |
| 规则文件 | `output/scoring_rules.json` → `scoring_rules` 表 |


### 3.2 B 卡（贷后行为）


| 项    | 说明                                                         |
| ---- | ---------------------------------------------------------- |
| 模型   | WOE + LR + PDO                                             |
| 特征   | parquet 中 `inst_*` / `pos_*` 等行为列                          |
| 规则文件 | `output/b_scoring_rules.json` → `behavior_scoring_rules` 表 |
| Java | `BehaviorScoreEngineImpl`                                  |


### 3.3 黑名单匹配（非 LR）


| 级别  | 条件            | 结果    |
| --- | ------------- | ----- |
| L3  | 姓名 + 地域 + 出生年 | 系统拒绝  |
| L2  | 姓名 + 地域       | 强制人工  |
| L1  | 仅姓名           | 弱匹配打标 |


---

## 四、核心业务流程

贷前 A 卡三道闸（详见 `[详细风控流程.md](详细风控流程.md)`）：

1. 准入校验
2. 黑名单分级
3. 收入/身份验真
4. LR+PDO 评分（须命中 `user_external_features`）
5. 规则 override（L2 黑名单等强制人工）
6. 管理员终审（可选）

---

## 五、API 概览

**基础路径**：`http://host:8080/api/v1`


| 类型    | 路径示例                                     | 鉴权         |
| ----- | ---------------------------------------- | ---------- |
| 用户    | `/auth/login`, `/risk/assessment/submit` | JWT        |
| 管理员   | `/admin/risk/approve`                    | Admin JWT  |
| 黑名单同步 | `/sync/blacklist`                        | 永久静态 Token |


完整定义见 `[接口文档.md](接口文档.md)`。

---

## 六、运行指南

### 6.1 Python 离线（Conda）

```bash
cd risk-assessment
conda activate 你的环境名
pip install -r requirements.txt

python clean_blacklist.py
python clean_user_features.py
python clean_behavior_features.py   # B 卡
python train_scoring_model.py       # A 卡
python train_b_card_model.py        # B 卡
python load_to_mysql.py
python score_demo_users.py          # 可选，校准测试用例
```

### 6.2 Java 在线

```bash
cd d:\javacode\RiskLendPro
mvn spring-boot:run
```

### 6.3 扣子黑名单同步

配置环境变量 `SPRING_BASE_URL`、`BLACKLIST_SYNC_TOKEN`，Cron 触发工作流：下载 CSV → 清洗 → 循环 POST `/api/v1/sync/blacklist`。

详见 `[运行说明.md](运行说明.md)` 第八、九章。

---

## 七、参考文档索引


| 文档                                             | 内容                       |
| ---------------------------------------------- | ------------------------ |
| `[项目总览.md](项目总览.md)`                           | 本文件，架构总览                 |
| `[csv数据来源和数据库表设计.md](csv数据来源和数据库表设计.md)`       | 数据契约、表结构、扣子模板            |
| `[运行说明.md](运行说明.md)`                           | Conda 步骤、联调、扣子配置         |
| `[HomeCredit评分卡使用说明.md](HomeCredit评分卡使用说明.md)` | A 卡训练解读、scoring_rules 详解 |
| `[详细风控流程.md](详细风控流程.md)`                       | 贷前三道闸、离线/增量数据流           |
| `[接口文档.md](接口文档.md)`                           | REST API、sync 黑名单        |
| `[java端实体类与数据库设计.md](java端实体类与数据库设计.md)`       | 主库 + credit_data_db 实体   |


---

## 八、版本历史


| 版本       | 说明                                        |
| -------- | ----------------------------------------- |
| v1.x     | Lending Club + 规则加减分（已废弃）                 |
| v2.x     | Home Credit A 卡 LR+PDO                    |
| v3.x     | 三道闸、补充材料、B 卡                              |
| **v4.0** | 文档对齐当前 Python 流水线；扣子定时同步黑名单；删除 FastAPI 描述 |


---

**生成时间**：2026年6月