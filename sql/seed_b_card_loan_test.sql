-- B 卡借款专项测试 seed（配合 sql/B卡借款测试用例.md）
-- 前提：对应用户已完成 DISBURSED 借款（B-L01~B-L04）
-- 执行后：GET /admin/b-card/monitor 或 POST /admin/b-card/recalculate/{userId}

USE risklendpro;

-- 陈慧清 13800138101 — 正常贷后 NORMAL（30 天后到期）
UPDATE repayment_record rr
INNER JOIN repayment_plan rp ON rp.plan_id = rr.plan_id
INNER JOIN user u ON u.id = rp.user_id
SET rr.due_date = DATE_ADD(CURDATE(), INTERVAL 30 DAY),
    rr.status = 'PENDING',
    rp.status = 'ACTIVE',
    rp.overdue_days = 0,
    rp.overdue_level = 'N',
    rp.update_time = NOW()
WHERE u.phone_number = '13800138101'
  AND rr.period = rp.current_period;

UPDATE user_credit_limit l
INNER JOIN user u ON u.id = l.user_id
SET l.has_overdue = 0, l.last_update_time = NOW()
WHERE u.phone_number = '13800138101';

-- 韩立成 13800138102 — 快到期 DUE_SOON（3 天后）
UPDATE repayment_record rr
INNER JOIN repayment_plan rp ON rp.plan_id = rr.plan_id
INNER JOIN user u ON u.id = rp.user_id
SET rr.due_date = DATE_ADD(CURDATE(), INTERVAL 3 DAY),
    rr.status = 'PENDING',
    rp.status = 'ACTIVE',
    rp.overdue_days = 0,
    rp.overdue_level = 'N',
    rp.update_time = NOW()
WHERE u.phone_number = '13800138102'
  AND rr.period = rp.current_period;

UPDATE user_credit_limit l
INNER JOIN user u ON u.id = l.user_id
SET l.has_overdue = 0, l.last_update_time = NOW()
WHERE u.phone_number = '13800138102';

-- 许静雅 13800138103 — 今日到期 DUE_TODAY
UPDATE repayment_record rr
INNER JOIN repayment_plan rp ON rp.plan_id = rr.plan_id
INNER JOIN user u ON u.id = rp.user_id
SET rr.due_date = CURDATE(),
    rr.status = 'PENDING',
    rp.status = 'ACTIVE',
    rp.overdue_days = 0,
    rp.overdue_level = 'N',
    rp.update_time = NOW()
WHERE u.phone_number = '13800138103'
  AND rr.period = rp.current_period;

UPDATE user_credit_limit l
INNER JOIN user u ON u.id = l.user_id
SET l.has_overdue = 0, l.last_update_time = NOW()
WHERE u.phone_number = '13800138103';

-- 罗明远 13800138104 — 逾期 M1（7 天，重算 B 分后应下降）
UPDATE repayment_record rr
INNER JOIN repayment_plan rp ON rp.plan_id = rr.plan_id
INNER JOIN user u ON u.id = rp.user_id
SET rr.due_date = DATE_SUB(CURDATE(), INTERVAL 7 DAY),
    rr.status = 'OVERDUE',
    rp.status = 'OVERDUE',
    rp.overdue_days = 7,
    rp.overdue_level = 'M1',
    rp.update_time = NOW()
WHERE u.phone_number = '13800138104'
  AND rr.period = rp.current_period;

UPDATE user_credit_limit l
INNER JOIN user u ON u.id = l.user_id
SET l.has_overdue = 1, l.last_update_time = NOW()
WHERE u.phone_number = '13800138104';

SELECT u.real_name, u.phone_number, l.b_card_enabled, l.b_score, l.has_overdue,
       rp.status AS plan_status, rp.overdue_level, rr.due_date, rr.status AS record_status
FROM user u
JOIN user_credit_limit l ON l.user_id = u.id
LEFT JOIN repayment_plan rp ON rp.user_id = u.id
LEFT JOIN repayment_record rr ON rr.plan_id = rp.plan_id AND rr.period = rp.current_period
WHERE u.phone_number IN ('13800138101', '13800138102', '13800138103', '13800138104')
ORDER BY u.phone_number;
