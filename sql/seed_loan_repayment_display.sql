-- 借款记录展示优化：补全 repayment_plan.paid_amount / overdue_days 与已完成期次
-- 可重复执行
-- 执行：mysql -h HOST -u admin -p RiskLendPro < sql/seed_loan_repayment_display.sql

USE RiskLendPro;

SET NAMES utf8mb4;

-- 1. 从 repayment_record 汇总已还金额，回写 plan（paid_amount=0 的活跃/逾期计划）
UPDATE repayment_plan rp
INNER JOIN (
    SELECT plan_id, COALESCE(SUM(actual_amount), 0) AS paid_sum
    FROM repayment_record
    WHERE status = 'COMPLETED' AND actual_amount > 0
    GROUP BY plan_id
) t ON t.plan_id = rp.plan_id
SET rp.paid_amount = t.paid_sum,
    rp.remaining_amount = GREATEST(rp.total_amount - t.paid_sum, 0),
    rp.update_time = NOW()
WHERE rp.status IN ('ACTIVE', 'OVERDUE')
  AND (rp.paid_amount IS NULL OR rp.paid_amount = 0);

-- 2. 无 completed 记录时，按 current_period 估算（每期均摊）
UPDATE repayment_plan rp
INNER JOIN loan l ON l.loan_id = rp.loan_id
SET rp.paid_amount = ROUND(rp.total_amount / rp.total_periods * GREATEST(rp.current_period - 1, 0), 2),
    rp.remaining_amount = GREATEST(rp.total_amount - ROUND(rp.total_amount / rp.total_periods * GREATEST(rp.current_period - 1, 0), 2), 0),
    rp.update_time = NOW()
WHERE rp.status = 'ACTIVE'
  AND rp.current_period > 1
  AND (rp.paid_amount IS NULL OR rp.paid_amount = 0);

-- 3. 逾期计划：确保 overdue_days / overdue_level 有值
UPDATE repayment_plan rp
INNER JOIN loan l ON l.loan_id = rp.loan_id
INNER JOIN user u ON u.id = rp.user_id
SET rp.overdue_days = CASE
        WHEN u.phone_number = '13800138104' THEN 22
        WHEN u.phone_number = '13800138103' THEN 9
        WHEN u.phone_number = '13900150103' THEN 36
        WHEN rp.overdue_days IS NULL OR rp.overdue_days = 0 THEN 7
        ELSE rp.overdue_days
    END,
    rp.overdue_level = CASE
        WHEN u.phone_number = '13900150103' THEN 'M2'
        WHEN rp.overdue_days BETWEEN 1 AND 30 THEN 'M1'
        WHEN rp.overdue_days BETWEEN 31 AND 60 THEN 'M2'
        ELSE COALESCE(rp.overdue_level, 'M1')
    END,
    rp.status = 'OVERDUE',
    rp.update_time = NOW()
WHERE l.status = 'OVERDUE';

-- 4. 补写已完成期次的 actual_amount（PENDING 且 due_date 已过、非当前期）
UPDATE repayment_record rr
INNER JOIN repayment_plan rp ON rp.plan_id = rr.plan_id
SET rr.actual_amount = rr.amount,
    rr.repayment_date = DATE_SUB(rr.due_date, INTERVAL 1 DAY),
    rr.status = 'COMPLETED'
WHERE rr.period < rp.current_period
  AND rr.status = 'PENDING'
  AND (rr.actual_amount IS NULL OR rr.actual_amount = 0);

-- 5. 再次汇总回写 plan
UPDATE repayment_plan rp
INNER JOIN (
    SELECT plan_id, COALESCE(SUM(actual_amount), 0) AS paid_sum
    FROM repayment_record
    WHERE actual_amount > 0
    GROUP BY plan_id
) t ON t.plan_id = rp.plan_id
SET rp.paid_amount = t.paid_sum,
    rp.remaining_amount = GREATEST(rp.total_amount - t.paid_sum, 0),
    rp.update_time = NOW()
WHERE rp.status IN ('ACTIVE', 'OVERDUE', 'COMPLETED');

SELECT l.loan_id, u.real_name, l.status AS loan_status,
       rp.paid_amount, rp.overdue_days, rp.overdue_level, rp.current_period
FROM loan l
JOIN user u ON u.id = l.user_id
LEFT JOIN repayment_plan rp ON rp.loan_id = l.loan_id
WHERE l.status IN ('DISBURSED', 'OVERDUE')
ORDER BY l.loan_id
LIMIT 20;
