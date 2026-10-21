# Tasks：贷后催收与还款流程 Bug 修复

- [x] T1 Fix 1（C1+C2）：RepaymentScheduleTask daysSinceDue + diffDays==-3 → commit `a75e248`
- [x] T2 Fix 2（C3）：逾期落库解耦 user → commit `a3663d3`
- [x] T3 Fix 3（C4+C7+R1）：OverdueStateSync + 三处接入 → commit `08610d0`
- [x] T4 Fix 4（C5）：OverdueLimitAdjustTask 事务边界修复 → commit `9175175` + 测试适配 `c0cf029`
- [x] T5 跑 loan-service 全量测试（64 用例全过，BUILD SUCCESS）
- [x] T6 新增 RepaymentScheduleTaskTest（4 个用例）→ commit `2cab79c`
- [x] T7 写 docs/testing/贷后催收与还款排查报告.md → commit `eec47c0`
- [x] T8 向用户汇报全部发现（含 DB/实体类：本次无变化；未 push）
