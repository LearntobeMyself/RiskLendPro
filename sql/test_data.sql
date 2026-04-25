-- 测试数据插入脚本
-- 注意：请在创建表结构后执行此脚本

-- 使用数据库
use RiskLendPro;

-- 1. 插入管理员数据
INSERT INTO `admin` (`username`, `password`, `phone_number`, `email`) VALUES
('admin1', '123456', '13800138001', 'admin1@example.com'),
('admin2', '123456', '13800138002', 'admin2@example.com');

-- 2. 插入用户数据
INSERT INTO `user` (`real_name`, `phone_number`, `email`, `id_card`, `password`, `role`, `assessment_status`) VALUES
('张三', '13900139001', 'zhangsan@example.com', '110101199001011234', '123456', 'USER', 'APPROVED'),
('李四', '13900139002', 'lisi@example.com', '110101199002022345', '123456', 'USER', 'NOT_ASSESSED'),
('王五', '13900139003', 'wangwu@example.com', '110101199003033456', '123456', 'USER', 'ASSESSING');

-- 3. 插入用户额度数据
INSERT INTO `user_credit_limit` (`user_id`, `total_limit`, `used_limit`, `remaining_limit`, `overdue_amount`, `has_overdue`) VALUES
(1, 50000.00, 10000.00, 40000.00, 0.00, FALSE),
(2, 30000.00, 0.00, 30000.00, 0.00, FALSE),
(3, 0.00, 0.00, 0.00, 0.00, FALSE);

-- 4. 插入风控评估申请数据
INSERT INTO `risk_assessment` (`apply_id`, `user_id`, `id_card`, `name`, `phone`, `email`, `gender`, `birthday`, `education`, `marriage`, `job_type`, `monthly_income`, `has_house`, `has_car`, `contact_phone`, `status`, `sys_decision`, `total_score`, `credit_limit`, `expire_date`, `submit_time`, `approval_time`, `is_final`, `operator_id`, `audit_remark`) VALUES
('L20240424001', 1, '110101199001011234', '张三', '13900139001', 'zhangsan@example.com', 1, '1990-01-01', '本科', '已婚', '企事业单位', '15000以上', TRUE, TRUE, '13800138000', 'FINAL_PASS', 'APPROVE', 85, 50000.00, '2025-04-24', '2024-04-20 10:00:00', '2024-04-20 14:00:00', TRUE, 1, '信用良好，审批通过'),
('L20240424002', 3, '110101199003033456', '王五', '13900139003', 'wangwu@example.com', 1, '1990-03-03', '大专', '单身', '私营企业', '8000-15000', FALSE, FALSE, '13800138000', 'MANUAL_REVIEW', 'REVIEW', 75, NULL, NULL, '2024-04-24 09:00:00', NULL, FALSE, NULL, NULL);

-- 5. 插入借款记录数据
-- 额度内借款（自动审批）
INSERT INTO `loan` (`user_id`, `amount`, `term_months`, `interest_rate`, `repayment_method`, `status`, `apply_time`, `approve_time`, `disbursement_time`, `auto_approved`, `reject_reason`, `additional_limit`, `operator_id`) VALUES
(1, 5000.00, 12, 0.05, '等额本息', 'DISBURRSED', '2024-04-22 10:00:00', '2024-04-22 10:00:00', '2024-04-22 10:00:00', TRUE, NULL, NULL, NULL),
-- 额度外借款（需要审批）
(1, 45000.00, 24, 0.05, '等额本息', 'PENDING_APPROVAL', '2024-04-24 11:00:00', NULL, NULL, FALSE, NULL, NULL, NULL),
-- 已审批通过的额度外借款
(1, 30000.00, 18, 0.05, '等额本金', 'APPROVED', '2024-04-21 14:00:00', '2024-04-21 15:00:00', '2024-04-21 15:00:00', FALSE, NULL, 10000.00, 1);

-- 6. 插入还款计划数据
INSERT INTO `repayment_plan` (`loan_id`, `user_id`, `total_amount`, `paid_amount`, `remaining_amount`, `total_periods`, `current_period`, `status`, `overdue_days`, `overdue_level`) VALUES
(1, 1, 5250.00, 0.00, 5250.00, 12, 0, 'ACTIVE', 0, 'N'),
(3, 1, 32250.00, 0.00, 32250.00, 18, 0, 'ACTIVE', 0, 'N');

-- 7. 插入还款记录数据
INSERT INTO `repayment_record` (`plan_id`, `loan_id`, `period`, `principal`, `interest`, `amount`, `actual_amount`, `due_date`, `repayment_date`, `status`) VALUES
(1, 1, 1, 416.67, 20.83, 437.50, NULL, '2024-05-22', NULL, 'PENDING'),
(1, 1, 2, 416.67, 20.83, 437.50, NULL, '2024-06-22', NULL, 'PENDING'),
(2, 3, 1, 1666.67, 125.00, 1791.67, NULL, '2024-05-21', NULL, 'PENDING');

-- 8. 插入额度调整记录数据
INSERT INTO `limit_adjust_log` (`user_id`, `old_limit`, `new_limit`, `reason`, `operator_id`) VALUES
(1, 40000.00, 50000.00, '信用良好，提升额度', 1);

-- 9. 插入模拟数据
INSERT INTO `mock_data` (`id_card`, `is_blacklist`, `overdue_count`, `loan_count`, `recent_query_count`) VALUES
('110101199001011234', FALSE, 0, 2, 3),
('110101199002022345', FALSE, 1, 1, 1),
('110101199003033456', FALSE, 0, 0, 2);

-- 10. 插入Vintage数据
INSERT INTO `vintage_data` (`month`, `disbursed_amount`, `m1_rate`, `m2_rate`, `m3_rate`) VALUES
('2024-01', 1000000.00, 0.02, 0.01, 0.005),
('2024-02', 1200000.00, 0.015, 0.008, 0.003),
('2024-03', 1500000.00, 0.018, 0.009, 0.004),
('2024-04', 1800000.00, 0.012, 0.006, 0.002);

-- 测试数据插入完成
SELECT '测试数据插入完成' AS message;