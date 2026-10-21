# Spec：贷后催收与还款流程 Bug 修复（2026-10-21）

## 背景

对 loan-service 还款与贷后催收（逾期邮件定时任务）逐逻辑点排查后确认 6 个待修 bug 与 4 个仅记录项。
"不管每期还多少钱都算还完"的历史 bug 经查**已被提交 d321c74 修复**（RepaymentServiceImpl L110-115 足额校验 + 回归测试），本次仅作验证记录，不改代码。

修复原则：小步提交（每个修复点一次 commit）；不 push 远程（未获授权）；遵守项目约束（Feign 不入事务、邮件失败不阻断主流程、定时任务 ShedLock）。

## 修复项

### Fix 1（BUG-C1 + BUG-C2）：RepaymentScheduleTask 天数计算与提前提醒

文件：`services/loan-service/src/main/java/org/example/risklendpro/loan/repay/RepaymentScheduleTask.java`

- 新增私有静态方法 `daysSinceDue(Date dueDate, Date today)`：用 `LocalDate` + `ChronoUnit.DAYS.between` 按自然日计算，忽略时分秒。返回负数表示未到期。
- L115-116（checkAndUpdateActivePlan）与 L149-150（updateOverduePlan）的毫秒截断计算替换为该方法。
- L118 `diffDays == 3` 改为 `diffDays == -3`（到期前3天提醒）。

### Fix 2（BUG-C3）：用户服务异常不再阻断逾期状态机

文件：同上 `RepaymentScheduleTask.java`

- checkAndUpdateActivePlan 重排：先算 `diffDays`；`diffDays >= 1` 时 handleOverdue 落库不再依赖 user；user 仅在发送邮件前获取，取不到只跳过邮件。
- handleOverdue 签名去掉 `UserSummary user` 参数；首逾邮件在方法内部自取 user 并判空跳过。

### Fix 3（BUG-C4 + BUG-C7 + BUG-R1）：新建 OverdueStateSync 统一逾期状态回写

新文件：`services/loan-service/src/main/java/org/example/risklendpro/loan/repay/OverdueStateSync.java`

- `@Component`，方法 `syncUserOverdueState(Long userId)`：
  - 聚合该用户所有 OVERDUE 计划下的 OVERDUE 还款记录金额求和；
  - FOR UPDATE 锁定 `user_credit_limit` 行；`overdueAmount` 做 nvl 防御（老数据 NULL 不再 NPE）；
  - 幂等回写 `hasOverdue`（和 > 0）与 `overdueAmount`（重算值，而非增量累加）。
- 三处接入：
  1. `RepaymentScheduleTask.handleOverdue`：删除私有方法 `updateUserCreditLimit`，改调 `overdueStateSync.syncUserOverdueState`；
  2. `RepaymentScheduleTask.updateOverduePlan` 逾期分支内自愈调用（前一日中断的场景次日自动补齐标志位）；
  3. `RepaymentServiceImpl.refreshOverdueState`：L197-213 整块替换为对 `OverdueStateSync` 的调用（解决"仍有其它逾期时 overdueAmount 不重算"）。

### Fix 4（BUG-C5）：OverdueLimitAdjustTask 事务边界与单用户容错

文件：`services/loan-service/src/main/java/org/example/risklendpro/loan/repay/OverdueLimitAdjustTask.java`

- 删除方法级 `@Transactional`（L66）。
- 注入 `PlatformTransactionManager`，用 `TransactionTemplate` 包裹**每个用户**的调整逻辑（仿 AdminLoanOpsServiceImpl 模式）。
- Feign 调用 `recalculateBehaviorScore` 移到事务外执行。
- 每用户 try/catch，单个失败 log.warn 跳过，不再整批回滚。
- 删除 L76-79 冗余的二次锁读（`adjustUserLimitByOverdue` L90-93 已有 FOR UPDATE）。

## 仅记录不改代码项

- C6：静态 SimpleDateFormat 非线程安全（当前单线程调度无实际竞争）。
- R3：executeRepayment L145 借据缺失（repaidLoan == null）时也恢复额度（边缘场景）。
- A1：approveLoan L272 `setEmailSent(true)` 在发送前无条件设置（cosmetic）。
- EmailUtil.sendSimpleEmail 内部吞异常：符合"邮件失败不阻断主流程"约束，保留。

## 验证

1. `mvn -pl services/loan-service -am test` 全量测试通过（基线 55 用例不得有失败）。
2. 新增 `RepaymentScheduleTaskTest`（Mockito，仿 RepaymentServiceImplTest 风格）覆盖：
   - diffDays == -3 触发提前提醒邮件；
   - diffDays == 0 触发到期当天邮件；
   - diffDays >= 1 记录/计划/借据转 OVERDUE；
   - user 为 null 时逾期状态仍落库、仅跳过邮件。
3. 每个修复点一次 git commit，最后输出排查汇总 md（`docs/testing/贷后催收与还款排查报告.md`）。

## 不做的事

- 不 push 远程。
- 不改动与本次 bug 无关的代码（不顺手重构）。
- 不修改数据库表结构。
