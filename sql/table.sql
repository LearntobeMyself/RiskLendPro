create database RiskLendPro;
use RiskLendPro;

CREATE TABLE `user` (
                        `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
                        `real_name` VARCHAR(50) NOT NULL COMMENT '用户姓名',
                        `phone_number` VARCHAR(20) NOT NULL UNIQUE COMMENT '手机号',
                        `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
                        `id_card` VARCHAR(18) NOT NULL UNIQUE COMMENT '身份证号',
                        `password` VARCHAR(255) NOT NULL COMMENT '密码',
                        `role` VARCHAR(20) DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
                        `assessment_status` VARCHAR(30) DEFAULT 'NOT_ASSESSED' COMMENT '评估状态: NOT_ASSESSED/ASSESSING/APPROVED',
                        `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE `admin` (
                         `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '管理员ID',
                         `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '管理员昵称',
                         `password` VARCHAR(255) NOT NULL COMMENT '密码',
                         `phone_number` VARCHAR(20) NOT NULL COMMENT '手机号',
                         `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
                         `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

CREATE TABLE `risk_assessment` (
                                   `apply_id` VARCHAR(30) PRIMARY KEY COMMENT '申请ID',
                                   `user_id` BIGINT COMMENT '用户ID',
                                   `id_card` VARCHAR(18) NOT NULL COMMENT '身份证号',
                                   `name` VARCHAR(50) NOT NULL COMMENT '姓名',
                                   `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
                                   `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
                                   `gender` TINYINT NOT NULL COMMENT '性别: 0-女, 1-男',
                                   `birthday` DATE NOT NULL COMMENT '出生日期',
                                   `education` VARCHAR(20) NOT NULL COMMENT '学历',
                                   `marriage` VARCHAR(10) NOT NULL COMMENT '婚姻状况',
                                   `job_type` VARCHAR(20) NOT NULL COMMENT '职业类型',
                                   `monthly_income` VARCHAR(20) NOT NULL COMMENT '月收入',
                                   `has_house` TINYINT(1) NOT NULL COMMENT '是否有房',
                                   `has_car` TINYINT(1) NOT NULL COMMENT '是否有车',
                                   `contact_phone` VARCHAR(20) NOT NULL COMMENT '紧急联系人电话',
                                   `status` VARCHAR(30) DEFAULT 'WAITING' COMMENT '状态: WAITING/SYSTEM_REJECT/MANUAL_REVIEW/FINAL_PASS/FINAL_REJECT',
                                   `sys_decision` VARCHAR(20) COMMENT '系统决策: APPROVE/REVIEW/REJECT',
                                   `total_score` INT COMMENT '风控总分',
                                   `credit_limit` DECIMAL(15,2) COMMENT '授信额度',
                                   `expire_date` DATE COMMENT '额度失效日期',
                                   `submit_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
                                   `approval_time` DATETIME COMMENT '审批时间',
                                   `is_final` TINYINT(1) DEFAULT FALSE COMMENT '是否终态',
                                   `operator_id` BIGINT COMMENT '审批操作员ID',
                                   `audit_remark` VARCHAR(500) COMMENT '审批评语',
                                   `supplement_status` VARCHAR(20) DEFAULT 'NONE' COMMENT '补充材料状态: NONE/REQUIRED/SUBMITTED',
                                   `supplement_requirements` JSON NULL COMMENT '需补充材料 JSON 数组',
                                   INDEX `idx_id_card` (`id_card`),
                                   INDEX `idx_user_id` (`user_id`),
                                   INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控评估申请表';

CREATE TABLE `risk_supplement_material` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `apply_id` VARCHAR(30) NOT NULL COMMENT '评估申请ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `material_type` VARCHAR(50) NOT NULL COMMENT '材料类型',
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `stored_path` VARCHAR(500) NOT NULL COMMENT '服务器存储路径',
    `file_size` BIGINT COMMENT '文件大小(字节)',
    `mime_type` VARCHAR(100) COMMENT 'MIME类型',
    `remark` VARCHAR(500) COMMENT '用户备注',
    `upload_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `expire_at` DATETIME NOT NULL COMMENT '过期时间',
    INDEX `idx_apply_id` (`apply_id`),
    INDEX `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控人工复核补充材料';

CREATE TABLE `loan` (
                        `loan_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '借款记录ID',
                        `user_id` BIGINT NOT NULL COMMENT '用户ID',
                        `amount` DECIMAL(15,2) NOT NULL COMMENT '借款金额',
                        `term_months` INT NOT NULL COMMENT '还款期限(月)',
                        `interest_rate` DECIMAL(5,4) NOT NULL COMMENT '利率',
                        `repayment_method` VARCHAR(20) NOT NULL COMMENT '还款方式: 等额本息/等额本金/先息后本',
                        `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING_APPROVAL/APPROVED/REJECTED/DISBURRSED/REPAID/OVERDUE',
                        `apply_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
                        `approve_time` DATETIME COMMENT '审批时间',
                        `disbursement_time` DATETIME COMMENT '放款时间',
                        `auto_approved` TINYINT(1) DEFAULT FALSE COMMENT '是否自动审批',
                        `reject_reason` VARCHAR(500) COMMENT '拒绝原因',
                        `additional_limit` DECIMAL(15,2) COMMENT '额外批准的额度',
                        `operator_id` BIGINT COMMENT '审批操作员ID',
                        `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        INDEX `idx_user_id` (`user_id`),
                        INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借款记录表';

CREATE TABLE `repayment_plan` (
                                  `plan_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '还款计划ID',
                                  `loan_id` BIGINT NOT NULL COMMENT '借款记录ID',
                                  `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                  `total_amount` DECIMAL(15,2) NOT NULL COMMENT '总金额(本息)',
                                  `paid_amount` DECIMAL(15,2) DEFAULT 0.00 COMMENT '已还金额',
                                  `remaining_amount` DECIMAL(15,2) NOT NULL COMMENT '剩余金额',
                                  `total_periods` INT NOT NULL COMMENT '总期数',
                                  `current_period` INT DEFAULT 0 COMMENT '当前期数',
                                  `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/COMPLETED/OVERDUE',
                                  `overdue_days` INT DEFAULT 0 COMMENT '逾期天数',
                                  `overdue_level` VARCHAR(10) DEFAULT 'N' COMMENT '逾期等级: N/M1/M2/M3',
                                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  INDEX `idx_loan_id` (`loan_id`),
                                  INDEX `idx_user_id` (`user_id`),
                                  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='还款计划表';

CREATE TABLE `repayment_record` (
                                    `record_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '还款记录ID',
                                    `plan_id` BIGINT NOT NULL COMMENT '还款计划ID',
                                    `loan_id` BIGINT NOT NULL COMMENT '借款记录ID',
                                    `period` INT NOT NULL COMMENT '期数',
                                    `principal` DECIMAL(15,2) NOT NULL COMMENT '本金',
                                    `interest` DECIMAL(15,2) NOT NULL COMMENT '利息',
                                    `amount` DECIMAL(15,2) NOT NULL COMMENT '还款金额',
                                    `actual_amount` DECIMAL(15,2) COMMENT '实际还款金额',
                                    `due_date` DATE NOT NULL COMMENT '到期日期',
                                    `repayment_date` DATETIME COMMENT '实际还款时间',
                                    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/COMPLETED/OVERDUE',
                                    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    INDEX `idx_plan_id` (`plan_id`),
                                    INDEX `idx_loan_id` (`loan_id`),
                                    INDEX `idx_due_date` (`due_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='还款记录表';

CREATE TABLE `user_credit_limit` (
                                     `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
                                     `user_id` BIGINT NOT NULL UNIQUE COMMENT '用户ID',
                                     `total_limit` DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '总额度',
                                     `used_limit` DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '已用额度',
                                     `remaining_limit` DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '剩余额度',
                                     `overdue_amount` DECIMAL(15,2) DEFAULT 0.00 COMMENT '逾期金额',
                                     `has_overdue` TINYINT(1) DEFAULT FALSE COMMENT '是否有逾期',
                                     `b_card_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'B卡是否已启动',
                                     `b_score` DECIMAL(6,1) NULL COMMENT '最新B卡综合分',
                                     `b_score_updated_at` DATETIME NULL COMMENT 'B分更新时间',
                                     `last_update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
                                     INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户额度表';

CREATE TABLE `limit_adjust_log` (
                                    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
                                    `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                    `old_limit` DECIMAL(15,2) NOT NULL COMMENT '原额度',
                                    `new_limit` DECIMAL(15,2) NOT NULL COMMENT '新额度',
                                    `reason` VARCHAR(500) NOT NULL COMMENT '调整原因',
                                    `operator_id` BIGINT NOT NULL COMMENT '操作员ID',
                                    `adjust_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '调整时间',
                                    INDEX `idx_user_id` (`user_id`),
                                    INDEX `idx_operator_id` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度调整记录表';

CREATE TABLE IF NOT EXISTS `user_b_card_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `base_score` DECIMAL(6,1) NULL COMMENT 'HC基线B分',
    `delta_score` DECIMAL(6,1) NULL COMMENT '本项目还款动态修正',
    `final_score` DECIMAL(6,1) NULL COMMENT '最终B分',
    `live_features` JSON NULL COMMENT '实时还款特征快照',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B卡评分历史';


CREATE TABLE `vintage_data` (
                                `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
                                `month` VARCHAR(10) NOT NULL UNIQUE COMMENT '月份: YYYY-MM',
                                `disbursed_amount` DECIMAL(18,2) NOT NULL COMMENT '放款金额',
                                `m1_rate` DECIMAL(8,6) DEFAULT 0 COMMENT 'M1逾期率',
                                `m2_rate` DECIMAL(8,6) DEFAULT 0 COMMENT 'M2逾期率',
                                `m3_rate` DECIMAL(8,6) DEFAULT 0 COMMENT 'M3逾期率',
                                `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Vintage数据表';

