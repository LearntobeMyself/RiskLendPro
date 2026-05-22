# RiskLendPro 数据来源与数据库表设计

本文档描述风控系统使用的三类数据：**Home Credit 训练宽表**、**模拟第三方征信窄表**、**失信被执行人黑名单**。全链路（Python 训练 + MySQL + Java 推理）已统一为 **Home Credit（HC）**，不再使用 Lending Club 训练。

---

## 一、总览

| 数据 | 文件/表 | 用途 |
|------|---------|------|
| HC 训练宽表 | `risk-assessment/data/raw/home_credit_train_min.parquet` | A 卡 LR 训练、特征工程参考 |
| 模拟第三方征信 | `credit_data_db.user_external_features` | Java 推理时第三方特征 + 验真对照 |
| 黑名单 | `credit_data_db.blacklist` | 贷前拦截 / 人工复核 |

```text
home_credit_train_min.parquet
        ├─► train_scoring_model.py  →  output/scoring_rules.json
        └─► clean_user_features.py →  cleaned_user_features.csv → load_to_mysql.py
```

---

## 二、Home Credit 训练宽表（`home_credit_train_min.parquet`）

### 2.1 来源与规模

| 项 | 说明 |
|----|------|
| 竞赛 | [Kaggle Home Credit Default Risk（2018）](https://www.kaggle.com/competitions/home-credit-default-risk) |
| 托管 | [HuggingFace jamirc/home_credit_default_risk](https://huggingface.co/datasets/jamirc/home_credit_default_risk) |
| 本地文件 | `risk-assessment/data/raw/home_credit_train_min.parquet`（社区精简版，约 108MB） |
| 粒度 | 一行 = 一笔贷款申请（`SK_ID_CURR`） |
| 列数 | 约 **318 列**（申请原始 + bureau/prev/inst/cc/pos 聚合 + 竞赛衍生，非官方 122 列） |

### 2.2 宽表列分组（目录）

| 分组 | 列名前缀/代表列 | 业务含义 | 本项目 |
|------|----------------|----------|--------|
| 主键与标签 | `SK_ID_CURR`, `TARGET` | 申请 ID；是否还款困难（0/1） | `TARGET` 仅作训练标签 |
| 申请原始 | `DAYS_BIRTH`, `DAYS_EMPLOYED`, `AMT_INCOME_TOTAL`, `AMT_CREDIT`, `NAME_EDUCATION_TYPE`, `OCCUPATION_TYPE`, `FLAG_OWN_CAR`, `AMT_REQ_CREDIT_BUREAU_*`, `EXT_SOURCE_1/2/3` 等 | 进件申请表与竞赛方汇总征信查询/外部分 | 见下文「8 维 LR」「验真列」 |
| 外部机构信贷 | `bureau_*`, `active_loans_count`, `active_debt_sum` | 其他金融机构历史（`bureau.csv` 聚合） | `active_loans_count` 入 LR；其余本期文档化不入模 |
| 信用卡 | `cc_*` | 信用卡账单聚合 | 本期不入 LR |
| 分期还款 | `inst_*` | 旧贷还款行为聚合 | 本期不入 LR（非本笔贷后 B 卡） |
| POS 现金贷 | `pos_*` | POS 合同 DPD 聚合 | 本期不入 LR |
| 本机构历史申请 | `prev_*`, `prev_refused` | 历史申请/被拒 | `prev_refused` 入验真/规则 |
| 简单衍生 | `CREDIT_INCOME_RATIO`, `AGE_YEARS`, `EXT_SOURCE_MEAN` 等 | 比率与统计 | 本期不入 LR |
| 深度衍生 | `EXT_2_pow2`, `final_score`, `application_risk_score`, `risk_score_composite` 等 | 竞赛特征工程 / 疑似综合分 | **禁止入模**（泄漏或与 8 维契约不一致） |

### 2.3 逻辑回归入模：8 维（训练键 = Java 键）

| 逻辑角色 | 训练/推理键名 | Parquet 源列 | 说明 |
|----------|---------------|-------------|------|
| 申请 | `days_birth` | `DAYS_BIRTH` | 出生距申请日天数（常为负） |
| 申请 | `days_employed` | `DAYS_EMPLOYED` | 入职距申请日天数 |
| 申请 | `amt_income_total` | `AMT_INCOME_TOTAL` | 年收入；推理用**申请表月收入×12** |
| 第三方 | `ext_source_2` | `EXT_SOURCE_2` | 外部权威综合分 A |
| 第三方 | `ext_source_3` | `EXT_SOURCE_3` | 外部权威综合分 B |
| 第三方 | `amt_req_credit_bureau_mon` | `AMT_REQ_CREDIT_BUREAU_MON` | 近 1 月征信查询次数 |
| 第三方 | `amt_req_credit_bureau_week` | `AMT_REQ_CREDIT_BUREAU_WEEK` | 近 1 周征信查询次数 |
| 第三方 | `active_loans_count` | `active_loans_count` | 活跃贷款数（bureau 聚合） |

- 标签：`TARGET` → 训练内部 `defaulted`（**不得**作为特征）。
- 预处理：第三方连续列缺失均值填充；`StandardScaler` 后 LR；权重写入 `scoring_rules.json` 的 `feature_weights`、`feature_scaler`。

### 2.4 第三方模拟征信：训练中使用的 5 列

以下列**必须**来自 `user_external_features`，且与训练权重一一对应（不得 Java 硬编码未训练字段参与 LR）：

| 训练键名 | Parquet 源列 | 库表列 |
|----------|-------------|--------|
| `ext_source_2` | `EXT_SOURCE_2` | `ext_source_2` |
| `ext_source_3` | `EXT_SOURCE_3` | `ext_source_3` |
| `amt_req_credit_bureau_mon` | `AMT_REQ_CREDIT_BUREAU_MON` | `credit_bureau_mon` |
| `amt_req_credit_bureau_week` | `AMT_REQ_CREDIT_BUREAU_WEEK` | `credit_bureau_week` |
| `active_loans_count` | `active_loans_count` | `active_loans_count` |

### 2.5 验真 / 规则层（进库、不进 LR）

| Parquet 源列 | 库表列 | 用途 |
|-------------|--------|------|
| `SK_ID_CURR` | `sk_id_curr` | Home Credit 申请 ID |
| （清洗生成） | `id_card` | 与主库 `user.id_card` 一致；Java **优先** `selectByIdCard` 关联 |
| `AMT_INCOME_TOTAL` | `amt_income_total` | 收入验真：对比用户自填月收入区间 |
| `DAYS_BIRTH` | `days_birth` | 年龄验真对照 |
| `DAYS_EMPLOYED` | `days_employed` | 工龄验真对照 |
| `DAYS_LAST_PHONE_CHANGE` | `days_last_phone_change` | 手机稳定性 |
| `FLAG_OWN_CAR` | `flag_own_car` | 资产验真 |
| `NAME_EDUCATION_TYPE` | `education_type` | 学历验真 |
| `OCCUPATION_TYPE` | `occupation_type` | 职业展示/策略 |
| `prev_refused` | `prev_refused_count` | 历史被拒（宽表列名为 `prev_refused`） |
| `TARGET` | `target` | 回测标签，不入 LR |

### 2.6 明确排除（勿用于 LR）

`final_score`、`application_risk_score`、`risk_score_composite`、`net_score` 及大量 `EXT_*` 多项式、`inst_*` / `cc_*` / `pos_*` 等竞赛衍生列。

---

## 三、模拟第三方征信表（`user_external_features`）

**数据库**：`credit_data_db`（与主业务库分离）

| 列名 | 类型 | 对应 HC / 用途 |
|------|------|----------------|
| `id` | BIGINT | 主键 |
| `sk_id_curr` | BIGINT | `SK_ID_CURR` |
| `id_card` | VARCHAR(20) | 清洗 CSV 的 `id_card`；Java `selectByIdCard` 关联 |
| `days_birth` | INT | 验真 |
| `days_employed` | INT | 验真 |
| `amt_income_total` | DECIMAL | 验真（后台收入） |
| `credit_bureau_week` | INT | LR 第三方 |
| `credit_bureau_mon` | INT | LR 第三方 |
| `days_last_phone_change` | INT | 规则/展示 |
| `active_loans_count` | INT | LR 第三方 |
| `ext_source_2` | DECIMAL | LR 第三方 |
| `ext_source_3` | DECIMAL | LR 第三方 |
| `flag_own_car` | TINYINT | 验真 |
| `occupation_type` | VARCHAR | 展示 |
| `education_type` | VARCHAR | 验真 |
| `target` | TINYINT | 回测 |
| `prev_refused_count` | INT | 规则/拦截 |
| `data_source` | VARCHAR | 如 `Home Credit` |
| `updated_at` | DATETIME | 更新时间 |

实体类：[`UserExternalFeatures.java`](src/main/java/org/example/risklendpro/entity/credit/UserExternalFeatures.java)

---

## 四、黑名单数据

### 4.1 来源

**浙江温州市失信被执行人公开数据**（约 2497 条）

- [CSV 下载](https://data.wenzhou.gov.cn/jdop_front/detail/data.do?iid=13341)

### 4.2 清洗后字段（`blacklist` 表）

| 字段 | 说明 |
|------|------|
| `name` | 被执行人姓名（匹配维度 1） |
| `area_code` | 地区编码（维度 2） |
| `birth_year` | 出生年份（维度 3） |
| `case_no` | 案号 |
| `court_name` | 执行法院 |
| `duty_status` | 履行情况 |
| `behavior_details` | 失信行为描述 |
| `risk_level` | HIGH / MEDIUM / LOW |

---

## 五、评分规则输出（`scoring_rules.json` / `scoring_rules` 表）

训练脚本输出 JSON，入库后供 Java 读取。逐项公式、8 维特征系数、PDO 参数与决策阈值的**可读解释**见 [`risk-assessment/HomeCredit评分卡使用说明.md` 第 10 章](risk-assessment/HomeCredit评分卡使用说明.md#10-评分规则解读scoring_rulesjson)。

| 节点 | 说明 |
|------|------|
| `model_type` | `hc_lr_standardized` |
| `feature_weights` | 8 维 LR 系数（须含 `ext_source_2` 等） |
| `feature_scaler` | 每特征 mean/scale |
| `intercept` | 截距 |
| `scorecard` | PDO 评分卡参数 |
| `thresholds.auto_approve` / `thresholds.manual_review` | 自动通过 / 人工复核阈值（PDO 分；当前 v6.0-hc 为 776 / 642） |
| `training_data.third_party_features` | 第三方 5 列名单 |

---

## 附录 A：Lending Club（已废弃）

> 以下仅作历史对照。当前 `train_scoring_model.py` 默认 `use_hc=True`，**不得**再用 LC 训练而 HC 推理。

| 文件 | 说明 |
|------|------|
| `loan.csv` | 原 P2P 贷款记录 |
| `LCDataDictionary.xlsx` | LC 数据字典 |

原 LC 字段如 `annual_inc`、`inq_last_6mths`、`loan_status` 等已废弃，见 git 历史版本文档。

---

## 附录 B：本地辅助文件

| 文件 | 说明 |
|------|------|
| `risk-assessment/data/cleaned/cleaned_user_features.csv` | 清洗后入库样本（默认每地区 250 条） |
| `risk-assessment/HomeCredit评分卡使用说明.md` | 操作步骤与联调说明 |
