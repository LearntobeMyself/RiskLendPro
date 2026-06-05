-- B 卡贷后演示数据（主库 risklendpro）
-- 前提：赵六 / 张小梁 / 吴九 均已完成 A 卡授信 + 至少一笔 DISBURSED 借款（B 卡已 activate）
-- 执行后：管理员端 GET /admin/b-card/monitor 刷新，或对吴九调用 POST /admin/b-card/recalculate/{userId}

USE risklendpro;

-- ---------------------------------------------------------------------------
-- 1. 赵六 13800138006 — 正常贷后（下一期距今天 > 7 天）
-- ---------------------------------------------------------------------------
UPDATE repayment_record rr
INNER JOIN repayment_plan rp ON rp.plan_id = rr.plan_id
INNER JOIN user u ON u.id = rp.user_id
SET rr.due_date = DATE_ADD(CURDATE(), INTERVAL 30 DAY),
    rr.status = 'PENDING',
    rp.status = 'ACTIVE',
    rp.overdue_days = 0,
    rp.overdue_level = 'N',
    rp.update_time = NOW()
WHERE u.phone_number = '13800138006'
  AND rr.period = rp.current_period;

UPDATE user_credit_limit l
INNER JOIN user u ON u.id = l.user_id
SET l.has_overdue = 0,
    l.last_update_time = NOW()
WHERE u.phone_number = '13800138006';

-- ---------------------------------------------------------------------------
-- 2. 张小梁 13800138003 — 快到期（3 天后到期，标签 DUE_SOON）
-- ---------------------------------------------------------------------------
UPDATE repayment_record rr
INNER JOIN repayment_plan rp ON rp.plan_id = rr.plan_id
INNER JOIN user u ON u.id = rp.user_id
SET rr.due_date = DATE_ADD(CURDATE(), INTERVAL 3 DAY),
    rr.status = 'PENDING',
    rp.status = 'ACTIVE',
    rp.overdue_days = 0,
    rp.overdue_level = 'N',
    rp.update_time = NOW()
WHERE u.phone_number = '13800138003'
  AND rr.period = rp.current_period;

UPDATE user_credit_limit l
INNER JOIN user u ON u.id = l.user_id
SET l.has_overdue = 0,
    l.last_update_time = NOW()
WHERE u.phone_number = '13800138003';

-- ---------------------------------------------------------------------------
-- 3. 吴九 13800138008 — 已逾期 M1（7 天，标签 OVERDUE；重算 B 分后 delta 下降）
-- ---------------------------------------------------------------------------
UPDATE repayment_record rr
INNER JOIN repayment_plan rp ON rp.plan_id = rr.plan_id
INNER JOIN user u ON u.id = rp.user_id
SET rr.due_date = DATE_SUB(CURDATE(), INTERVAL 7 DAY),
    rr.status = 'OVERDUE',
    rp.status = 'OVERDUE',
    rp.overdue_days = 7,
    rp.overdue_level = 'M1',
    rp.update_time = NOW()
WHERE u.phone_number = '13800138008'
  AND rr.period = rp.current_period;

UPDATE user_credit_limit l
INNER JOIN user u ON u.id = l.user_id
SET l.has_overdue = 1,
    l.last_update_time = NOW()
WHERE u.phone_number = '13800138008';

-- 验证
SELECT u.real_name, u.phone_number, l.b_card_enabled, l.b_score, l.has_overdue,
       rp.status AS plan_status, rp.overdue_level, rr.due_date, rr.status AS record_status
FROM user u
JOIN user_credit_limit l ON l.user_id = u.id
LEFT JOIN repayment_plan rp ON rp.user_id = u.id
LEFT JOIN repayment_record rr ON rr.plan_id = rp.plan_id AND rr.period = rp.current_period
WHERE u.phone_number IN ('13800138006', '13800138003', '13800138008')
ORDER BY u.phone_number;
