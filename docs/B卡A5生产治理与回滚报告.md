# B 卡 A5 生产治理、监控与回滚报告

> 阶段：A5（依赖 A4）
> 迁移脚本：`sql/migration_b_card_a5_governance.sql`（已在 `RiskLendPro` 执行，3 张表均为空表）

## 1. 结论摘要

| 能力 | 实现 |
|---|---|
| 模型注册 / 版本哈希 | `b_card_model_registry`，artifact 内容 SHA-256，同一版本禁止覆盖 |
| 发布审批 | `CANDIDATE → APPROVED`，人工审批人/时间落库 |
| 激活（单事务） | 旧 ACTIVE 下线为 `RETIRED`，目标置 `ACTIVE`；唯一索引保证至多一个 ACTIVE |
| 回滚 | 只能回滚到「曾激活过」的版本；同样单事务、不删除任何历史行 |
| 决策追溯 | `b_card_decision_audit` 记录模型/特征/策略/规则哈希/数据快照引用 |
| 监控告警 | 快照数、缺失率、分数 PSI、命中率、分箱坏账率、阈值漂移 |
| 告警闭环 | `OPEN → ACKED → CLOSED`，可记录确认人/时间 |
| 历史保留 | 所有治理操作只改状态与时间戳，**没有任何 DELETE** |

## 2. 模型治理

### 2.1 表结构要点

- `uk_model_version`：同一模型版本唯一，重复注册直接拒绝；
- `uk_model_single_active`（生成列 `active_slot`）：数据库层保证至多一个 ACTIVE；
- 保留 `approved_by/approved_at/activated_at/retired_at/last_action/last_action_at`，可还原完整发布历史。

### 2.2 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/admin/b-card/governance/models` | 注册候选（不激活） |
| POST | `/admin/b-card/governance/models/{version}/approve` | 人工审批 |
| POST | `/admin/b-card/governance/models/{version}/activate` | 激活（要求已审批、特征版本与在线一致） |
| POST | `/admin/b-card/governance/models/{version}/rollback` | 回滚到曾激活版本 |
| GET | `/admin/b-card/governance/models` | 版本列表（含历史） |
| GET | `/admin/b-card/governance/models/active` | 当前版本 |

### 2.3 发布 / 回滚步骤（Runbook）

**发布**
1. 注册候选：提交 A4 的 `shadow_model.json` 与校准指标；
2. 风控负责人审批（`approve`）；
3. 确认存在可回滚的上一稳定版本（首次发布除外）；
4. 激活（`activate`）：单事务下线旧版本、激活新版本；
5. 观察监控告警与影子差异，确认无异常。

**回滚**
1. 在版本列表中找到上一稳定版本；
2. `POST .../models/{previous}/rollback`（单事务，历史完整保留）；
3. 规则侧回滚使用 `sql/activate_b_card_rule.sql`（同样单事务、历史规则保留）；
4. 回滚后调 `POST /internal/behavior-scores/rules/refresh` 立即生效。

## 3. 监控与告警

每日 04:30（`risk.b-card.monitor-cron`）执行，指标与阈值：

| 指标 | 计算 | WARN | CRITICAL |
|---|---|---|---|
| `SNAPSHOT_COUNT` | 当日 `BF_V2.2` 快照数 | — | 0（链路中断） |
| `MISSING_RATE` | `DEGRADED/INSUFFICIENT` 占比 | ≥10% | ≥25% |
| `SCORE_PSI` | 当日影子概率 vs 前 30 天 | ≥0.10 | ≥0.25 |
| `HIT_RATE` | 影子决策中降额/拒绝占比 | ≥30% | ≥50% |
| `BAD_RATE_BIN` | 已成熟样本按决策档位分箱的最大坏账率 | ≥30% | ≥50% |
| `THRESHOLD_DRIFT` | 当前规则阈值 vs 上一规则版本 | ≥30 分 | ≥60 分 |

样本不足（<30）或标签未成熟时指标返回不可用，**不产生告警**，避免噪声误报。

**告警闭环**：`GET /alerts?status=OPEN` → `POST /alerts/{id}/ack` → `POST /alerts/{id}/close`；同一天重复运行只更新数值，不会把已确认的告警重新打开。

## 4. 决策追溯

每次额度系数决策（`resolveLimitMultiplierForUser`）写入一条 `b_card_decision_audit`：

| 字段 | 来源 |
|---|---|
| `model_version` | 最新评分日志中的规则版本 |
| `feature_version` | `HC_STATIC_V1+LIVE_V1` |
| `policy_version` | `BCARD_LIMIT_POLICY_V1` |
| `rule_hash` | 规则内容 SHA-256 |
| `snapshot_ref` | `b_card_log:<id>`（本次决策依据的数据快照） |
| `data_status` / `score` / `multiplier` / `decision_allowed` | 决策对象 |

审计写入失败只记录错误日志，**不影响额度决策本身**（避免审计故障拖垮主链路）。

## 5. 测试结果

| 测试 | 位置 | 结果 |
|---|---|---|
| 模型发布与回滚 | `BCardModelRegistryServiceTest`（5 项） | 通过 |
| 决策追溯与审计 | `BehaviorScoreServiceTest` 审计用例 | 通过 |
| PSI / 缺失率 / 坏账率 / 漂移告警 | `BCardMonitorMetricsTest`（7 项） | 通过 |
| 规则与模型版本一致性 | `BCardModelRegistryServiceTest.rejectsActivationWhenFeatureVersionDiffersFromOnline` | 通过 |

全量回归：risk-service **70** 项、loan-service **19** 项，全部通过。

## 6. 验收对照

| 验收标准 | 结论 |
|---|---|
| 任一线上决策可追溯到模型/特征/策略/数据快照 | **通过**（审计表 + 单测断言全部版本字段与 `b_card_log:<id>`） |
| 模型漂移、规则异常、数据异常可自动告警 | **通过**（6 类指标 + 阈值规则 + 单测）；真实告警需数据积累 |
| 出现问题时快速回滚到上一稳定版本 | **通过**（注册表回滚单测；规则回滚脚本就绪） |
| 历史规则和模型版本完整保留 | **通过**（无 DELETE；单 active 唯一索引；发布/回滚测试断言历史行数不变） |

## 7. 剩余风险

1. **真实监控数据尚未产生**：当前只有 14 条 `BF_V2.2` 快照、0 条 LABELED 样本，`BAD_RATE_BIN` 与 PSI 需要更多数据才可用。
2. **告警阈值是初始经验值**，需在影子运行一个账龄周期后校准。
3. **审计写入是同步的**：每次额度决策多一次 insert；若成为瓶颈，后续可改 outbox/异步。
4. **阈值漂移依赖规则历史**：目前库里只有 1 个规则版本，漂移恒为 0；发布 v1.1 后才会生效。
5. **权限粒度**：治理接口沿用现有「已认证管理员」模式，未做细粒度 RBAC。
6. **模型产物体积**：artifact JSON 直接存库，后续大模型可改为对象存储 + 哈希引用。
7. **服务需常驻**：监控/影子/标签任务都依赖服务运行；当前环境不保留后台进程，需要你本机 `start.bat` 常驻。

## 8. 数据库变更

已执行 `sql/migration_b_card_a5_governance.sql`：

- `b_card_model_registry`（0 行，单 active 唯一索引已生效）
- `b_card_decision_audit`（0 行）
- `b_card_monitor_alert`（0 行）

均为新增表，未修改任何现有表或数据。