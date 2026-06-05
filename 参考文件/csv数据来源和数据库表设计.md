# RiskLendPro 数据来源与数据库表设计

> **文档版本**：v2.0（对齐 A 卡 **v7.0-hc-woe** / B 卡 **v1.0-b-woe**；以 `risk-assessment/output/*.json` 与 `load_to_mysql.py` 为准）

本文档描述风控系统使用的数据：**Home Credit 训练宽表（A/B 卡）**、**模拟第三方征信窄表**、**失信被执行人黑名单**。全链路（Python 训练 + MySQL + Java 推理）已统一为 **Home Credit（HC）**；A 卡默认 **IV 筛选 + WOE 分箱 + LR + PDO**。

---

## 一、总览

| 数据 | 文件/表 | 用途 |
|------|---------|------|
| HC 训练宽表 | `risk-assessment/data/raw/home_credit_train_min.parquet` | A 卡 / B 卡 LR 训练 |
| 模拟第三方征信 | `credit_data_db.user_external_features` | Java A 卡推理第三方特征 + 验真 |
| A 卡规则 | `credit_data_db.scoring_rules` | Java 读取 PDO 评分卡 |
| B 卡行为特征 | `credit_data_db.user_behavior_features` | Java B 卡贷后评分 |
| B 卡规则 | `credit_data_db.behavior_scoring_rules` | Java B 卡阈值 |
| 黑名单 | `credit_data_db.blacklist` | 贷前拦截 / 人工复核 |

```text
home_credit_train_min.parquet
        ├─► feature_engineering_hc.py + woe_binning.py
        ├─► train_scoring_model.py      → output/scoring_rules.json（默认 hc_woe_lr）
        ├─► train_b_card_model.py       → output/b_scoring_rules.json
        └─► clean_user_features.py      → cleaned_user_features.csv → load_to_mysql.py

data/raw/blacklist.csv
        └─► clean_blacklist.py          → cleaned_blacklist.csv   → load_to_mysql.py（全量）

扣子平台 Cron 工作流（增量）
        └─► 抓取温州公开数据 → 清洗 → POST /api/v1/sync/blacklist → blacklist 表
```

### 黑名单入库双通道

| 通道 | 适用场景 | 入口 | 表操作 |
|------|----------|------|--------|
| **全量** | 开发、重导、首次部署 | `clean_blacklist.py` + `load_to_mysql.py` | `DROP` 后重建 `blacklist` 并批量 INSERT |
| **增量** | 生产定时更新 | 扣子工作流 → `POST /api/v1/sync/blacklist` | 单条 INSERT；`(name, area_code, birth_year)` 去重 |

---

## 二、Home Credit 训练宽表

### 2.1 来源与规模

| 项 | 说明 |
|----|------|
| 竞赛 | [Kaggle Home Credit Default Risk（2018）](https://www.kaggle.com/competitions/home-credit-default-risk) |
| 本地文件 | `risk-assessment/data/raw/home_credit_train_min.parquet`（社区精简版，约 108MB） |
| 粒度 | 一行 = 一笔贷款申请（`SK_ID_CURR`） |
| 列数 | 约 **318 列** |

### 2.2 A 卡默认：IV 筛选 + WOE + LR（v7.0-hc-woe）

训练链路：`feature_engineering_hc.py` → `woe_binning.py` → `train_scoring_model.py`（默认 `use_woe=True` → `train_hc_woe_scoring_model`）。

| 逻辑角色 | 训练/推理键名 | 主要来源 | 说明 |
|----------|---------------|----------|------|
| 第三方 | `ext_source_3` | `EXT_SOURCE_3` / 外部表 | IV 最高，WOE 入模 |
| 第三方 | `ext_source_2` | `EXT_SOURCE_2` / 外部表 | 外部权威综合分 A |
| 申请 | `edu_mid` | 学历档位 one-hot | 申请表 `education` 映射 |
| 第三方 | `phone_change_days` | `DAYS_LAST_PHONE_CHANGE` | 手机换号稳定性 |
| 申请 | `age_years` | `DAYS_BIRTH` 或生日推算 | 年龄 |
| 第三方 | `prev_refused_count` | HC 衍生 / 外部表 | 历史被拒次数 |
| 申请 | `gender_male` | `CODE_GENDER` / 申请表 | 性别男=1 |

- 标签：`TARGET`（1 = 还款困难），**不得**作为特征。
- 预处理：**WOE 分箱编码** → LR → PDO（350–950）；规则写入 `output/scoring_rules.json`（`model_type=hc_woe_lr`）。
- IV 筛选：候选 19 维，IV ≥ 0.02 入模；当前选中 7 维（见 JSON `feature_selection_report`）。

### 2.3 第三方模拟征信：最小入库集 + WOE 依赖列

**最小 5 列（历史 LR 契约，仍须入库）**

| 训练键名 | 库表列 |
|----------|--------|
| `ext_source_2` | `ext_source_2` |
| `ext_source_3` | `ext_source_3` |
| `amt_req_credit_bureau_mon` | `credit_bureau_mon` |
| `amt_req_credit_bureau_week` | `credit_bureau_week` |
| `active_loans_count` | `active_loans_count` |

**当前 v7 WOE 模型额外依赖（须入库或申请表提供）**

| 训练键名 | 库表列 | 说明 |
|----------|--------|------|
| `phone_change_days` | `days_last_phone_change` | WOE 入模 |
| `prev_refused_count` | `prev_refused_count` | WOE 入模 + 规则拦截 |

### 2.4 验真 / 规则 / WOE 候选（进库，当前 IV 未入模）

| Parquet 源列 / 清洗列 | 库表列 | 用途 |
|----------------------|--------|------|
| `SK_ID_CURR` | `sk_id_curr` | HC 申请 ID |
| （清洗生成） | `id_card` | 与主库 `user.id_card` 一致 |
| `AMT_INCOME_TOTAL` | `amt_income_total` | 收入验真 |
| `DAYS_BIRTH` | `days_birth` | 年龄验真 |
| `DAYS_EMPLOYED` | `days_employed` | 工龄验真 |
| `prev_refused` | `prev_refused_count` | 规则 + WOE 入模 |
| `TARGET` | `target` | 回测标签 |
| `gender_male` | `gender_male` | WOE 入模（申请表也可提供） |
| `married` | `married` | WOE 候选（IV<0.02，未入模） |
| `own_realty` | `own_realty` | WOE 候选 / 验真 |
| `employment_stable` | `employment_stable` | WOE 候选 |
| `credit_income_ratio` | `credit_income_ratio` | WOE 候选 |
| `cc_utilization` | `cc_utilization` | WOE 候选 |
| `loan_overdue_max_6m` | `loan_overdue_max_6m` | WOE 候选 |
| `FLAG_OWN_CAR` / `has_car` | `flag_own_car` | 验真 |

---

## 三、模拟第三方征信表（`user_external_features`）

**数据库**：`credit_data_db`

| 列名 | 类型 | 用途 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `sk_id_curr` | BIGINT | `SK_ID_CURR` |
| `id_card` | VARCHAR(20) | Java `selectByIdCard` 关联键 |
| `days_birth` | INT | 验真：核对年龄 |
| `days_employed` | INT | 验真：核对工作年限 |
| `amt_income_total` | DECIMAL(15,2) | 验真：后台收入 |
| `credit_bureau_week` | INT | 第三方征信：近 1 周查询次数 |
| `credit_bureau_mon` | INT | 第三方征信：近 1 月查询次数 |
| `days_last_phone_change` | INT | WOE 入模（`phone_change_days`） |
| `active_loans_count` | INT | 第三方：活跃贷款数 |
| `ext_source_2` | DECIMAL(10,6) | WOE 入模 |
| `ext_source_3` | DECIMAL(10,6) | WOE 入模 |
| `flag_own_car` | TINYINT(1) | 验真：是否有车 |
| `gender_male` | TINYINT(1) | WOE 入模 / 回测 |
| `married` | TINYINT(1) | WOE 候选 / 回测 |
| `own_realty` | TINYINT(1) | WOE 候选 / 验真 |
| `employment_stable` | TINYINT(1) | WOE 候选 |
| `credit_income_ratio` | DECIMAL(12,4) | WOE 候选 |
| `cc_utilization` | DECIMAL(12,4) | WOE 候选 |
| `loan_overdue_max_6m` | INT | WOE 候选 |
| `occupation_type` | VARCHAR(50) | 展示 / 验真 |
| `education_type` | VARCHAR(50) | 展示 / 验真 |
| `target` | TINYINT(1) | 回测标签（0=正常，1=逾期） |
| `prev_refused_count` | INT | WOE 入模 + 规则拦截 |
| `data_source` | VARCHAR(50) | 如 `Home Credit` |
| `updated_at` | DATETIME | 更新时间 |

实体：[`UserExternalFeatures.java`](../src/main/java/org/example/risklendpro/entity/credit/UserExternalFeatures.java)。建表 DDL 见 [`load_to_mysql.py`](../risk-assessment/load_to_mysql.py) 第 68–100 行。

---

## 四、黑名单数据

### 4.1 来源

**浙江温州市失信被执行人公开数据**（约 2497 条）

- [CSV 下载](https://data.wenzhou.gov.cn/jdop_front/detail/data.do?iid=13341)
- 本地副本：`risk-assessment/data/raw/blacklist.csv`

### 4.2 字段映射（原始 → 清洗/API → 数据库）

| 原始 CSV 列 | 清洗 CSV / API 字段 | 数据库列 | 说明 |
|------------|---------------------|----------|------|
| 被执行人姓名/名称 | `name` | `name` | 匹配维度 1，支持 `*` 通配 |
| 执行法院 | `area_code` / `areaCode` | `area_code` | 法院名映射行政区划码（见 `clean_blacklist.py`） |
| 出生日期 | `birth_year` / `birthYear` | `birth_year` | 提取前 4 位年份，匹配维度 3 |
| 案号 | `case_no` / `caseNo` | `case_no` | 人工核对 |
| 执行法院 | `court_name` / `courtName` | `court_name` | 展示 |
| 被执行人的履行情况 | `duty_status` / `dutyStatus` | `duty_status` | 判定风险严重程度 |
| 失信被执行人行为情况 | `behavior_details` / `behaviorDetails` | `behavior_details` | 具体原因 |
| — | `risk_level` / `riskLevel` | `risk_level` | HIGH/MEDIUM/LOW；未传则按履行情况推导 |
| — | — | `created_at` | 服务端写入时间 |
| — | — | `expire_at` | NULL = 永久有效 |
| — | — | `id` | 自增主键 |

**`risk_level` 推导规则**（与 `clean_blacklist.py` / `BlacklistServiceImpl` 一致）：

- `全部未履行` → `HIGH`
- `部分未履行` → `MEDIUM`
- 其他 → `LOW`

**去重键**：`(name, area_code, birth_year)`

### 4.3 Java 匹配级别

| 级别 | 命中条件 | 业务结果 |
|------|----------|----------|
| L3 (FULL) | 姓名 + 地域 + 出生年 | 系统拒绝 |
| L2 (NAME_AREA) | 姓名 + 地域 | 强制人工复核 |
| L1 (NAME_ONLY) | 仅姓名 | 弱匹配，打标继续 |

实体：[`Blacklist.java`](../src/main/java/org/example/risklendpro/entity/credit/Blacklist.java)

---

## 五、A 卡评分规则（`scoring_rules.json` / `scoring_rules` 表）

| 节点 | 说明 |
|------|------|
| `version` | `v7.0-hc-woe` |
| `model_type` | `hc_woe_lr` |
| `features` | 每特征 WOE 分箱：`cuts` / `woe` / `missing_bin` |
| `coefficients` | IV 入模 7 维 LR 系数 |
| `intercept` | 截距 |
| `scorecard` | PDO 参数（350–950；当前 `pdo≈125.08`） |
| `thresholds.auto_approve` / `manual_review` | 当前 **788 / 642** |
| `feature_selection_report` | IV 表与 `selected_features` |
| `model_metrics` | accuracy ≈ 0.661，AUC ≈ 0.714 |

入库时 `load_scoring_rules_to_mysql` 将 `coefficients` 写入表列 `feature_weights`（兼容旧字段名）。Java 按 `model_type` 走 WOE 分支（`buildHcWoeRawFeatureMap` → `woeLookup`）。

逐项解读见 [`output/scoring_rules.md`](../risk-assessment/output/scoring_rules.md)（完整分箱）及 [`HomeCredit评分卡使用说明.md`](HomeCredit评分卡使用说明.md) 第 10 章。

---

## 六、B 卡（贷后行为）数据

### 6.1 流水线

```text
home_credit_train_min.parquet
    → clean_behavior_features.py  → cleaned_behavior_features.csv
    → train_b_card_model.py       → output/b_scoring_rules.json
    → load_to_mysql.py（步骤 6）   → user_behavior_features + behavior_scoring_rules
```

B 卡从 parquet 中选取 `inst_*`、`pos_*` 等贷后行为列，WOE + LR + PDO，与 A 卡规则物理分离。

**当前 v1.0-b-woe 入模 7 维**（`output/b_scoring_rules.json` → `selected_features`）：

`inst_amt_paid_1y`、`inst_dbd_mean_total`、`inst_dbd_mean_1y`、`pos_dpd_mean`、`inst_late_ratio_total`、`inst_partial_payment_ratio`、`pos_dpd_def_mean`

**决策阈值**（PDO 分）：`watch=684.6`，`reduce_limit=547.4`。离线指标：accuracy ≈ 0.610，AUC ≈ 0.571。

### 6.2 表结构

**`user_behavior_features`**

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | BIGINT | 主键 |
| `sk_id_curr` | BIGINT | HC 申请 ID |
| `id_card` | VARCHAR(20) | 与主库关联 |
| `feature_json` | JSON | 上述 7 个 `inst_*`/`pos_*` 键值（见 `clean_behavior_features.py`） |
| `data_source` | VARCHAR | 默认 `Home Credit B-card` |
| `updated_at` | DATETIME | 更新时间 |

**`behavior_scoring_rules`**

| 列 | 类型 | 说明 |
|----|------|------|
| `version` | VARCHAR(32) | 规则版本 |
| `rule_content` | JSON | 完整规则备份 |
| `feature_weights` | JSON | WOE/LR 系数 |
| `scorecard` | JSON | PDO 参数 |
| `intercept` | DECIMAL | 截距 |
| `threshold_watch` | DECIMAL | 观察阈值 |
| `threshold_reduce_limit` | DECIMAL | 降额阈值 |
| `is_active` | TINYINT | 是否激活 |

实体：[`UserBehaviorFeatures.java`](../src/main/java/org/example/risklendpro/entity/credit/UserBehaviorFeatures.java)、[`BehaviorScoringRules.java`](../src/main/java/org/example/risklendpro/entity/credit/BehaviorScoringRules.java)

---

## 七、扣子平台定时同步黑名单（通用模板）

用于生产环境**增量**更新失信名单，无需重跑 `load_to_mysql.py`（避免 DROP 表）。

### 7.1 工作流节点建议

```mermaid
flowchart LR
    cron[Cron定时触发] --> fetch[HTTP抓取温州公开CSV]
    fetch --> parse[解析CSV行]
    parse --> clean[代码节点字段清洗]
    clean --> loop[循环每条记录]
    loop --> post[HTTP POST sync/blacklist]
    post --> log[记录成功跳过失败]
```

| 步骤 | 扣子节点类型 | 说明 |
|------|-------------|------|
| 1 | **定时触发** | Cron，建议每日 02:00 |
| 2 | **HTTP 请求** | 下载温州失信被执行人公开 CSV |
| 3 | **代码** | 复刻 `clean_blacklist.py`：`get_area_code()`、`extract_birth_year()`、去无姓名行 |
| 4 | **循环** | 遍历清洗后记录 |
| 5 | **HTTP 请求** | `POST {SPRING_BASE_URL}/api/v1/sync/blacklist` |
| 6 | **条件分支** | 400 且 message 含「已存在」→ 跳过；401 → 告警；5xx → 重试 |

### 7.2 环境变量（扣子侧配置）

| 变量 | 示例 | 说明 |
|------|------|------|
| `SPRING_BASE_URL` | `http://47.109.109.231:8080` | Java 服务根地址（不含 context-path） |
| `BLACKLIST_SYNC_TOKEN` | 与 `application.yaml` 中 `risk.blacklist-sync.api-token` 一致 | **勿写入公开日志** |

Java 配置项：[`application.yaml`](../src/main/resources/application.yaml) → `risk.blacklist-sync.api-token`

### 7.3 请求示例

```bash
curl -X POST "http://47.109.109.231:8080/api/v1/sync/blacklist" \
  -H "Authorization: Bearer {BLACKLIST_SYNC_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "孙*",
    "areaCode": "110112",
    "birthYear": 1986,
    "caseNo": "（2025）京0112执测L2号",
    "courtName": "北京市通州区人民法院",
    "dutyStatus": "全部未履行",
    "behaviorDetails": "有履行能力而拒不履行生效法律文书确定义务",
    "riskLevel": "HIGH"
  }'
```

### 7.4 与离线清洗的差异

| 项 | 离线 `clean_blacklist.py` | 扣子 + API |
|----|---------------------------|------------|
| 输出 | `cleaned_blacklist.csv` | 直接入库 |
| 去重 | CSV 内 drop_duplicates | API 层 `(name, area_code, birth_year)` 查重 |
| `created_at` | 脚本运行时间 | Java 服务端 `new Date()` |
| 表影响 | 随 `load_to_mysql` DROP 重建 | 仅 INSERT，不 DROP |

---

## 附录 A：Lending Club（已废弃）

当前 `train_scoring_model.py` 默认 HC WOE 训练，**不得** LC 训练 + HC 推理混用。LC 相关文件仅作 git 历史参考。

---

## 附录 C：CSV → DB 字段映射（`cleaned_user_features.csv`）

来源：[`clean_user_features.py`](../risk-assessment/clean_user_features.py) 输出 → [`load_to_mysql.py`](../risk-assessment/load_to_mysql.py) INSERT。

**抽样规则**：从 parquet 去重后随机抽样 **500 行**（`area_code=110112` 与 `310000` 各 250）；`id_card` 由 `area_code + birth_year + 随机月日 + 序号` 生成。

| CSV 列 | DB 列 | 备注 |
|--------|-------|------|
| `sk_id_curr` | `sk_id_curr` | HC 申请 ID |
| `id_card` | `id_card` | Java 关联键 |
| `days_birth` / `age` | `days_birth` | 无 `days_birth` 时用 `-age×365` |
| `days_employed` / `employment_years` | `days_employed` | 无 `days_employed` 时用 `-employment_years×365` |
| `AMT_INCOME_TOTAL` | `amt_income_total` | |
| `credit_query_week` | `credit_bureau_week` | |
| `credit_query_month` | `credit_bureau_mon` | |
| `phone_change_days` | `days_last_phone_change` | WOE 入模 |
| `active_loans_count` | `active_loans_count` | |
| `ext_source_2` | `ext_source_2` | WOE 入模 |
| `ext_source_3` | `ext_source_3` | WOE 入模 |
| `has_car` | `flag_own_car` | |
| `gender_male` | `gender_male` | WOE 入模 |
| `married` | `married` | |
| `own_realty` | `own_realty` | |
| `employment_stable` | `employment_stable` | |
| `credit_income_ratio` | `credit_income_ratio` | |
| `cc_utilization` | `cc_utilization` | |
| `loan_overdue_max_6m` | `loan_overdue_max_6m` | |
| `occupation_type` | `occupation_type` | |
| `education` | `education_type` | |
| `has_default_history` | `target` | 回测标签 |
| `prev_refused_count` | `prev_refused_count` | WOE 入模 + 规则 |
| — | `data_source` | 固定 `Home Credit` |
| — | `updated_at` | 入库时间 |

CSV 中 **`area_code`、`birth_year`、`age`、`employment_years`** 等列用于清洗与生成 `id_card`，不单独入库。

---

## 附录 D：旧版 hc_lr_standardized（可选 fallback）

`train_scoring_model(use_woe=False)` 仍可产出 **8 维 StandardScaler + LR**（`model_type=hc_lr_standardized`，JSON 含 `feature_weights` + `feature_scaler`）。Java [`CreditScoreEngineImpl`](../src/main/java/org/example/risklendpro/service/impl/CreditScoreEngineImpl.java) 按 `model_type` 分支兼容。

**当前仓库默认产物与部署为 WOE v7**（`output/scoring_rules.json` → `hc_woe_lr`）。旧版仅作历史 fallback，详见 [`HomeCredit评分卡使用说明.md`](HomeCredit评分卡使用说明.md) 第 10.6 节对照表。

---

## 附录 B：本地辅助文件

| 文件 | 说明 |
|------|------|
| `risk-assessment/data/cleaned/cleaned_user_features.csv` | A 卡演示样本（约 500 条） |
| `risk-assessment/data/cleaned/cleaned_blacklist.csv` | 黑名单清洗结果 |
| `risk-assessment/data/cleaned/cleaned_behavior_features.csv` | B 卡行为特征 |
| `risk-assessment/output/scoring_rules.json` | A 卡规则 |
| `risk-assessment/output/scoring_rules.md` | A 卡规则解读（完整分箱表） |
| `risk-assessment/output/b_scoring_rules.json` | B 卡规则 |
| `risk-assessment/output/b_scoring_rules.md` | B 卡规则解读（完整分箱表） |
| `risk-assessment/output/demo_user_scores.json` | 测试用例预期分数 |
