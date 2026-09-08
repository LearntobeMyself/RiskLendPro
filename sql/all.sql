CREATE TABLE `admin` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
  `username` varchar(50) NOT NULL COMMENT '管理员昵称',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `phone_number` varchar(20) NOT NULL COMMENT '手机号',
  `email` varchar(100) NOT NULL COMMENT '邮箱',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员表'

CREATE TABLE `limit_adjust_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `old_limit` decimal(15,2) NOT NULL COMMENT '原额度',
  `new_limit` decimal(15,2) NOT NULL COMMENT '新额度',
  `reason` varchar(500) NOT NULL COMMENT '调整原因',
  `operator_id` bigint NOT NULL COMMENT '操作员ID',
  `adjust_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '调整时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_operator_id` (`operator_id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='额度调整记录表'

CREATE TABLE `loan` (
  `loan_id` bigint NOT NULL AUTO_INCREMENT COMMENT '借款记录ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `amount` decimal(15,2) NOT NULL COMMENT '借款金额',
  `term_months` int NOT NULL COMMENT '还款期限(月)',
  `interest_rate` decimal(5,4) NOT NULL COMMENT '利率',
  `repayment_method` varchar(20) NOT NULL COMMENT '还款方式: 等额本息/等额本金/先息后本',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '状态: PENDING_APPROVAL/APPROVED/REJECTED/DISBURRSED/REPAID/OVERDUE',
  `apply_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `disbursement_time` datetime DEFAULT NULL COMMENT '放款时间',
  `auto_approved` tinyint(1) DEFAULT '0' COMMENT '是否自动审批',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '拒绝原因',
  `additional_limit` decimal(15,2) DEFAULT NULL COMMENT '额外批准的额度',
  `operator_id` bigint DEFAULT NULL COMMENT '审批操作员ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`loan_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='借款记录表'

CREATE TABLE `repayment_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT COMMENT '还款记录ID',
  `plan_id` bigint NOT NULL COMMENT '还款计划ID',
  `loan_id` bigint NOT NULL COMMENT '借款记录ID',
  `period` int NOT NULL COMMENT '期数',
  `principal` decimal(15,2) NOT NULL COMMENT '本金',
  `interest` decimal(15,2) NOT NULL COMMENT '利息',
  `amount` decimal(15,2) NOT NULL COMMENT '还款金额',
  `actual_amount` decimal(15,2) DEFAULT NULL COMMENT '实际还款金额',
  `due_date` date NOT NULL COMMENT '到期日期',
  `repayment_date` datetime DEFAULT NULL COMMENT '实际还款时间',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/COMPLETED/OVERDUE',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`record_id`),
  KEY `idx_plan_id` (`plan_id`),
  KEY `idx_loan_id` (`loan_id`),
  KEY `idx_due_date` (`due_date`)
) ENGINE=InnoDB AUTO_INCREMENT=44 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='还款记录表'

CREATE TABLE `risk_assessment` (
  `apply_id` varchar(30) NOT NULL COMMENT '申请ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `id_card` varchar(18) NOT NULL COMMENT '身份证号',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `phone` varchar(20) NOT NULL COMMENT '手机号',
  `email` varchar(100) NOT NULL COMMENT '邮箱',
  `gender` tinyint NOT NULL COMMENT '性别: 0-女, 1-男',
  `birthday` date NOT NULL COMMENT '出生日期',
  `education` varchar(20) NOT NULL COMMENT '学历',
  `marriage` varchar(10) NOT NULL COMMENT '婚姻状况',
  `job_type` varchar(20) NOT NULL COMMENT '职业类型',
  `monthly_income` varchar(20) NOT NULL COMMENT '月收入',
  `has_house` tinyint(1) NOT NULL COMMENT '是否有房',
  `has_car` tinyint(1) NOT NULL COMMENT '是否有车',
  `contact_phone` varchar(20) NOT NULL COMMENT '紧急联系人电话',
  `status` varchar(30) DEFAULT 'WAITING' COMMENT '状态: WAITING/SYSTEM_REJECT/MANUAL_REVIEW/FINAL_PASS/FINAL_REJECT',
  `sys_decision` varchar(20) DEFAULT NULL COMMENT '系统决策: APPROVE/REVIEW/REJECT',
  `total_score` int DEFAULT NULL COMMENT '风控总分',
  `credit_limit` decimal(15,2) DEFAULT NULL COMMENT '授信额度',
  `expire_date` date DEFAULT NULL COMMENT '额度失效日期',
  `submit_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `approval_time` datetime DEFAULT NULL COMMENT '审批时间',
  `is_final` tinyint(1) DEFAULT '0' COMMENT '是否终态',
  `operator_id` bigint DEFAULT NULL COMMENT '审批操作员ID',
  `audit_remark` varchar(500) DEFAULT NULL COMMENT '审批评语',
  `supplement_status` varchar(20) DEFAULT 'NONE' COMMENT 'NONE/REQUIRED/SUBMITTED',
  `supplement_requirements` json DEFAULT NULL COMMENT '需补充材料 JSON 数组',
  PRIMARY KEY (`apply_id`),
  KEY `idx_id_card` (`id_card`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控评估申请表'

CREATE TABLE `risk_supplement_material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `apply_id` varchar(30) NOT NULL COMMENT '评估申请ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `material_type` varchar(50) NOT NULL COMMENT '材料类型',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `stored_path` varchar(500) NOT NULL COMMENT '服务器存储路径',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `mime_type` varchar(100) DEFAULT NULL COMMENT 'MIME类型',
  `remark` varchar(500) DEFAULT NULL COMMENT '用户备注',
  `upload_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `expire_at` datetime NOT NULL COMMENT '过期时间',
  PRIMARY KEY (`id`),
  KEY `idx_apply_id` (`apply_id`),
  KEY `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控人工复核补充材料'

CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `real_name` varchar(50) NOT NULL COMMENT '用户姓名',
  `phone_number` varchar(20) NOT NULL COMMENT '手机号',
  `email` varchar(100) NOT NULL COMMENT '邮箱',
  `id_card` varchar(18) NOT NULL COMMENT '身份证号',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `role` varchar(20) DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
  `assessment_status` varchar(30) DEFAULT 'NOT_ASSESSED' COMMENT '评估状态: NOT_ASSESSED/ASSESSING/APPROVED',
  `account_status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED/FROZEN',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `phone_number` (`phone_number`),
  UNIQUE KEY `id_card` (`id_card`)
) ENGINE=InnoDB AUTO_INCREMENT=71 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表'

CREATE TABLE `user_b_card_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `base_score` decimal(6,1) DEFAULT NULL COMMENT 'HC基线B分',
  `delta_score` decimal(6,1) DEFAULT NULL COMMENT '本项目还款动态修正',
  `final_score` decimal(6,1) DEFAULT NULL COMMENT '最终B分',
  `live_features` json DEFAULT NULL COMMENT '实时还款特征快照',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='B卡评分历史'

CREATE TABLE `user_credit_limit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `total_limit` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '总额度',
  `used_limit` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '已用额度',
  `remaining_limit` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '剩余额度',
  `overdue_amount` decimal(15,2) DEFAULT '0.00' COMMENT '逾期金额',
  `has_overdue` tinyint(1) DEFAULT '0' COMMENT '是否有逾期',
  `last_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `b_card_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'B卡是否已启动',
  `b_score` decimal(6,1) DEFAULT NULL COMMENT '最新B卡综合分',
  `b_score_updated_at` datetime DEFAULT NULL COMMENT 'B分更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户额度表'

CREATE TABLE `vintage_data` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `month` varchar(10) NOT NULL COMMENT '月份: YYYY-MM',
  `disbursed_amount` decimal(18,2) NOT NULL COMMENT '放款金额',
  `m1_rate` decimal(8,6) DEFAULT '0.000000' COMMENT 'M1逾期率',
  `m2_rate` decimal(8,6) DEFAULT '0.000000' COMMENT 'M2逾期率',
  `m3_rate` decimal(8,6) DEFAULT '0.000000' COMMENT 'M3逾期率',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `month` (`month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Vintage数据表'


