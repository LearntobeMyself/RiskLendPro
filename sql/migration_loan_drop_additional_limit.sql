-- 移除「额外批准额度」字段：审批放款不再使用该列。
-- 抵押类（房抵/车抵）按 评估价 × 抵押率 封顶放款；信用类超出剩余额度在受理时直接拒绝，不进入人工审批。
-- 执行：mysql -h HOST -u admin -p RiskLendPro < sql/migration_loan_drop_additional_limit.sql
ALTER TABLE `loan` DROP COLUMN `additional_limit`;
