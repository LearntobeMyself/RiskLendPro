# A 卡模型与反欺诈优化说明

> 本文回答三个问题：A 卡模型还能怎么优化、当前"黑名单 → 收入校验 → 评分卡"的判断流程是否合理、如何用黑名单 / UserExternalFeatures / B 卡动态做反欺诈与"可能逾期/跑路"的早期预警。
>
> 配套改动已落地并通过测试；离线结果见文末。

---

## 一、现状诊断

### 1.1 A 卡模型（v7.0-hc-woe）

| 现状 | 问题 |
|---|---|
| `train_scoring_model.py` 使用 `train_test_split(random_state=42)` | 随机切分，无法回答"跨时间是否稳定" |
| 概率直接做 PDO 映射 | 概率没有校准含义；Brier 偏高 |
| 阈值在 `scoring_rules.json` 是 `auto_approve=788 / manual_review=642` | 但 `RiskAssessmentServiceImpl` **硬编码 720/580**，展示档位与实际决策可能不一致 |
| 特征以 HC 静态字段为主 | `UserExternalFeatures` 里的多头查询、换号天数、历史拒绝、逾期代理没有系统性进入特征治理 |
| 没有 IV/单调性/相关性/PSI 的统一筛选记录 | 退化/共线特征可能留在模型里 |

### 1.2 当前判断流程

实际代码顺序是：**黑名单 → 收入校验 → 评分卡**（不是先收入）。

**合理的部分**
- 黑名单和收入是强信号，先做硬闸可以节省评分成本；
- 黑名单命中后仍会做"影子评分"写入 Redis，便于事后分析；
- 评分明细写入 Redis（`risk:report:<applyId>`，30 天），管理端可查看。

**可优化的部分**
1. `creditScoreEngine.checkBlacklist` 每次申请都 `selectAll()` 全表扫描黑名单，申请量大时是明显热点；
2. 硬闸是"二值"的，缺少一个**统一的风险分数**把"黑名单 + 多头 + 换号 + 历史拒绝 + 收入偏差"合在一起；
3. 没有申请频率（短时间多次申请）、设备/手机号稳定性等**行为反欺诈**信号；
4. Redis 只存展示报告，没有独立的决策审计表；
5. A 卡阈值来源不唯一（见 1.1）。

### 1.3 反欺诈与"跑路"预警现状

- `UserExternalFeatures` 已有很合适的字段：`creditBureauWeek/Mon`、`daysLastPhoneChange`、`activeLoansCount`、`prevRefusedCount`、`loanOverdueMax6m`、`ccUtilization`、`extSource2/3`；
- B 卡动态已有 `currentMaxDpd / maxDpd12m / onTimeRate12m / daysSinceLastOverdue / utilizationRate`；
- 但两者目前是分开用的：外部特征只进评分卡，B 卡只做额度与 PSI 监控，**没有一个"未来可能逾期/失联"的联合预警**。

---

## 二、优化方案

### 2.1 A 卡模型

1. **时间外验证**：按申请月份做 vintage 切分（前 60% 训练、20% 校准、20% OOT），不再随机切分。
2. **四道筛选**：IV ≥ 0.02 → 单调性 |Spearman| ≥ 0.6 → 相关性 ≤ 0.8 → PSI ≤ 0.25。
3. **双模型对比**：WOE+LR 可解释基线 vs 树模型挑战者。
4. **Platt 校准 + PDO 映射**：让输出概率具备"坏账率"含义。
5. **业务成本阈值**：按坏账损失、收入损失、客户体验、审核容量在概率网格上搜索，不用分位数。
6. **阈值单一来源**（已改代码）：`RiskAssessmentServiceImpl` 改为调用 `CreditScoreEngine.getAutoApproveThreshold()` / `getManualReviewThreshold()`，与规则/DB 保持一致。

> 离线实现：`risk-assessment/train_a_card_governance.py`。产出 `output/a_card_model_report.json` 与候选规则 `output/a_card_scoring_rules_candidate.json`（`deployable=false`，不覆盖线上规则）。

### 2.2 判断流程

保留现有硬闸，在前面加一层**统一反欺诈预检**：

```
黑名单/收入硬闸（现有）
        ↓
反欺诈预检 FraudRiskService（新增）
  ├─ CRITICAL → 系统拒绝（FRAUD_RISK_CRITICAL）
  ├─ HIGH     → 强制人工复核（FRAUD_RISK_HIGH）
  ├─ MEDIUM   → 打标签（FRAUD_RISK_MEDIUM）
  └─ LOW      → 继续评分卡
        ↓
评分卡 + 额度计算（现有）
```

这样既保留"强信号快速拦截"，又让风险等级可解释、可审计。

其他两处优化：
- **黑名单缓存**：`CreditScoreEngineImpl` 增加 60 秒全量缓存 + `clearBlacklistCache()`，避免每次申请全表扫描；
- **审计**：反欺诈结果写入 `auditRemark` / `riskTags`，随 Redis 报告一起保存。

### 2.3 反欺诈监控（未来可能逾期/失联）

新增 `FraudRiskService`，把三类信号合成 0–100 分：

| 信号 | 分值（示例） |
|---|---|
| 黑名单 FULL / NAME_AREA / NAME_ONLY | +80 / +45 / +12 |
| 近 1 周征信查询 ≥6 / ≥3 | +18 / +9 |
| 近 1 月征信查询 ≥12 / ≥6 | +14 / +7 |
| 手机号 30 天内更换 / 90 天内更换 | +14 / +7 |
| 活跃贷款 ≥5 / ≥3 | +12 / +6 |
| 历史被拒 ≥3 / ≥1 | +14 / +5 |
| 近 6 月逾期代理 ≥3 / ≥1 | +22 / +12 |
| 信用卡使用率 ≥1.0 / ≥0.8 | +10 / +6 |
| 第三方评分 A/B < 0.3 | +12 / +10 |
| 自填收入高于征信 >50% / >15% | +18 / +8 |
| B 卡当前 DPD ≥30 / ≥7 | +20 / +10 |
| B 卡准时率 < 0.5 | +14 |
| 最近一次逾期 <30 天 | +10 |
| 额度使用率 ≥0.9 | +8 |
| 近 30 天申请 ≥3 次 | +8 |

等级：**≥70 CRITICAL，≥45 HIGH，≥25 MEDIUM，其余 LOW**。

落地：
- `FraudRiskService`：纯规则、可解释，返回 `score / level / signals / summary`；
- `FraudMonitorService`：每天扫描启用 B 卡的用户，结合最新 `BF_V2.2` 快照、外部特征、黑名单、近 30 天申请次数；
- 预警表 `b_card_fraud_warning`（每个用户每天一条，可人工确认）；
- 接口：`POST /admin/b-card/governance/fraud/scan`、`GET .../fraud/warnings`、`POST .../fraud/warnings/{id}/ack`。

---

## 三、本次代码改动

| 文件 | 改动 |
|---|---|
| `risk/score/CreditScoreEngineImpl.java` | 黑名单 60 秒缓存 + 清理方法 |
| `risk/fraud/FraudRiskService.java` | 反欺诈评分（申请时点 + 存量早筛） |
| `risk/fraud/FraudMonitorService.java` | 每日扫描、预警落库、确认 |
| `risk/fraud/FraudRiskMonitorTask.java` | 每日 05:00 定时任务 |
| `risk/fraud/FraudWarningSchemaInitializer.java` | `b_card_fraud_warning` 建表（幂等） |
| `risk/admin/BCardGovernanceController.java` | 反欺诈扫描/预警/确认接口 |
| `risk/assessment/RiskAssessmentServiceImpl.java` | 接入反欺诈预检；阈值改为读引擎（消除 720/580 与 788/642 的不一致） |
| `risk-assessment/train_a_card_governance.py` | A 卡时间外验证/筛选/校准/成本阈值 |
| `sql/migration_b_card_fraud.sql` | 预警表迁移 |

---

## 四、运行结果（可复制）

### 4.1 A 卡模型（fixture，deployable=false）

```
入模特征(9): ['ext_source_3', 'credit_bureau_week', 'active_loans_count', 'cc_utilization',
             'ext_source_2', 'credit_bureau_mon', 'days_last_phone_change',
             'employment_years', 'age_years']

OOT: LR raw AUC=0.6559  LR calibrated AUC=0.6559  Brier=0.0757 | tree AUC=0.6147
阈值(业务成本): approve<0.125  reject>=0.135  review=0.0556  cost=1,652,112.5
PSI: train=0.0000  calib=0.0322  oot=0.0081
```

| 指标（OOT, n=450） | LR 原始 | LR 校准后 | 树模型 |
|---|---:|---:|---:|
| AUC | 0.6559 | 0.6559 | 0.6147 |
| KS | 0.2883 | 0.2883 | 0.2412 |
| Lift@10% | 2.63 | 2.63 | 1.32 |
| PR-AUC | 0.1491 | 0.1491 | 0.1205 |
| Brier | 0.2064 | **0.0757** | 0.0824 |

### 4.2 B 卡链路回归

```
A3_CHECKS = 6, failed = 0, passed = True
A4_CHECKS = 5, failed = 0, passed = True
```

### 4.3 Java 测试

```
risk-service: Tests run 78, Failures 0, Errors 0
（其中 FraudRiskServiceTest 6 项）
```

---

## 五、结果解释

1. **校准不改变 AUC**：Platt 是单调变换，只改变概率刻度，不改变排序；所以 AUC/KS/Lift 不变，但 **Brier 从 0.2064 降到 0.0757**，说明概率更接近真实坏账率。
2. **树模型更差**：fixture 只有 1800 条且标签由线性规则生成，树模型容易过拟合；真实数据上应再用 LightGBM/HistGB 复验。
3. **阈值带很窄（0.125–0.135）**：因为额度压降的体验/收入成本较高，成本最优解只在最高风险处拒绝；实际业务应调整成本参数后重新求解。
4. **PSI 0.008–0.032**：OOT 与训练分布接近，说明时间外稳定性尚可（合成数据）。
5. **反欺诈评分**：黑名单 FULL 单项就 80 分，直接 CRITICAL；多头+换号+历史拒绝可到 HIGH；B 卡 DPD≥30 + 准时率低 + 最近逾期可到 HIGH。这样能把"可能逾期/失联"的用户提前捞出来人工复核。

---

## 六、剩余风险

1. A 卡与反欺诈目前用 **fixture/规则** 验证逻辑，真实效果需用真实申请数据回测；
2. 本机缺少 `pyarrow`，无法直接读取 102MB 的 HC parquet，真实 A 卡训练需在标准环境（pandas≥2.0 + pyarrow）执行；
3. 反欺诈分值是规则权重，建议用历史坏账/欺诈标签做逻辑回归或评分卡校准；
4. 黑名单缓存 60 秒，规则同步后最慢 60 秒生效（可调用清理方法）；
5. A 卡候选规则 `deployable=false`，未覆盖线上 `scoring_rules.json`，上线需走审批与回滚流程。