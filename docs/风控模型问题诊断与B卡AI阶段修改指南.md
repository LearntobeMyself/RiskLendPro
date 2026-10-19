# RiskLendPro 风控模型问题诊断与 B 卡 AI 阶段修改指南

> 基线仓库：`LearntobeMyself/RiskLendPro`  
> 基线提交：`1db7eca`  
> 编写日期：2026-09-20  
> 适用范围：A/B 卡共用模型治理问题、B 卡离线和在线链路、B 卡特征快照与贷后决策  
> 使用方式：每次只让 AI 执行一个阶段或一个任务包，不要一次性重写整条风控链路。

---

## 1. 结论先行

### 1.1 当前 B 卡处于什么阶段

当前 B 卡应定位为“可观测、可审计、默认不自动降额的安全版本”，不应被视为可正式独立决策的生产评分卡。原因如下：

1. 线上基础分仍依赖 Home Credit 静态特征，而新建的 `b_card_feature_snapshot` 数据没有进入线上评分链路。
2. 当前 V1 离线 AUC 约 `0.5713`、KS 约 `0.1236`、Top 20% 抓捕率约 `26.8%`，区分度偏弱。
3. 训练使用随机切分和 `TARGET` 标签，不是基于真实观察期/表现期的贷后标签，无法证明跨时间稳定性。
4. 历史特征快照没有严格按 `asOfDate` 还原当时状态，回填历史样本时可能发生时间穿越。
5. 部分关键路径仍可能在“分数不可信”时生成额度系数，尤其是自动调额调用链。
6. 新 B 卡快照与现有模型的特征命名、特征口径、缺失值处理都不一致，不能直接替换模型。
7. 没有成熟的标签生成、模型注册、影子评分、PSI 监控、校准和回滚闭环。

### 1.2 最重要的五个优先事项

| 优先级 | 事项 | 为什么必须先做 |
|---|---|---|
| P0-1 | 彻底阻断不可信分数进入额度调整 | 这是当前最直接的潜在资金风险 |
| P0-2 | 用户还款成功后及时重算 B 分 | 否则准时还款的正面行为长期不进入评分 |
| P0-3 | 修复历史快照的时间穿越 | 否则后续 V2 训练集本身不可信 |
| P0-4 | 统一训练与在线特征口径、缺失值语义 | 当前存在训练用中位数、在线用 0 的偏差 |
| P0-5 | 重建真实标签、时间外验证和模型注册 | 决定 V2 是否有资格进入影子运行 |

### 1.3 优化方向概览

| 方向 | 预期收益 | 工作量 | 风险 | 建议 |
|---|---|---:|---:|---|
| 修复可信度门控和自动调额保护 | 高 | 低 | 低 | 立即做 |
| 还款/逾期事件后实时重算 | 高 | 低 | 低 | 立即做 |
| 模型规则原子激活和缓存刷新 | 高 | 中 | 低 | 阶段 1 完成 |
| 重做 as-of 历史特征 | 高 | 高 | 中 | 阶段 2 完成 |
| 标签成熟和样本生成 | 高 | 高 | 中 | 阶段 2-3 |
| V2 单模型重建 | 高 | 高 | 中高 | 数据成熟后做 |
| 概率校准和业务阈值 | 高 | 中 | 中 | 阶段 3 完成 |
| V2 影子运行 | 高 | 中 | 低 | 阶段 4 |
| 自动化额度决策 | 潜在收益最高但风险最高 | 高 | 高 | 影子验收前禁止开启 |

---

## 2. 当前 B 卡实际链路

```text
用户放款
  -> loan-service 调用 risk-service.activateBehaviorScore
  -> risk-service 用身份证查 user_behavior_features（HC 静态特征）
  -> BehaviorScoreEngineImpl 计算 HC 基线分
  -> loan-service.getLoanBehavior 生成实时贷后修正特征
  -> baseScore + deltaScore 得到最终 B 分
  -> 写入 user_credit_limit.b_score 和 user_b_card_log
  -> 每日快照任务另外生成 b_card_feature_snapshot（BF_V2.1）
  -> 自动调额默认关闭，后续需要影子验收后才允许开启
```

### 2.1 关键事实

- `BehaviorScoreService.computeBaseScore` 读取 `user_behavior_features`，不是读取 `b_card_feature_snapshot`。
- `BCardFeatureServiceImpl` 生成的 BF_V2.1 快照目前主要用于未来训练，不是当前 V1 推理输入。
- `BehaviorLiveFeatureServiceImpl` 只把 `maxOverdueDays`、`overduePeriodCount`、`onTimeRate`、`historyAvailable` 转成固定加减分。
- 自动调额由 `OverdueLimitAdjustTask` 从 loan-service 发起。
- `risk.b-card.auto-adjust-enabled` 当前默认值为 `false`，这是正确的安全默认值。

---

## 3. P0 问题清单：先修安全边界，不先改模型公式

### P0-1 不可信 B 分仍可能进入额度调整

**证据**

- `services/risk-service/src/main/java/org/example/risklendpro/risk/score/BehaviorScoreService.java`：
  - `resolveLimitMultiplierForUser` 会检查最新日志的 `scoreReliable`，但远程接口没有调用它。
  - `resolveLimitMultiplier` 只按分数返回系数。
- `services/risk-service/src/main/java/org/example/risklendpro/risk/client/InternalRiskController.java`：
  - `getLimitMultiplier` 直接调用 `resolveLimitMultiplier`，没有 `scoreReliable`、模型版本或数据状态校验。
- `services/loan-service/src/main/java/org/example/risklendpro/loan/repay/OverdueLimitAdjustTask.java`：
  - 自动调额前只检查 `bScore != null`，没有检查最新评分是否可信。

**影响**

一旦显式开启自动调额，缺少身份证、缺少特征快照、特征解析失败或历史不足的用户，仍可能根据中性分或不完整分生成降额系数。这直接违反了“不可信分数不参与额度决策”的设计目标。

**AI 修改要求**

1. 保留 `resolveLimitMultiplierForUser` 的可信度语义，但不要只靠调用方自觉。
2. 远程决策接口改为返回结构化对象，至少包含：
   - `scoreReliable`
   - `dataStatus`
   - `modelVersion`
   - `featureVersion`
   - `policyVersion`
   - `multiplier`
   - `decisionAllowed`
   - `reason`
3. `decisionAllowed=false` 时，系数必须为 `null`，loan-service 必须直接跳过额度变更。
4. `OverdueLimitAdjustTask` 不允许只根据 `bScore` 判断可否调整。
5. 增加回归测试，覆盖以下场景：
   - 无评分日志
   - `scoreReliable=false`
   - 模型未加载
   - 特征缺失
   - 可信且分数正常

**验收标准**

- 不可信分数无论数值多低，都不会产生调额日志。
- 自动调额失败时保持原额度，并记录跳过原因。
- 可信度判断和系数生成在同一个服务端决策点完成。

---

### P0-2 准时还款后 B 分不会及时重算

**证据**

- `services/loan-service/src/main/java/org/example/risklendpro/loan/repay/RepaymentServiceImpl.java`：正常还款成功后没有调用 B 分重算。
- `services/loan-service/src/main/java/org/example/risklendpro/loan/repay/RepaymentScheduleTask.java`：
  - 逾期首次发生和每日逾期更新时调用重算。
  - 按时还款推进期次时没有对应重算调用。
- 全仓库只有逾期任务、修复后的逾期调额任务和激活流程会重算行为分。

**影响**

B 卡最核心的正向信号是“按时还款”。如果按时还款不重算，用户改善后的行为可能长期不反映在 B 分中，导致监控、解释和后续模型训练都出现偏差。

**AI 修改要求**

1. 正常还款提交成功并完成本地事务后，发送事件或可靠消息触发 `recalculateBehaviorScore`。
2. 不在数据库事务未提交时同步调用跨服务 HTTP。
3. 优先考虑 outbox 表或事件表：
   - 还款成功写入 outbox
   - 异步任务消费并重算
   - 失败可重试、可审计
4. 每日任务可以保留兜底，但不能作为唯一触发机制。
5. 测试场景：
   - 按时还款后产生一次重算事件
   - 逾期后仍能重算
   - 跨服务失败不污染本地还款事务
   - 重复消费不重复生成业务额度变更

**验收标准**

- 在用户完成准时还款后，基于事件链路能更新 B 分。
- 本地还款事务不受 risk-service 短暂不可用影响。
- 重算失败有可查询的重试记录。

---

### P0-3 历史快照存在时间穿越风险

**证据**

- `services/risk-service/src/main/java/org/example/risklendpro/risk/bcard/BCardFeatureServiceImpl.java`：
  - 查询贷款、计划和还款记录时没有按 `asOfDate` 限制。
  - `currentMaxDpd` 使用当前 `RepaymentPlan.overdueDays`。
  - `maxDpd12m` 使用当前计划状态和当前更新时间。
  - `effectiveLoans` 依据当前贷款状态，而不是历史时点状态。
  - `outstandingAmount` 和额度使用率采用当前值。
  - `createSnapshot` 虽然接收 `asOfDate`，但没有保证数据只来自该日期之前。

**影响**

如果直接用当前实现回填 2026-08-01 的快照，2026-09-01 才发生的还款、逾期、额度变化可能被写进 8 月特征，导致 V2 训练集发生标签泄漏或时间穿越。随机切分下这种错误不容易被发现，但在真实 OOT 验证中会严重高估模型效果。

**AI 修改要求**

1. 所有查询和聚合必须显式遵守 `asOfDate`。
2. 还款计划的 DPD 必须按 `asOfDate - dueDate` 重新计算，不能读取当前 `overdueDays`。
3. 贷款状态、计划状态和额度状态需要支持历史时点还原。
4. 若现有表无法还原历史状态，新增不可变事件表或状态历史表：
   - 贷款状态历史
   - 还款期次状态历史
   - 额度历史快照
5. 快照重跑必须幂等：
   - 对同一 `user + asOfDate + featureVersion` 重跑，结果应稳定。
6. 增加隐藏未来数据测试：
   - 在 `asOfDate` 之后新增还款、逾期、额度变化
   - 过去快照的 `feature_json` 不得变化

**验收标准**

- 任意历史日期快照不读取该日期之后的数据。
- `same inputs + same asOfDate = same feature_json`。
- 至少覆盖未来还款、未来逾期、未来额度变化三类测试。

---

### P0-4 训练和在线缺失值处理不一致

**证据**

- `risk-assessment/clean_behavior_features.py`：
  - 对合并后特征执行 `fillna(0)`，把“缺失”伪装成有效数值 0。
- `risk-assessment/train_b_card_model.py`：
  - 训练前使用中位数填充缺失值。
  - WOE 的 `missing_bin` 在当前流程中基本失去作用。
- `services/risk-service/src/main/java/org/example/risklendpro/risk/score/BehaviorScoreEngineImpl.java`：
  - 在线按 `Map<String, Double>` 读取特征，无法区分“真实 0”和“缺失”。
- `BehaviorScoreService` 只判断整行特征是否存在，不校验特征完整率和字段集合。

**影响**

同一个缺失用户在训练和线上会走不同数值，JAVA 无法复现 Python 的填充逻辑。对 B 卡这种真实业务样本稀少的场景，这个问题会让离线指标和线上分数没有可比性。

**AI 修改要求**

1. 训练数据中保留 `NULL`，不要提前填 0。
2. 在训练阶段单独拟合缺失值策略，并将策略写入模型文件。
3. 在线推理使用同一套缺失值策略和缺失标记。
4. 模型输入增加元数据：
   - 必需特征列表
   - 特征版本
   - 缺失率阈值
   - 缺失值策略
5. 当必需特征完整率低于阈值时，分数状态必须是 `FEATURE_INCOMPLETE`，不得标记为 `RELIABLE`。
6. 增加 Python/Java 一致性测试：
   - 同一原始特征和同一缺失模式
   - Python 与 Java 输出相同 WOE 和分数
   - 缺失、真实 0、异常值分别测试

**验收标准**

- 不再把缺失值填成真实 0。
- Python 和 Java 对相同输入得分误差小于 `0.01`。
- 缺失率超阈值时 `scoreReliable=false`。

---

### P0-5 当前模型标签和切分方式不成立

**证据**

- `risk-assessment/train_b_card_model.py`：
  - 直接把 Home Credit `TARGET` 作为 `defaulted`。
  - 使用 `train_test_split` 随机切分。
  - 数据不存在时自动训练合成数据。
- 当前输出文件未体现观察期、表现期、成熟标签、时间外验证。

**影响**

`TARGET` 是申请层面的历史违约标签，不是“某个贷后时点之后 6/12 个月发生 30/90 DPD”的 B 卡标签。将其与贷后行为特征混合，且使用随机切分，会造成：

- 标签时间定义不清
- 观察期和表现期混用
- 无法检验跨时间稳定性
- 对未来表现的外推能力未知
- 合成数据可能被误当成真实模型训练结果

**AI 修改要求**

1. 删除或显式隔离合成数据训练路径。
2. 数据缺失时必须失败退出，不得生成可部署规则。
3. B 卡标签从真实快照生成：
   - `label_30dpd_6m`
   - `label_90dpd_12m`
4. 训练切分改为时间切分：
   - 训练集：较早 vintage
   - 验证集：中间 vintage
   - OOT：最新 vintage
5. 记录样本成熟日期、标签版本、观察期和表现期。
6. 对未成熟样本使用 `sample_status=OPEN`，训练时排除。

**验收标准**

- 训练日志明确输出每个样本的观察期、表现期和标签版本。
- OOT 不使用随机样本，而是严格晚于训练期。
- 缺少真实数据时训练脚本返回非零退出码，不生成规则。

---

### P0-6 标签成熟任务缺失，表现期定义不一致

**证据**

- `services/risk-service/src/main/java/org/example/risklendpro/risk/bcard/BCardFeatureServiceImpl.java`：
  - 创建快照时固定设置 `performance_end = asOfDate + 180 天`。
  - `sample_status` 始终写为 `OPEN`。
  - `label_30dpd_6m` 和 `label_90dpd_12m` 没有生成入口。
- `sql/migration_b_card_v2.sql` 同时存在 6 个月和 12 个月标签字段，但没有对应的成熟状态、成熟日期和标签版本字段。
- 当前没有定时任务或服务负责扫描到期快照并回填标签。

**影响**

即使快照按时生成，快照也不会自动变成可训练样本。6 个月和 12 个月标签使用不同表现期，当前统一的 180 天窗口无法同时表达两个标签，后续很容易把未成熟样本误用于训练。

**AI 修改要求**

1. 明确 `30DPD@6M` 和 `90DPD@12M` 的观察期、表现期和成熟规则。
2. 为快照增加或等价记录：
   - `maturity_date_30dpd_6m`
   - `maturity_date_90dpd_12m`
   - `label_version`
   - `label_calculated_at`
3. 新增幂等标签任务：
   - 只处理已成熟样本
   - 未逾期不等于坏样本时要按表现期结束判断
   - 结清、未结清、展期、核销等状态都要有定义
4. `sample_status` 至少区分：
   - `OPEN`
   - `MATURED`
   - `LABELED`
   - `EXCLUDED`
5. 训练数据导出只允许使用 `LABELED` 且标签版本匹配的样本。

**验收标准**

- 6 个月标签和 12 个月标签按各自成熟日期生成。
- 重跑标签任务不重复或覆盖不一致结果。
- 训练脚本能拒绝未成熟、标签缺失或标签版本不一致的样本。

---

## 4. P1 问题清单：再决定 V2 怎么做

### P1-1 V1 区分度明显不足

**证据**

- `risk-assessment/output/b_scoring_rules.json`：
  - AUC 约 `0.571345`
  - Accuracy 约 `0.6101`
- `docs/2026.9.15_B卡改动日志.md`：
  - KS 约 `0.1236`
  - Top 20% 坏客户抓捕率约 `26.8%`
  - WOE/LR 离线 AUC 约 `0.6038`
  - 树模型挑战 AUC 约 `0.6200`、OOT 尚未正式建立

**判断**

当前分数可以作为监控和排序参考，但不适合作为自动降额主依据。后续不能只追求 AUC 小幅提升，必须同时看 KS、Lift、校准、跨时间稳定性和坏账/额度损失。

### P1-2 特征选择与分箱不够严谨

**证据**

- `pick_b_card_columns` 按缺失率和唯一值数量选取字段，不是按 IV、稳定性、业务合理性和相关性选取。
- `pos_dpd_mean`、`pos_dpd_def_mean` 的 IV 约 `4.2e-09`，系数约 `-2.87e-08`，实质上接近无贡献。
- 分箱采用分位数切分，没有强制单调性、最小箱占比、缺失箱独立性和稳定性校验。

**建议**

- 特征选择同时要求：
  - IV 或信息增益达到阈值
  - 业务方向可解释
  - 缺失率可接受
  - PSI 稳定
  - 与其他特征相关性低于阈值
- 分箱后检查单调性和每箱样本量。
- 近零 IV 特征不得仅因为字段数量不足而强行入模。

### P1-3 PDO 概率没有真实校准

**证据**

- `train_b_card_model.py` 使用 `class_weight="balanced"`，会改变预测概率分布。
- 标定函数以验证集预测 odds 的中位数作为 `target_odds`。
- 当前规则 `target_odds=0.9040787474`，明显接近“坏/好 odds≈0.9”，而训练数据实际违约率约 `8.72%`，两者不是同一校准口径。
- `watch`、`reduce_limit` 直接取预测分的第 55%/35% 分位，没有业务成本、容量或人工处理能力约束。

**影响**

B 分不能直接解释为违约概率或预期损失，分数阈值也不代表明确的风险水平。后续报告应避免把 B 分描述为校准后的 PD。

**建议**

- 在独立校准集上做 Platt/Isotonic 校准。
- 校准后再做 PDO 映射。
- 阈值由以下约束共同确定：
  - 可接受的坏账率
  - 人工审核容量
  - 额度损失
  - 客户体验
- 输出校准曲线、分箱坏账率、Lift 表和阈值业务模拟。

### P1-4 “基础分 + 动态分”融合方式缺少统计依据

**证据**

- `BehaviorScoreService.recalculateInternal` 直接执行 `baseScore + deltaScore`。
- `BehaviorLiveFeatureService.LiveFeatures.computeDelta` 使用固定公式：
  - 最大逾期天数乘 `-3`
  - 逾期期数乘 `-12`
  - 准时率乘 `+40`
  - 最后截断到 `[-100, +20]`
- 基础分中的 `inst_dbd_mean_total`、`inst_dbd_mean_1y` 已经包含历史 DPD，实时 DPD 修正又再次使用逾期天数，存在相关特征重复计数。

**建议**

中期方向应由“两个分数相加”改为：

1. 在固定观察时点生成统一行为快照。
2. 用同一模型同时消费静态和动态行为特征。
3. 若保留动态修正，必须基于不同历史窗口独立校准，而不是固定业务常量。

在校准完成前，动态分只能作为监控标签或人工解释，不应作为自动降额依据。

### P1-5 贷后监控特征还有性能和口径问题

**证据**

- `InternalLoanController.getLoanBehavior` 先按计划循环查询还款记录，随后又查询全部还款记录，存在 N+1 查询。
- 当前用 `Date.before(now)` 判断逾期，而定时任务用整天差判断，月末、到期日当天和时区边界可能出现口径不一致。
- `onTimeRate` 的变量名和分母语义混淆：未还且已到期记录也会计入 `paid` 分母。
- `BCardFeatureServiceImpl` 的 `paidRecordCount12m` 在 `dueDate.before(asOfDate)` 时也增加，命名与实际含义不一致。

**建议**

- 先批量查询所有相关计划记录，再按 `planId` 分组。
- 逾期统一使用业务时区下的 `LocalDate` 和统一的 `dpd` 计算函数。
- 将“已结清期数”“到期未还期数”“准时结清期数”“部分还款期数”定义为独立字段。
- 为这些口径增加边界测试。

### P1-6 评分审计信息不足

**证据**

- `UserBCardLog` 只有基分、 delta、最终分和简化的 `live_features` JSON。
- 没有模型版本、特征版本、策略版本、规则哈希、输入特征快照、拒绝原因和阈值来源。
- `user_b_card_log` 无法完整复现一次评分。

**AI 修改要求**

新增至少以下字段或等价 JSON 结构：

- `model_version`
- `feature_version`
- `policy_version`
- `rule_hash`
- `feature_hash`
- `base_score`
- `delta_score`
- `final_score`
- `score_reliable`
- `data_status`
- `decision_allowed`
- `created_at`

**验收标准**

- 给定日志中的模型版本和特征快照，可离线复现同一分数。
- 任何额度决策都能追溯到分数版本和规则版本。

### P1-7 规则激活与缓存不具备生产级治理

**证据**

- `BehaviorScoreEngineImpl` 首次加载后把规则永久缓存在内存中，没有 TTL 或版本失效。
- `BehaviorScoringRulesMapper.selectActiveRule` 使用 `ORDER BY version DESC`，字符串版本排序会随版本命名变化而失效。
- `risk-assessment/load_to_mysql.py` 新版本插入时没有可靠地关闭旧版本，理论上可能出现多个 `is_active=1`。
- `threshold_watch`、`threshold_reduce_limit` 存在于数据库列，同时又存在于 `rule_content.thresholds`，可能版本不一致。

**AI 修改要求**

1. 规则发布采用“校验后原子激活”：
   - 先写入 inactive 新版本
   - 完成结构校验和评分回放
   - 单事务切换 active 版本
2. 缓存至少支持：
   - TTL
   - 规则版本/hash 检查
   - 人工刷新
3. 只允许一个 active 版本。
4. Java 启动或加载时校验：
   - `model_type`
   - `card_type`
   - 特征列表
   - 系数和 WOE 长度
   - 阈值一致性
   - PDO 参数
5. 校验失败不得回退成“可用中性分”，应返回不可信状态。

---

## 5. 分阶段执行方案

### 阶段 0：冻结基线，建立安全闸门

**目标**

在修改模型前，先让当前版本可测试、可观测、可回滚。

**任务**

- 固定基线提交 `1db7eca`。
- 保持 `risk.b-card.auto-adjust-enabled=false`。
- 统一 JDK 21 和 Maven 测试环境。
- 运行并保存 B 卡相关单元测试结果。
- 为可信度、自动调额、还款重算、历史快照建立回归测试清单。
- 记录当前规则版本、阈值、AUC、KS 和线上分布。

**交付物**

- 基线测试报告。
- 当前模型指标快照。
- B 卡回归测试清单。

**AI 指令**

```text
请执行《风控模型问题诊断与 B 卡 AI 阶段修改指南》的阶段 0。
禁止修改业务公式、模型规则和自动调额默认值。
先运行现有测试并记录结果，再补齐缺失的回归测试。
只提交测试、配置和文档变更。
```

---

### 阶段 1：修复生产安全边界

**目标**

在模型不变的前提下，消除不可信分数进入决策和准时还款不重算的问题。

**任务**

- 完成 P0-1：可信度门控和自动调额保护。
- 完成 P0-2：还款成功后的可靠重算事件。
- 完成 P1-6/P1-7 中与评分审计和模型缓存直接相关的部分。
- 不在本阶段引入 V2，不改变现有 B 卡分数公式。

**交付物**

- 结构化评分决策 DTO。
- loan-service 自动调额可信度门控。
- 还款事件/outbox 和重算消费链路。
- 评分审计字段。
- 回归测试。

**退出条件**

- 不可信分不会调额。
- 准时还款可触发重算。
- 模型规则激活原子化，缓存可刷新。
- 所有 B 卡相关测试通过。

**AI 指令**

```text
请只执行本文件阶段 1。
不要修改 B 卡特征公式、WOE、系数、PDO 或阈值。
先列出计划修改文件和风险，再逐项实现 P0-1、P0-2 和必要的审计字段。
每完成一组修改就运行对应测试并汇报失败原因。
```

---

### 阶段 2：修复 as-of 特征和标签流水线

**目标**

建立可信、可复现、无时间穿越的 B 卡训练样本。

**任务**

- 完成 P0-3：所有历史聚合按 `asOfDate` 还原。
- 完成 P0-4：删除缺失值伪 0，统一 Python/Java 转换。
- 完成 P0-6：标签成熟任务。
- 将特征版本升级到新的 `BF_V2.2` 或等效版本。
- 增加数据质量报告：
  - 缺失率
  - 异常值
  - 账龄与 DPD 冲突
  - 状态历史缺失
  - 样本成熟率

**交付物**

- as-of 特征服务。
- 标签生成任务。
- 训练样本导出脚本。
- 数据质量报告。
- 历史快照重跑一致性测试。

**退出条件**

- 历史快照不受未来数据影响。
- 标签只在表现期成熟后生成。
- 训练导出数据包含 `asOfDate`、观察期、表现期、标签和特征版本。
- 重复运行结果一致。

**AI 指令**

```text
请只执行本文件阶段 2。
目标是修复历史快照的 as-of 语义和标签成熟流程，不要训练新模型。
任何无法从现有表还原的历史状态必须明确报告，并提出事件表/状态历史表迁移方案。
新增测试必须覆盖未来数据不能污染历史快照。
```

---

### 阶段 3：重建 V2 训练与评估

**目标**

用真实、成熟的 B 卡样本训练可解释、可校准的挑战模型。

**任务**

- 完成 P0-5：真实标签和时间切分。
- 完成 P1-2、P1-3、P1-4。
- 对比：
  - 现行 V1
  - WOE/LR
  - 带正则的逻辑回归
  - LightGBM/XGBoost 挑战模型
- 输出：
  - AUC
  - KS
  - Lift
  - 分箱坏账率
  - 校准曲线
  - PSI
  - 阈值业务模拟
  - 特征稳定性和解释

**建议门槛**

以下是项目建议值，最终应结合样本量和导师要求调整：

| 指标 | 研究挑战模型 | 影子运行 | 自动决策 |
|---|---:|---:|---:|
| 独立 OOT AUC | ≥0.62 | ≥0.65 | 需单独审批 |
| KS | ≥0.18 | ≥0.25 | 需单独审批 |
| 校准误差 | 可观察 | 受控 | 必须受控 |
| PSI | <0.10 | <0.10 | <0.10，超限熔断 |
| 坏样本量 | 满足置信区间 | 满足 OOT 置信区间 | 需正式评审 |

**硬性禁止**

- 仅按 Accuracy 选模型。
- 仅用随机切分证明效果。
- 用合成数据生成上线规则。
- 用未成熟标签训练。
- 只做一次回测就上线。

**AI 指令**

```text
请只执行本文件阶段 3。
输入必须是阶段 2 产出的真实成熟样本，不得使用合成数据和随机切分。
先完成数据审计和 OOT 切分，再训练模型。
每次实验必须保存配置、特征版本、样本版本、指标和随机种子。
```

---

### 阶段 4：V2 影子运行和双评分

**目标**

在不影响生产额度的前提下，验证 V2 的稳定性、解释性和业务价值。

**任务**

- 保留 V1 为生产监控分。
- 新增 V2 shadow 分，不影响额度。
- 每次评分同时记录 V1、V2、模型版本、特征快照和决策原因。
- 建立差异分析：
  - V1/V2 相关性
  - 分群差异
  - 阈值附近差异
  - 逾期前后变化
  - 数据缺失影响
- 建立人工审核面板。

**退出条件**

- 影子运行达到约定周期。
- PSI、校准、坏账率和人工解释均达标。
- 没有不可解释的大规模分数漂移。
- 形成人工审批或小额试运行方案。

**AI 指令**

```text
请只执行本文件阶段 4。
不得修改生产 B 分，不得开启自动调额。
实现 V1/V2 双写、差异监控和管理端对比查询。
所有影子分数必须带模型版本、特征版本、reliability 和解释字段。
```

---

### 阶段 5：受控上线和回滚

**目标**

在通过影子验收后，按小范围、可回滚方式引入 V2。

**任务**

- 增加自动调额总开关和分群开关。
- 增加单次降额上限、累计降额上限、冷却期和人工复核条件。
- 增加熔断：
  - PSI 超限
  - 数据完整率下降
  - 校准偏差
  - 调额异常率
  - 申诉/人工推翻率异常
- 采用 canary 或低风险客群试运行。
- 保留 V1 回滚路径。
- 所有决策写入调额审计和模型审计日志。

**AI 指令**

```text
请只执行本文件阶段 5。
任何自动额度变更都必须由显式开关控制，默认关闭。
实现熔断、回滚、人工复核和完整审计后才能允许开启。
```

---

## 6. 可直接复制给 AI 的任务模板

```text
请执行《风控模型问题诊断与 B 卡 AI 阶段修改指南》的阶段 X、任务 Y。
当前基线提交：1db7eca。
限制：
1. 不修改未在本任务列出的模型规则和阈值。
2. 保持 risk.b-card.auto-adjust-enabled=false。
3. 不引入合成数据。
4. 不把不可信分数用于额度决策。
5. 先阅读相关代码和测试，再给出最小修改计划。
6. 每项修改必须有测试或可验证验收标准。
7. 最后输出：修改文件、测试命令、测试结果、剩余风险和下一步。
```

---

## 7. 后续验收检查表

- [ ] 自动调额默认关闭。
- [ ] 不可信分数无调额日志。
- [ ] 准时还款可触发 B 分重算。
- [ ] 任意历史快照不读取未来数据。
- [ ] Python 和 Java 对同一特征得到一致分数。
- [ ] 缺失值有明确策略和缺失标记。
- [ ] 标签有观察期、表现期和成熟状态。
- [ ] OOT 通过时间切分而不是随机切分。
- [ ] V2 有校准曲线、PSI 和阈值业务模拟。
- [ ] 评分日志可复现模型版本和特征版本。
- [ ] V2 影子运行未经人工审批不影响额度。
- [ ] 自动调额具备熔断、冷却、累计上限和回滚。

---

## 8. 当前环境验证说明

本次检查中，代码级证据来自仓库当前提交 `1db7eca`。本机命令行默认 Java 为 17，而根 `pom.xml` 目标 Java 为 21；因此未把当前环境下的 Maven 测试结果作为模型正确性的依据。执行后续阶段前，应先统一到 JDK 21，再运行：

```powershell
.\mvnw.cmd -pl services/risk-service -am `
  "-Dtest=BCardFeatureServiceImplTest,BehaviorScoreServiceTest,BehaviorLiveFeatureServiceTest" `
  "-DfailIfNoTests=false" test
```

测试未通过前，不应把任何 V2 规则标记为可上线。
