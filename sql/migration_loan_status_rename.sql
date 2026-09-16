-- 数据修正：贷款状态拼写 DISBURRSED -> DISBURSED（需在预发布/发布时手工执行）
-- 说明：无自动化迁移框架，本脚本供 DBA 手动执行。若已应用则忽略（UPDATE 幂等，无匹配影响 0 行）。
UPDATE `loan` SET `status` = 'DISBURSED' WHERE `status` = 'DISBURRSED';
-- 如还款记录也沿用该拼写（一般记录状态用 PENDING/COMPLETED，不涉及），据此保持一致。