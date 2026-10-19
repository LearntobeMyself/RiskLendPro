# B 卡 A3 特征工程与重新训练报告

> 阶段：A3（依赖 A0 / A1 / A2）
> 产物目录：`risk-assessment/output/offline_runs/`
> 特征目录（唯一事实来源）：`risk-assessment/output/b_card_feature_catalog.json`

## 1. 结论摘要

| 项 | 结论 |
|---|---|
| 离线与在线特征口径 | **已统一**：离线 `build_asof_features.py` 与在线 `BCardFeatureServiceImpl` 对同一份原始还款数据产出逐字段一致的 as-of 特征，由 Java 测试 `BCardOfflineFeatureConsistencyTest` 守护 |
| 时间外验证 | **已完成**：按 vintage（as_of_date）切分，禁止随机切分；训练=较早 vintage、验证=中间、OOT=最新 |
| 模型对比 | **已完成**：WOE+LR 可解释基线 + 树模型挑战者（按可用性降级，本次为 sklearn GradientBoosting） |
| 指标完整性 | **已完成**：AUC / KS / Lift@10% / PR-AUC / Brier / 分期坏账率 / 分数 PSI / 特征 PSI / 校准表 |
| 可重复性 | **已完成**：两次训练模型哈希与全部指标完全一致 |
| **真实数据训练与上线** | **尚未完成（数据阻断）**：线上 `b_card_feature_snapshot` 还没有 `sample_status=LABELED` 的成熟样本，脚本会以退出码 2 明确失败且不产出任何可部署规则 |

## 2. 特征目录与缺失值语义

特征目录见 `output/b_card_feature_catalog.json`，共 35 项：

- **28 项在线可用**（`available_online=true`）——与在线 `BCardFeatureVector` 字段一一对应，是唯一允许入模的集合；
- **7 项离线独有**（3/6 个月窗口等，`available_online=false`）——本阶段只用于 IV 与稳定性分析，**禁止入模**，因为在线上无法复现会造成线上线下不一致。

覆盖的特征族：贷款结构（笔数/在贷/结清/逾期）、DPD（当前/12 个月最大/均值/逾期次数）、还款行为（准时率、部分还款率、已还期次数）、额度（总额度/已用/剩余/使用率）、金额（欠款、未结清、放款金额与趋势）、时间（账龄、最近逾期距今天数、近 3/6 个月新增放款）。

**缺失值语义（统一定义，禁止离线用中位数、线上用 0）**：

| 类型 | 语义 |
|---|---|
| 数值 | 缺失 = `0.0` |
| 布尔 | 缺失 = `false` |
| `daysSinceLastOverdue` | 无逾期历史 = `null`（训练时按 0 处理） |

所有特征只允许使用 `asOfDate` 当日及以前的数据，窗口同时受 `asOfDate` 上界约束。

## 3. 数据要求与导出方法

训练数据必须来自 A2 产出的 as-of 快照，导出为 CSV：

```sql
SELECT user_id, as_of_date, feature_version, sample_status, label_version,
       label_30dpd_6m, label_90dpd_12m, feature_json
FROM b_card_feature_snapshot
WHERE sample_status = 'LABELED' AND label_version = 'LBL_V1';
```

导出文件放到 `risk-assessment/data/asof/b_card_asof_snapshots.csv`（UTF-8 带表头），然后：

```bash
python train_b_card_model.py --dataset snapshot
python evaluate_b_card_model.py --dataset snapshot
```

脚本会强制校验：`sample_status=LABELED`、`label_version=LBL_V1`、两个标签均非空，且 vintage ≥ 3 个；不满足直接失败退出，不产出规则。

**流程验证数据集**：`--dataset fixture` 使用 `data/fixture/b_card_asof_training_fixture.csv`（合成数据，`deployable=false`），只用于验证代码与筛选逻辑，**任何情况下都不得作为上线依据**，且不会写入 `output/b_scoring_rules.json`。

## 4. 切分与特征筛选

- **时间外切分**：按 `as_of_date` 升序，前 60% vintage 作训练、接下来 20% 作验证、最新 20% 作 OOT；不使用随机切分。
- **四道筛选**：IV（≥0.02）→ 单调性（|Spearman| ≥0.6）→ 相关性（>0.8 共线剔除，保留 IV 高者）→ 稳定性（特征 PSI ≤0.25）。
- 入模特征还会被断言必须属于「在线可用集合」，否则直接报错。

## 5. 本次流程验证结果（fixture，非上线依据）

数据集 1320 行 / 6 个 vintage；切分：train 4 个、valid 1 个、oot 1 个。

| 指标 | LR 基线 (WOE+LR) | 树模型挑战者 (GradientBoosting) |
|---|---|---|
| OOT AUC | 0.7925 | 0.7506 |
| OOT KS | 0.4773 | — |
| OOT Lift@10% | 2.95 | — |

稳定性：分数 PSI（train vs OOT）= 0.0256，最大特征 PSI = 0.0856（均远低于 0.1 警戒线）。

筛选结果：28 个候选中 9 个入模；剔除原因分布——恒定退化特征 3 个（`activeLoanCount`、`repaidLoanCount`、`recentNewLoanCount180d`）、纯噪声 1 个（`recentNewLoanCount90d`）、共线 5 个（`cappedUtilizationRate`/`usedLimit` 与 `utilizationRate`、`maxDpd12m`/`avgDpd12m` 与 `currentMaxDpd`）、IV 或单调性不足若干。

## 6. 产物与模型哈希

`output/offline_runs/<dataset>/`：

| 文件 | 内容 |
|---|---|
| `config.json` | 数据集、数据版本、特征版本、标签版本、切分、随机种子、sklearn 版本、模型哈希 |
| `model_artifact.json` | WOE 定义、系数、截距、`deployable` 标记 |
| `metrics.json` | 训练/验证/OOT 分期指标 + 校准表 |
| `feature_selection.json` | 每个候选特征的 IV/单调性/PSI/入模原因 |
| `oot_report.json` | 独立评估脚本产出的 OOT/PSI/校准报告 |
| `model_hash.txt` | 模型哈希（对 `model_artifact.json` 规范化后取 SHA-256） |

本次 fixture 运行模型哈希：`63513d8b381de7f873499f7876acf280d41cf56ded3ed018f458649b8071ed5e`。

## 7. 验证方式

```bash
cd risk-assessment
python tests/run_a3_checks.py     # 6 项检查，报告写入 output/a3_check_report.json
```

Java 侧（离线/在线特征一致性）：

```bash
mvnw -pl services/risk-service -am test -Dtest=BCardOfflineFeatureConsistencyTest
```

基准数据：`data/fixture/asof_consistency_fixture.json`（原始还款数据）与
`output/b_card_offline_features_golden.csv`（离线构建结果基准）。

## 8. 剩余风险与阻断项

1. **【阻断】缺少真实成熟样本**：线上快照表尚无 `LABELED` 样本（A2 的表刚建立、标签需 6/12 个月成熟），因此真实数据训练、真实 OOT 指标、影子运行门槛都还无法确认。A3 的代码与验证已完成，但"模型效果达到影子门槛"这条验收标准**要等数据积累**。
2. **3/6 个月窗口特征尚未在线化**：目录中标记 `available_online=false`，需在 A2 的在线特征向量中增加对应字段后才能入模，否则会破坏线上线下一致性；本阶段按规则排除在模型外。
3. **当前环境依赖较旧**：本机 Python 为 3.7 / pandas 0.23 / sklearn 0.19，无 LightGBM / XGBoost / HistGradientBoosting，因此树模型挑战者降级为 sklearn `GradientBoosting`。仓库 `requirements.txt` 声明的是 pandas≥2.0 / sklearn≥1.3，建议在标准环境重跑以获得 HistGradientBoosting 或 LightGBM 的对比结论。
4. **候选模型尚未接入在线链路**：本阶段产物 `deployable=false`，且刻意不写 `output/b_scoring_rules.json`。把 as-of 特征接入在线评分（V1 目前读 `user_behavior_features` 的 HC 静态特征）属于 A4 的范围。
5. **PSI 阈值 0.25 / 单调性 0.6 / IV 0.02 为经验值**，建议由风控确认后再固化为正式准入标准。
6. **数据版本哈希**当前基于训练样本 `user_id` 集合生成，建议后续改为对「快照表版本 + 导出时间」做哈希，以便更精确地复现历史训练。