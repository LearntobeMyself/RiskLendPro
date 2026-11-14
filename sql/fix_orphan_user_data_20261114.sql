-- =============================================================================
-- 清理「用户主记录已删除、业务行残留」的孤立数据
-- 背景：user 表为物理删除（无软删除列），risk_assessment / user_credit_limit 的
--       user_id 为普通 bigint、无外键，删除用户后业务行不会被级联清理。
--       典型症状：信用评分列表存在 userId=70，但 /admin/risk/users/70 与
--       /admin/users/70 均返回业务码 400「用户不存在」。
-- 由：后端交接清单 2026-11-14 第 1 项
-- 执行顺序：第 1 节只读核对 → 第 2 节备份 → 第 3 节清理 → 第 4 节复核
-- 注意：第 3 节为删除操作，请在确认第 1 节结果、且第 2 节备份成功后再执行。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 只读核对：统计各业务表中 user_id 在主表已不存在的行数
-- -----------------------------------------------------------------------------
SELECT 'risk_assessment'           AS tbl, COUNT(*) AS orphan_rows FROM risk_assessment ra
    LEFT JOIN `user` u ON u.id = ra.user_id WHERE u.id IS NULL
UNION ALL SELECT 'user_credit_limit',          COUNT(*) FROM user_credit_limit t
    LEFT JOIN `user` u ON u.id = t.user_id WHERE u.id IS NULL
UNION ALL SELECT 'user_credit_limit_history',  COUNT(*) FROM user_credit_limit_history t
    LEFT JOIN `user` u ON u.id = t.user_id WHERE u.id IS NULL
UNION ALL SELECT 'loan',                       COUNT(*) FROM loan t
    LEFT JOIN `user` u ON u.id = t.user_id WHERE u.id IS NULL
UNION ALL SELECT 'repayment_plan',             COUNT(*) FROM repayment_plan t
    LEFT JOIN `user` u ON u.id = t.user_id WHERE u.id IS NULL
UNION ALL SELECT 'user_b_card_log',            COUNT(*) FROM user_b_card_log t
    LEFT JOIN `user` u ON u.id = t.user_id WHERE u.id IS NULL
UNION ALL SELECT 'limit_adjust_log',           COUNT(*) FROM limit_adjust_log t
    LEFT JOIN `user` u ON u.id = t.user_id WHERE u.id IS NULL
UNION ALL SELECT 'risk_supplement_material',   COUNT(*) FROM risk_supplement_material t
    LEFT JOIN `user` u ON u.id = t.user_id WHERE u.id IS NULL
UNION ALL SELECT 'recommendation_impression',  COUNT(*) FROM recommendation_impression t
    LEFT JOIN `user` u ON u.id = t.user_id WHERE u.id IS NULL
UNION ALL SELECT 'user_product_event',         COUNT(*) FROM user_product_event t
    LEFT JOIN `user` u ON u.id = t.user_id WHERE u.id IS NULL
UNION ALL SELECT 'user_product_preference',    COUNT(*) FROM user_product_preference t
    LEFT JOIN `user` u ON u.id = t.user_id WHERE u.id IS NULL;

-- 明细（以 risk_assessment 为例，其余表同理）
SELECT ra.apply_id, ra.user_id, ra.name, ra.id_card, ra.status, ra.submit_time
FROM risk_assessment ra
LEFT JOIN `user` u ON u.id = ra.user_id
WHERE u.id IS NULL
ORDER BY ra.user_id;

-- -----------------------------------------------------------------------------
-- 2. 备份（只备份将被删除的行，便于回溯）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bak_orphan_risk_assessment_20261114 AS
SELECT ra.* FROM risk_assessment ra
LEFT JOIN `user` u ON u.id = ra.user_id
WHERE u.id IS NULL;

CREATE TABLE IF NOT EXISTS bak_orphan_user_credit_limit_20261114 AS
SELECT t.* FROM user_credit_limit t
LEFT JOIN `user` u ON u.id = t.user_id
WHERE u.id IS NULL;

-- -----------------------------------------------------------------------------
-- 3. 清理（确认第 2 节备份行数与第 1 节统计一致后再执行）
-- -----------------------------------------------------------------------------
DELETE ra FROM risk_assessment ra
LEFT JOIN `user` u ON u.id = ra.user_id
WHERE u.id IS NULL;

DELETE t FROM user_credit_limit t
LEFT JOIN `user` u ON u.id = t.user_id
WHERE u.id IS NULL;

-- 若第 1 节在 user_credit_limit_history / loan / repayment_plan 等表也查到孤立行，
-- 按同样模式补一条（先备份，再 DELETE ... LEFT JOIN ... WHERE u.id IS NULL）。
-- 有真实交易记录的表（loan / repayment_plan）请先与业务方确认是否归档而非删除。

-- -----------------------------------------------------------------------------
-- 4. 复核：孤立行应为 0，且列表接口不再返回该 userId
-- -----------------------------------------------------------------------------
SELECT 'risk_assessment' AS tbl, COUNT(*) AS remaining_orphans FROM risk_assessment ra
    LEFT JOIN `user` u ON u.id = ra.user_id WHERE u.id IS NULL
UNION ALL SELECT 'user_credit_limit', COUNT(*) FROM user_credit_limit t
    LEFT JOIN `user` u ON u.id = t.user_id WHERE u.id IS NULL;

SELECT COUNT(*) AS user70_assessments FROM risk_assessment WHERE user_id = 70;
SELECT COUNT(*) AS user70_limits      FROM user_credit_limit WHERE user_id = 70;
