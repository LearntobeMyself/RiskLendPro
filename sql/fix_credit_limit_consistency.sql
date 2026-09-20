-- 修复额度一致性：确保 used_limit <= total_limit
-- 触发场景：早期审批/降额逻辑未强制保证总额度覆盖已用额度，导致部分用户出现 used > total
-- 修复策略：将 total_limit 提升到 used_limit，remaining_limit 置 0，并记录调额日志

START TRANSACTION;

UPDATE RiskLendPro.user_credit_limit
SET total_limit = used_limit,
    remaining_limit = 0,
    last_update_time = NOW()
WHERE used_limit > total_limit;

INSERT INTO RiskLendPro.limit_adjust_log (user_id, old_limit, new_limit, reason, operator_id, adjust_time)
SELECT user_id, total_limit, used_limit, '数据一致性修复：总额度调整为覆盖已用额度', 0, NOW()
FROM RiskLendPro.user_credit_limit
WHERE used_limit > total_limit;

COMMIT;
