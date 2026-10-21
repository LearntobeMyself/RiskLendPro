# Tasks：贷后催收与还款流程 Bug 修复

- [ ] T1 Fix 1（C1+C2）：RepaymentScheduleTask daysSinceDue + diffDays==-3 → commit
- [ ] T2 Fix 2（C3）：逾期落库解耦 user → commit
- [ ] T3 Fix 3（C4+C7+R1）：OverdueStateSync + 三处接入 → commit
- [ ] T4 Fix 4（C5）：OverdueLimitAdjustTask 事务边界修复 → commit
- [ ] T5 跑 loan-service 全量测试（55 用例基线须全过）
- [ ] T6 新增 RepaymentScheduleTaskTest（4 个用例）→ commit
- [ ] T7 写 docs/testing/贷后催收与还款排查报告.md → commit
- [ ] T8 向用户汇报全部发现（含 DB/实体类：本次无变化；不 push）
