-- 图片三人物 · 额度外贷款待审 seed（可重复执行）
-- 前提：已执行 seed_admin_demo_users.sql；B 卡测试用户 13800138xxx 已注册
-- 执行：mysql -h HOST -u admin -p RiskLendPro < sql/seed_loan_pending_image3.sql

USE RiskLendPro;

SET NAMES utf8mb4;

-- 清理本 seed 区间（保留原有 104/107 待审）
DELETE FROM loan WHERE loan_id BETWEEN 120 AND 130;

INSERT INTO `loan`
(`loan_id`, `user_id`, `amount`, `term_months`, `interest_rate`, `repayment_method`, `status`,
 `apply_time`, `approve_time`, `disbursement_time`, `auto_approved`, `reject_reason`, `additional_limit`, `operator_id`)
SELECT 120, u.id, 55000.00, 12, 0.0500, '等额本息', 'PENDING_APPROVAL',
       DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL, 0, NULL, 5000.00, NULL
FROM user u WHERE u.phone_number = '13900150101' LIMIT 1;

INSERT INTO `loan`
(`loan_id`, `user_id`, `amount`, `term_months`, `interest_rate`, `repayment_method`, `status`,
 `apply_time`, `approve_time`, `disbursement_time`, `auto_approved`, `reject_reason`, `additional_limit`, `operator_id`)
SELECT 121, u.id, 48000.00, 12, 0.0500, '等额本息', 'PENDING_APPROVAL',
       DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL, 0, NULL, 8000.00, NULL
FROM user u WHERE u.phone_number = '13900150109' LIMIT 1;

INSERT INTO `loan`
(`loan_id`, `user_id`, `amount`, `term_months`, `interest_rate`, `repayment_method`, `status`,
 `apply_time`, `approve_time`, `disbursement_time`, `auto_approved`, `reject_reason`, `additional_limit`, `operator_id`)
SELECT 122, u.id, 32000.00, 12, 0.0500, '等额本息', 'PENDING_APPROVAL',
       DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, NULL, 0, NULL, 7000.00, NULL
FROM user u WHERE u.phone_number = '13900150107' LIMIT 1;

INSERT INTO `loan`
(`loan_id`, `user_id`, `amount`, `term_months`, `interest_rate`, `repayment_method`, `status`,
 `apply_time`, `approve_time`, `disbursement_time`, `auto_approved`, `reject_reason`, `additional_limit`, `operator_id`)
SELECT 123, u.id, 25000.00, 12, 0.0500, '等额本息', 'PENDING_APPROVAL',
       DATE_SUB(NOW(), INTERVAL 11 DAY), NULL, NULL, 0, NULL, 3000.00, NULL
FROM user u WHERE u.phone_number = '13900150102' LIMIT 1;

INSERT INTO `loan`
(`loan_id`, `user_id`, `amount`, `term_months`, `interest_rate`, `repayment_method`, `status`,
 `apply_time`, `approve_time`, `disbursement_time`, `auto_approved`, `reject_reason`, `additional_limit`, `operator_id`)
SELECT 124, u.id, 28000.00, 12, 0.0500, '等额本息', 'PENDING_APPROVAL',
       DATE_SUB(NOW(), INTERVAL 14 DAY), NULL, NULL, 0, NULL, 6000.00, NULL
FROM user u WHERE u.phone_number = '13900150105' LIMIT 1;

INSERT INTO `loan`
(`loan_id`, `user_id`, `amount`, `term_months`, `interest_rate`, `repayment_method`, `status`,
 `apply_time`, `approve_time`, `disbursement_time`, `auto_approved`, `reject_reason`, `additional_limit`, `operator_id`)
SELECT 125, u.id, 45000.00, 12, 0.0500, '等额本息', 'PENDING_APPROVAL',
       DATE_SUB(NOW(), INTERVAL 18 DAY), NULL, NULL, 0, NULL, 10000.00, NULL
FROM user u WHERE u.phone_number = '13900150100' LIMIT 1;

INSERT INTO `loan`
(`loan_id`, `user_id`, `amount`, `term_months`, `interest_rate`, `repayment_method`, `status`,
 `apply_time`, `approve_time`, `disbursement_time`, `auto_approved`, `reject_reason`, `additional_limit`, `operator_id`)
SELECT 126, u.id, 35000.00, 12, 0.0500, '等额本息', 'PENDING_APPROVAL',
       DATE_SUB(NOW(), INTERVAL 21 DAY), NULL, NULL, 0, NULL, 5000.00, NULL
FROM user u WHERE u.phone_number = '13800138006' LIMIT 1;

INSERT INTO `loan`
(`loan_id`, `user_id`, `amount`, `term_months`, `interest_rate`, `repayment_method`, `status`,
 `apply_time`, `approve_time`, `disbursement_time`, `auto_approved`, `reject_reason`, `additional_limit`, `operator_id`)
SELECT 127, u.id, 42000.00, 12, 0.0500, '等额本息', 'PENDING_APPROVAL',
       DATE_SUB(NOW(), INTERVAL 24 DAY), NULL, NULL, 0, NULL, 8000.00, NULL
FROM user u WHERE u.phone_number = '13800138008' LIMIT 1;

INSERT INTO `loan`
(`loan_id`, `user_id`, `amount`, `term_months`, `interest_rate`, `repayment_method`, `status`,
 `apply_time`, `approve_time`, `disbursement_time`, `auto_approved`, `reject_reason`, `additional_limit`, `operator_id`)
SELECT 128, u.id, 50000.00, 12, 0.0500, '等额本息', 'PENDING_APPROVAL',
       DATE_SUB(NOW(), INTERVAL 27 DAY), NULL, NULL, 0, NULL, 12000.00, NULL
FROM user u WHERE u.phone_number = '13800138101' LIMIT 1;

INSERT INTO `loan`
(`loan_id`, `user_id`, `amount`, `term_months`, `interest_rate`, `repayment_method`, `status`,
 `apply_time`, `approve_time`, `disbursement_time`, `auto_approved`, `reject_reason`, `additional_limit`, `operator_id`)
SELECT 129, u.id, 38000.00, 12, 0.0500, '等额本息', 'PENDING_APPROVAL',
       DATE_SUB(NOW(), INTERVAL 29 DAY), NULL, NULL, 0, NULL, 15000.00, NULL
FROM user u WHERE u.phone_number = '13800138103' LIMIT 1;

INSERT INTO `loan`
(`loan_id`, `user_id`, `amount`, `term_months`, `interest_rate`, `repayment_method`, `status`,
 `apply_time`, `approve_time`, `disbursement_time`, `auto_approved`, `reject_reason`, `additional_limit`, `operator_id`)
SELECT 130, u.id, 46000.00, 12, 0.0500, '等额本息', 'PENDING_APPROVAL',
       DATE_SUB(NOW(), INTERVAL 30 DAY), NULL, NULL, 0, NULL, 10000.00, NULL
FROM user u WHERE u.phone_number = '13800138104' LIMIT 1;

SELECT l.loan_id, u.real_name, u.phone_number, l.amount, l.additional_limit, l.status, l.apply_time
FROM loan l
JOIN user u ON u.id = l.user_id
WHERE l.status = 'PENDING_APPROVAL'
ORDER BY l.apply_time DESC;

SELECT COUNT(*) AS pending_count FROM loan WHERE status = 'PENDING_APPROVAL';
