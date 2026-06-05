# 金融系统实体类与数据库设计文档

> **文档版本**：v2.0（对齐 `org.example.risklendpro.entity` / `entity.credit` 与 [`sql/table.sql`](../sql/table.sql)、[`load_to_mysql.py`](../risk-assessment/load_to_mysql.py)）

**双库说明**：

| 库 | 用途 | 实体包 |
|----|------|--------|
| `RiskLendPro` | 主业务 | `org.example.risklendpro.entity` |
| `credit_data_db` | Python 训练入库 / Java 只读征信 | `org.example.risklendpro.entity.credit` |

---

## 1. 数据库表结构设计（主库 `RiskLendPro`）

### 1.1 用户表 (`user`)

| 字段名                 | 数据类型           | 约束                                                      | 描述                                     |
| ------------------- | -------------- | ------------------------------------------------------- | -------------------------------------- |
| `id`                | `BIGINT`       | `PRIMARY KEY AUTO_INCREMENT`                            | 用户ID                                   |
| `real_name`         | `VARCHAR(50)`  | `NOT NULL`                                              | 用户姓名                                   |
| `phone_number`      | `VARCHAR(20)`  | `NOT NULL UNIQUE`                                       | 手机号                                    |
| `email`             | `VARCHAR(100)` | `NOT NULL`                                              | 邮箱                                     |
| `id_card`           | `VARCHAR(18)`  | `NOT NULL UNIQUE`                                       | 身份证号                                   |
| `password`          | `VARCHAR(255)` | `NOT NULL`                                              | 密码                                     |
| `role`              | `VARCHAR(20)`  | `DEFAULT 'USER'`                                        | 角色: USER/ADMIN                         |
| `assessment_status` | `VARCHAR(30)`  | `DEFAULT 'NOT_ASSESSED'`                                | 评估状态: NOT\_ASSESSED/ASSESSING/APPROVED |
| `create_time`       | `DATETIME`     | `DEFAULT CURRENT_TIMESTAMP`                             | 创建时间                                   |
| `update_time`       | `DATETIME`     | `DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间                                   |

**SQL建表语句**:

```sql
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
```

### 1.2 管理员表 (`admin`)

| 字段名            | 数据类型           | 约束                                                      | 描述    |
| -------------- | -------------- | ------------------------------------------------------- | ----- |
| `id`           | `BIGINT`       | `PRIMARY KEY AUTO_INCREMENT`                            | 管理员ID |
| `username`     | `VARCHAR(50)`  | `NOT NULL UNIQUE`                                       | 管理员昵称 |
| `password`     | `VARCHAR(255)` | `NOT NULL`                                              | 密码    |
| `phone_number` | `VARCHAR(20)`  | `NOT NULL`                                              | 手机号   |
| `email`        | `VARCHAR(100)` | `NOT NULL`                                              | 邮箱    |
| `create_time`  | `DATETIME`     | `DEFAULT CURRENT_TIMESTAMP`                             | 创建时间  |
| `update_time`  | `DATETIME`     | `DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间  |

**SQL建表语句**:

```sql
CREATE TABLE `admin` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '管理员ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '管理员昵称',
    `password` VARCHAR(255) NOT NULL COMMENT '密码',
    `phone_number` VARCHAR(20) NOT NULL COMMENT '手机号',
    `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';
```

### 1.3 风控评估申请表 (`risk_assessment`)

| 字段名              | 数据类型            | 约束                                      | 描述                                                                  |
| ---------------- | --------------- | --------------------------------------- | ------------------------------------------------------------------- |
| `apply_id`       | `VARCHAR(30)`   | `PRIMARY KEY`                           | 申请ID                                                                |
| `user_id`        | `BIGINT`        | `COMMENT '用户ID'`                        | 用户ID                                                                |
| `id_card`        | `VARCHAR(18)`   | `NOT NULL`                              | 身份证号                                                                |
| `name`           | `VARCHAR(50)`   | `NOT NULL`                              | 姓名                                                                  |
| `phone`          | `VARCHAR(20)`   | `NOT NULL`                              | 手机号                                                                 |
| `email`          | `VARCHAR(100)`  | `NOT NULL`                              | 邮箱                                                                  |
| `gender`         | `TINYINT`       | `NOT NULL`                              | 性别: 0-女, 1-男                                                        |
| `birthday`       | `DATE`          | `NOT NULL`                              | 出生日期                                                                |
| `education`      | `VARCHAR(20)`   | `NOT NULL`                              | 学历                                                                  |
| `marriage`       | `VARCHAR(10)`   | `NOT NULL`                              | 婚姻状况                                                                |
| `job_type`       | `VARCHAR(20)`   | `NOT NULL`                              | 职业类型                                                                |
| `monthly_income` | `VARCHAR(20)`   | `NOT NULL`                              | 月收入                                                                 |
| `has_house`      | `TINYINT(1)`    | `NOT NULL`                              | 是否有房                                                                |
| `has_car`        | `TINYINT(1)`    | `NOT NULL`                              | 是否有车                                                                |
| `contact_phone`  | `VARCHAR(20)`   | `NOT NULL`                              | 紧急联系人电话                                                             |
| `status`         | `VARCHAR(30)`   | `DEFAULT 'WAITING'`                     | 状态: WAITING/SYSTEM\_REJECT/MANUAL\_REVIEW/FINAL\_PASS/FINAL\_REJECT |
| `sys_decision`   | `VARCHAR(20)`   | `COMMENT '系统决策: APPROVE/REVIEW/REJECT'` | 系统决策                                                                |
| `total_score`    | `INT`           | `COMMENT '风控总分'`                        | 风控总分                                                                |
| `credit_limit`   | `DECIMAL(15,2)` | `COMMENT '授信额度'`                        | 授信额度                                                                |
| `expire_date`    | `DATE`          | `COMMENT '额度失效日期'`                      | 额度失效日期                                                              |
| `submit_time`    | `DATETIME`      | `DEFAULT CURRENT_TIMESTAMP`             | 提交时间                                                                |
| `approval_time`  | `DATETIME`      | `COMMENT '审批时间'`                        | 审批时间                                                                |
| `is_final`       | `TINYINT(1)`    | `DEFAULT FALSE`                         | 是否终态                                                                |
| `operator_id`    | `BIGINT`        | `COMMENT '审批操作员ID'`                     | 审批操作员ID                                                             |
| `audit_remark`   | `VARCHAR(500)`  | `COMMENT '审批评语'`                        | 审批评语                                                                |
| `supplement_status` | `VARCHAR(20)` | `DEFAULT 'NONE'` | 补充材料状态: NONE / REQUIRED / SUBMITTED |
| `supplement_requirements` | `JSON` | `NULL` | 需补充材料 JSON 数组 |

**SQL建表语句**:

```sql
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
```

### 1.4 借款记录表 (`loan`)

| 字段名                 | 数据类型            | 约束                                                      | 描述                                                                |
| ------------------- | --------------- | ------------------------------------------------------- | ----------------------------------------------------------------- |
| `loan_id`           | `BIGINT`        | `PRIMARY KEY AUTO_INCREMENT`                            | 借款记录ID                                                            |
| `user_id`           | `BIGINT`        | `NOT NULL`                                              | 用户ID                                                              |
| `amount`            | `DECIMAL(15,2)` | `NOT NULL`                                              | 借款金额                                                              |
| `term_months`       | `INT`           | `NOT NULL`                                              | 还款期限(月)                                                           |
| `interest_rate`     | `DECIMAL(5,4)`  | `NOT NULL`                                              | 利率                                                                |
| `repayment_method`  | `VARCHAR(20)`   | `NOT NULL`                                              | 还款方式: 等额本息/等额本金/先息后本                                              |
| `status`            | `VARCHAR(20)`   | `DEFAULT 'PENDING'`                                     | 状态: PENDING\_APPROVAL/APPROVED/REJECTED/DISBURRSED/REPAID/OVERDUE |
| `apply_time`        | `DATETIME`      | `DEFAULT CURRENT_TIMESTAMP`                             | 申请时间                                                              |
| `approve_time`      | `DATETIME`      | `COMMENT '审批时间'`                                        | 审批时间                                                              |
| `disbursement_time` | `DATETIME`      | `COMMENT '放款时间'`                                        | 放款时间                                                              |
| `auto_approved`     | `TINYINT(1)`    | `DEFAULT FALSE`                                         | 是否自动审批                                                            |
| `reject_reason`     | `VARCHAR(500)`  | `COMMENT '拒绝原因'`                                        | 拒绝原因                                                              |
| `additional_limit`  | `DECIMAL(15,2)` | `COMMENT '额外批准的额度'`                                     | 额外批准的额度                                                           |
| `operator_id`       | `BIGINT`        | `COMMENT '审批操作员ID'`                                     | 审批操作员ID                                                           |
| `create_time`       | `DATETIME`      | `DEFAULT CURRENT_TIMESTAMP`                             | 创建时间                                                              |
| `update_time`       | `DATETIME`      | `DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间                                                              |

**SQL建表语句**:

```sql
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
```

### 1.5 还款计划表 (`repayment_plan`)

| 字段名                | 数据类型            | 约束                                                      | 描述                           |
| ------------------ | --------------- | ------------------------------------------------------- | ---------------------------- |
| `plan_id`          | `BIGINT`        | `PRIMARY KEY AUTO_INCREMENT`                            | 还款计划ID                       |
| `loan_id`          | `BIGINT`        | `NOT NULL`                                              | 借款记录ID                       |
| `user_id`          | `BIGINT`        | `NOT NULL`                                              | 用户ID                         |
| `total_amount`     | `DECIMAL(15,2)` | `NOT NULL`                                              | 总金额(本息)                      |
| `paid_amount`      | `DECIMAL(15,2)` | `DEFAULT 0.00`                                          | 已还金额                         |
| `remaining_amount` | `DECIMAL(15,2)` | `NOT NULL`                                              | 剩余金额                         |
| `total_periods`    | `INT`           | `NOT NULL`                                              | 总期数                          |
| `current_period`   | `INT`           | `DEFAULT 0`                                             | 当前期数                         |
| `status`           | `VARCHAR(20)`   | `DEFAULT 'ACTIVE'`                                      | 状态: ACTIVE/COMPLETED/OVERDUE |
| `overdue_days`     | `INT`           | `DEFAULT 0`                                             | 逾期天数                         |
| `overdue_level`    | `VARCHAR(10)`   | `DEFAULT 'N'`                                           | 逾期等级: N/M1/M2/M3             |
| `create_time`      | `DATETIME`      | `DEFAULT CURRENT_TIMESTAMP`                             | 创建时间                         |
| `update_time`      | `DATETIME`      | `DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间                         |

**SQL建表语句**:

```sql
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
```

### 1.6 还款记录表 (`repayment_record`)

| 字段名              | 数据类型            | 约束                           | 描述                            |
| ---------------- | --------------- | ---------------------------- | ----------------------------- |
| `record_id`      | `BIGINT`        | `PRIMARY KEY AUTO_INCREMENT` | 还款记录ID                        |
| `plan_id`        | `BIGINT`        | `NOT NULL`                   | 还款计划ID                        |
| `loan_id`        | `BIGINT`        | `NOT NULL`                   | 借款记录ID                        |
| `period`         | `INT`           | `NOT NULL`                   | 期数                            |
| `principal`      | `DECIMAL(15,2)` | `NOT NULL`                   | 本金                            |
| `interest`       | `DECIMAL(15,2)` | `NOT NULL`                   | 利息                            |
| `amount`         | `DECIMAL(15,2)` | `NOT NULL`                   | 还款金额                          |
| `actual_amount`  | `DECIMAL(15,2)` | `COMMENT '实际还款金额'`           | 实际还款金额                        |
| `due_date`       | `DATE`          | `NOT NULL`                   | 到期日期                          |
| `repayment_date` | `DATETIME`      | `COMMENT '实际还款时间'`           | 实际还款时间                        |
| `status`         | `VARCHAR(20)`   | `DEFAULT 'PENDING'`          | 状态: PENDING/COMPLETED/OVERDUE |
| `create_time`    | `DATETIME`      | `DEFAULT CURRENT_TIMESTAMP`  | 创建时间                          |

**SQL建表语句**:

```sql
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
```

### 1.7 用户额度表 (`user_credit_limit`)

| 字段名                | 数据类型            | 约束                                                      | 描述     |
| ------------------ | --------------- | ------------------------------------------------------- | ------ |
| `id`               | `BIGINT`        | `PRIMARY KEY AUTO_INCREMENT`                            | ID     |
| `user_id`          | `BIGINT`        | `NOT NULL UNIQUE`                                       | 用户ID   |
| `total_limit`      | `DECIMAL(15,2)` | `NOT NULL DEFAULT 0.00`                                 | 总额度    |
| `used_limit`       | `DECIMAL(15,2)` | `NOT NULL DEFAULT 0.00`                                 | 已用额度   |
| `remaining_limit`  | `DECIMAL(15,2)` | `NOT NULL DEFAULT 0.00`                                 | 剩余额度   |
| `overdue_amount`   | `DECIMAL(15,2)` | `DEFAULT 0.00`                                          | 逾期金额   |
| `has_overdue`      | `TINYINT(1)`    | `DEFAULT FALSE`                                         | 是否有逾期  |
| `b_card_enabled`   | `TINYINT(1)`    | `NOT NULL DEFAULT 0`                                    | B 卡是否已启动 |
| `b_score`          | `DECIMAL(6,1)`  | `NULL`                                                  | 最新 B 卡综合分 |
| `b_score_updated_at` | `DATETIME`    | `NULL`                                                  | B 分更新时间 |
| `last_update_time` | `DATETIME`      | `DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 最后更新时间 |

**SQL建表语句**:

```sql
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
```

### 1.8 额度调整记录表 (`limit_adjust_log`)

| 字段名           | 数据类型            | 约束                           | 描述    |
| ------------- | --------------- | ---------------------------- | ----- |
| `id`          | `BIGINT`        | `PRIMARY KEY AUTO_INCREMENT` | 记录ID  |
| `user_id`     | `BIGINT`        | `NOT NULL`                   | 用户ID  |
| `old_limit`   | `DECIMAL(15,2)` | `NOT NULL`                   | 原额度   |
| `new_limit`   | `DECIMAL(15,2)` | `NOT NULL`                   | 新额度   |
| `reason`      | `VARCHAR(500)`  | `NOT NULL`                   | 调整原因  |
| `operator_id` | `BIGINT`        | `NOT NULL`                   | 操作员ID |
| `adjust_time` | `DATETIME`      | `DEFAULT CURRENT_TIMESTAMP`  | 调整时间  |

**SQL建表语句**:

```sql
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
```

### 1.9 风控补充材料表 (`risk_supplement_material`)

人工复核时用户上传的补充材料；过期由 `SupplementMaterialCleanupJob` 清理。

| 字段名 | 数据类型 | 约束 | 描述 |
|--------|----------|------|------|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | 主键 |
| `apply_id` | `VARCHAR(30)` | `NOT NULL` | 评估申请 ID |
| `user_id` | `BIGINT` | `NOT NULL` | 用户 ID |
| `material_type` | `VARCHAR(50)` | `NOT NULL` | 材料类型 |
| `original_name` | `VARCHAR(255)` | `NOT NULL` | 原始文件名 |
| `stored_path` | `VARCHAR(500)` | `NOT NULL` | 服务器存储路径 |
| `file_size` | `BIGINT` | | 文件大小（字节） |
| `mime_type` | `VARCHAR(100)` | | MIME 类型 |
| `remark` | `VARCHAR(500)` | | 用户备注 |
| `upload_time` | `DATETIME` | `DEFAULT CURRENT_TIMESTAMP` | 上传时间 |
| `expire_at` | `DATETIME` | `NOT NULL` | 过期时间 |

实体：[`RiskSupplementMaterial.java`](../src/main/java/org/example/risklendpro/entity/RiskSupplementMaterial.java)

### 1.10 B 卡评分历史表 (`user_b_card_log`)

| 字段名 | 数据类型 | 约束 | 描述 |
|--------|----------|------|------|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | 主键 |
| `user_id` | `BIGINT` | `NOT NULL` | 用户 ID |
| `base_score` | `DECIMAL(6,1)` | `NULL` | HC 基线 B 分 |
| `delta_score` | `DECIMAL(6,1)` | `NULL` | 本项目还款动态修正 |
| `final_score` | `DECIMAL(6,1)` | `NULL` | 最终 B 分 |
| `live_features` | `JSON` | `NULL` | 实时还款特征快照 |
| `created_at` | `DATETIME` | `DEFAULT CURRENT_TIMESTAMP` | 记录时间 |

实体：[`UserBCardLog.java`](../src/main/java/org/example/risklendpro/entity/UserBCardLog.java)

### 1.11 Vintage数据表 (`vintage_data`)

| 字段名                | 数据类型            | 约束                           | 描述          |
| ------------------ | --------------- | ---------------------------- | ----------- |
| `id`               | `BIGINT`        | `PRIMARY KEY AUTO_INCREMENT` | ID          |
| `month`            | `VARCHAR(10)`   | `NOT NULL UNIQUE`            | 月份: YYYY-MM |
| `disbursed_amount` | `DECIMAL(18,2)` | `NOT NULL`                   | 放款金额        |
| `m1_rate`          | `DECIMAL(8,6)`  | `DEFAULT 0`                  | M1逾期率       |
| `m2_rate`          | `DECIMAL(8,6)`  | `DEFAULT 0`                  | M2逾期率       |
| `m3_rate`          | `DECIMAL(8,6)`  | `DEFAULT 0`                  | M3逾期率       |
| `create_time`      | `DATETIME`      | `DEFAULT CURRENT_TIMESTAMP`  | 创建时间        |

**SQL建表语句**:

```sql
CREATE TABLE `vintage_data` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    `month` VARCHAR(10) NOT NULL UNIQUE COMMENT '月份: YYYY-MM',
    `disbursed_amount` DECIMAL(18,2) NOT NULL COMMENT '放款金额',
    `m1_rate` DECIMAL(8,6) DEFAULT 0 COMMENT 'M1逾期率',
    `m2_rate` DECIMAL(8,6) DEFAULT 0 COMMENT 'M2逾期率',
    `m3_rate` DECIMAL(8,6) DEFAULT 0 COMMENT 'M3逾期率',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Vintage数据表';
```

---

## 2. 征信库 credit_data_db（Python 同步 / Java 只读）

**数据源**：`spring.datasource.credit` → 库名 `credit_data_db`  
**Mapper 包**：`org.example.risklendpro.mapper.credit`  
**实体包**：`org.example.risklendpro.entity.credit`

与主库 `RiskLendPro` 分离：离线 Python `load_to_mysql.py` 写入；在线 Java 读取规则与外部特征；黑名单另支持扣子工作流调用 `POST /api/v1/sync/blacklist` 增量写入。

### 2.1 黑名单表 (`blacklist`)

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `name` | VARCHAR(100) | 被执行人姓名（匹配维度 1，支持 `*`） |
| `area_code` | VARCHAR(20) | 地区编码（维度 2） |
| `birth_year` | INT | 出生年份（维度 3） |
| `case_no` | VARCHAR(50) | 案号 |
| `court_name` | VARCHAR(100) | 执行法院 |
| `duty_status` | VARCHAR(50) | 履行情况 |
| `behavior_details` | VARCHAR(500) | 失信行为描述 |
| `risk_level` | VARCHAR(10) | HIGH / MEDIUM / LOW |
| `created_at` | DATETIME | 创建时间 |
| `expire_at` | DATETIME | NULL=永久 |

实体：[`Blacklist.java`](../src/main/java/org/example/risklendpro/entity/credit/Blacklist.java)  
增量 API：[`BlacklistSyncController`](../src/main/java/org/example/risklendpro/controller/BlacklistSyncController.java)

### 2.2 用户外部特征表 (`user_external_features`)

A 卡 WOE+LR 第三方征信模拟；Java 优先按 `id_card` 关联（纯数字时可回退 `sk_id_curr`）。

| 字段 | 类型 | Java 属性 | 用途 |
|------|------|-----------|------|
| `id` | BIGINT | `id` | 主键 |
| `sk_id_curr` | BIGINT | `skIdCurr` | HC 申请 ID |
| `id_card` | VARCHAR(20) | `idCard` | 与主库 `user.id_card` 一致 |
| `days_birth` | INT | `daysBirth` | 验真：年龄 |
| `days_employed` | INT | `daysEmployed` | 验真：工龄 |
| `amt_income_total` | DECIMAL(15,2) | `amtIncomeTotal` | 验真：后台收入 |
| `credit_bureau_week` | INT | `creditBureauWeek` | 第三方：周查询 |
| `credit_bureau_mon` | INT | `creditBureauMon` | 第三方：月查询 |
| `days_last_phone_change` | INT | `daysLastPhoneChange` | WOE 入模（`phone_change_days`） |
| `active_loans_count` | INT | `activeLoansCount` | 第三方 / 验真 |
| `ext_source_2` | DECIMAL(10,6) | `extSource2` | WOE 入模 |
| `ext_source_3` | DECIMAL(10,6) | `extSource3` | WOE 入模 |
| `flag_own_car` | TINYINT(1) | `flagOwnCar` | 验真 |
| `gender_male` | TINYINT(1) | `genderMale` | WOE 候选 / 回测 |
| `married` | TINYINT(1) | `married` | WOE 候选 |
| `own_realty` | TINYINT(1) | `ownRealty` | WOE 候选 / 验真 |
| `employment_stable` | TINYINT(1) | `employmentStable` | WOE 候选 |
| `credit_income_ratio` | DECIMAL(12,4) | `creditIncomeRatio` | WOE 候选 |
| `cc_utilization` | DECIMAL(12,4) | `ccUtilization` | WOE 候选 |
| `loan_overdue_max_6m` | INT | `loanOverdueMax6m` | WOE 候选 |
| `occupation_type` | VARCHAR(50) | `occupationType` | 展示 / 验真 |
| `education_type` | VARCHAR(50) | `educationType` | 展示 / 验真 |
| `target` | TINYINT(1) | `target` | 回测标签 |
| `prev_refused_count` | INT | `prevRefusedCount` | WOE 入模 + 规则 |
| `data_source` | VARCHAR(50) | `dataSource` | 如 `Home Credit` |
| `updated_at` | DATETIME | `updatedAt` | 更新时间 |

实体：[`UserExternalFeatures.java`](../src/main/java/org/example/risklendpro/entity/credit/UserExternalFeatures.java)（无 MyBatis-Plus 注解，credit 数据源 XML 映射）

### 2.3 评分规则表 (`scoring_rules`)

A 卡 **v7.0-hc-woe** WOE+LR+PDO 规则；`is_active=1` 为当前生效版本。

| 字段 | 类型 | Java 属性 | 说明 |
|------|------|-----------|------|
| `id` | BIGINT | `id` | 主键 |
| `version` | VARCHAR(20) | `version` | 如 `v7.0-hc-woe` |
| `rule_content` | JSON | `ruleContent` | 完整规则备份（含 `features`/`coefficients`） |
| `feature_weights` | JSON | `featureWeights` | 入库时存 `coefficients`（兼容列名） |
| `scorecard` | JSON | `scorecard` | PDO 参数 |
| `application_rule_bonus` | JSON | `applicationRuleBonus` | 申请表策略加成 |
| `feature_scores` | JSON | `featureScores` | 旧版 LC 逐项规则 |
| `feature_derivation` | JSON | `featureDerivation` | 特征推导说明 |
| `intercept` | DECIMAL(16,8) | `intercept` | LR 截距 |
| `threshold_auto_approve` | DECIMAL(10,2) | `thresholdAutoApprove` | 自动通过（当前 788） |
| `threshold_manual_review` | DECIMAL(10,2) | `thresholdManualReview` | 人工审核（当前 642） |
| `is_active` | TINYINT(1) | `isActive` | 是否激活 |
| `trained_at` | DATETIME | `trainedAt` | 训练时间 |
| `training_data_count` | INT | `trainingDataCount` | 训练样本量 |
| `accuracy` | DECIMAL(10,6) | `accuracy` | 离线准确率 |
| `created_at` | DATETIME | `createdAt` | 记录创建时间 |

规则解读：[`output/scoring_rules.md`](../risk-assessment/output/scoring_rules.md)

实体：[`ScoringRules.java`](../src/main/java/org/example/risklendpro/entity/credit/ScoringRules.java)

### 2.4 B 卡行为特征表 (`user_behavior_features`)

| 字段 | 类型 | Java 属性 | 说明 |
|------|------|-----------|------|
| `id` | BIGINT | `id` | 主键 |
| `sk_id_curr` | BIGINT | `skIdCurr` | HC 申请 ID |
| `id_card` | VARCHAR(20) | `idCard` | 关联主库用户 |
| `feature_json` | JSON | `featureJson` | 7 维 `inst_*`/`pos_*` 行为特征 |
| `data_source` | VARCHAR | `dataSource` | 默认 `Home Credit B-card` |
| `updated_at` | DATETIME | `updatedAt` | 更新时间 |

实体：[`UserBehaviorFeatures.java`](../src/main/java/org/example/risklendpro/entity/credit/UserBehaviorFeatures.java)

### 2.5 B 卡规则表 (`behavior_scoring_rules`)

| 字段 | 类型 | Java 属性 | 说明 |
|------|------|-----------|------|
| `id` | BIGINT | `id` | 主键 |
| `version` | VARCHAR(32) | `version` | 如 `v1.0-b-woe` |
| `rule_content` | JSON | `ruleContent` | 完整规则备份 |
| `feature_weights` | JSON | `featureWeights` | WOE/LR 系数 |
| `scorecard` | JSON | `scorecard` | PDO 参数 |
| `intercept` | DECIMAL | `intercept` | 截距 |
| `threshold_watch` | DECIMAL | `thresholdWatch` | 观察阈值（684.6） |
| `threshold_reduce_limit` | DECIMAL | `thresholdReduceLimit` | 降额阈值（547.4） |
| `is_active` | TINYINT | `isActive` | 是否激活 |
| `trained_at` | DATETIME | `trainedAt` | 训练时间 |
| `training_data_count` | INT | `trainingDataCount` | 训练样本量 |
| `accuracy` | DECIMAL | `accuracy` | 离线准确率 |
| `created_at` | DATETIME | `createdAt` | 创建时间 |

规则解读：[`output/b_scoring_rules.md`](../risk-assessment/output/b_scoring_rules.md)

实体：[`BehaviorScoringRules.java`](../src/main/java/org/example/risklendpro/entity/credit/BehaviorScoringRules.java)

---

## 3. Java 实体类设计（主库 `RiskLendPro`）

包路径：**`org.example.risklendpro.entity`**。主库实体使用 MyBatis-Plus `@TableName` / `@TableField`；征信库实体见 [§4](#4-java-实体类设计征信库-credit_data_db)。

### 3.1 用户实体 (`User`)

```java
package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String realName;
    private String phoneNumber;
    private String email;
    private String idCard;
    private String password;
    private String role;
    private String assessmentStatus;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
}
```

### 3.2 管理员实体 (`Admin`)

```java
package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("admin")
public class Admin {
    private Long id;
    private String username;
    private String password;
    private String phoneNumber;
    private String email;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
}
```

### 3.3 风控评估申请实体 (`RiskAssessment`)

```java
package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("risk_assessment")
public class RiskAssessment {
    @TableId(type = IdType.INPUT)
    @TableField("apply_id")
    private String applyId;
    @TableField("user_id")
    private Long userId;
    private String idCard;
    private String name;
    private String phone;
    private String email;
    private Integer gender;
    private Date birthday;
    private String education;
    private String marriage;
    @TableField("job_type")
    private String jobType;
    @TableField("monthly_income")
    private String monthlyIncome;
    @TableField("has_house")
    private Boolean hasHouse;
    @TableField("has_car")
    private Boolean hasCar;
    @TableField("contact_phone")
    private String contactPhone;
    private String status;
    @TableField("sys_decision")
    private String sysDecision;
    @TableField("total_score")
    private Integer totalScore;
    @TableField("credit_limit")
    private BigDecimal creditLimit;
    @TableField("expire_date")
    private Date expireDate;
    @TableField("submit_time")
    private Date submitTime;
    @TableField("approval_time")
    private Date approvalTime;
    @TableField("is_final")
    private Boolean isFinal;
    @TableField("operator_id")
    private Long operatorId;
    @TableField("audit_remark")
    private String auditRemark;
    @TableField("supplement_status")
    private String supplementStatus;
    @TableField("supplement_requirements")
    private String supplementRequirements;
}
```

### 3.4 借款记录实体 (`Loan`)

```java
package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("loan")
public class Loan {
    @TableId(type = IdType.AUTO)
    @TableField("loan_id")
    private Long loanId;
    @TableField("user_id")
    private Long userId;
    private BigDecimal amount;
    @TableField("term_months")
    private Integer termMonths;
    @TableField("interest_rate")
    private BigDecimal interestRate;
    @TableField("repayment_method")
    private String repaymentMethod;
    private String status;
    @TableField("apply_time")
    private Date applyTime;
    @TableField("approve_time")
    private Date approveTime;
    @TableField("disbursement_time")
    private Date disbursementTime;
    @TableField("auto_approved")
    private Boolean autoApproved;
    @TableField("reject_reason")
    private String rejectReason;
    @TableField("additional_limit")
    private BigDecimal additionalLimit;
    @TableField("operator_id")
    private Long operatorId;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
}
```

### 3.5 还款计划实体 (`RepaymentPlan`)

```java
package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("repayment_plan")
public class RepaymentPlan {
    @TableField("plan_id")
    private Long planId;
    @TableField("loan_id")
    private Long loanId;
    @TableField("user_id")
    private Long userId;
    @TableField("total_amount")
    private BigDecimal totalAmount;
    @TableField("paid_amount")
    private BigDecimal paidAmount;
    @TableField("remaining_amount")
    private BigDecimal remainingAmount;
    @TableField("total_periods")
    private Integer totalPeriods;
    @TableField("current_period")
    private Integer currentPeriod;
    private String status;
    @TableField("overdue_days")
    private Integer overdueDays;
    @TableField("overdue_level")
    private String overdueLevel;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
}
```

### 3.6 还款记录实体 (`RepaymentRecord`)

```java
package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("repayment_record")
public class RepaymentRecord {
    @TableField("record_id")
    private Long recordId;
    @TableField("plan_id")
    private Long planId;
    @TableField("loan_id")
    private Long loanId;
    private Integer period;
    private BigDecimal principal;
    private BigDecimal interest;
    private BigDecimal amount;
    @TableField("actual_amount")
    private BigDecimal actualAmount;
    @TableField("due_date")
    private Date dueDate;
    @TableField("repayment_date")
    private Date repaymentDate;
    private String status;
    @TableField("create_time")
    private Date createTime;
}
```

### 3.7 用户额度实体 (`UserCreditLimit`)

```java
package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("user_credit_limit")
public class UserCreditLimit {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("total_limit")
    private BigDecimal totalLimit;
    @TableField("used_limit")
    private BigDecimal usedLimit;
    @TableField("remaining_limit")
    private BigDecimal remainingLimit;
    @TableField("overdue_amount")
    private BigDecimal overdueAmount;
    @TableField("has_overdue")
    private Boolean hasOverdue;
    @TableField("b_card_enabled")
    private Boolean bCardEnabled;
    @TableField("b_score")
    private BigDecimal bScore;
    @TableField("b_score_updated_at")
    private Date bScoreUpdatedAt;
    @TableField("last_update_time")
    private Date lastUpdateTime;
}
```

### 3.8 额度调整记录实体 (`LimitAdjustLog`)

```java
package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("limit_adjust_log")
public class LimitAdjustLog {
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("old_limit")
    private BigDecimal oldLimit;
    @TableField("new_limit")
    private BigDecimal newLimit;
    private String reason;
    @TableField("operator_id")
    private Long operatorId;
    @TableField("adjust_time")
    private Date adjustTime;
}
```

### 3.9 风控补充材料实体 (`RiskSupplementMaterial`)

```java
@Data
@TableName("risk_supplement_material")
public class RiskSupplementMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("apply_id")
    private String applyId;
    @TableField("user_id")
    private Long userId;
    @TableField("material_type")
    private String materialType;
    @TableField("original_name")
    private String originalName;
    @TableField("stored_path")
    private String storedPath;
    @TableField("file_size")
    private Long fileSize;
    @TableField("mime_type")
    private String mimeType;
    private String remark;
    @TableField("upload_time")
    private Date uploadTime;
    @TableField("expire_at")
    private Date expireAt;
}
```

### 3.10 B 卡评分历史实体 (`UserBCardLog`)

```java
@Data
@TableName("user_b_card_log")
public class UserBCardLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("base_score")
    private BigDecimal baseScore;
    @TableField("delta_score")
    private BigDecimal deltaScore;
    @TableField("final_score")
    private BigDecimal finalScore;
    @TableField("live_features")
    private String liveFeatures;
    @TableField("created_at")
    private Date createdAt;
}
```

### 3.11 Vintage数据实体 (`VintageData`)

```java
package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("vintage_data")
public class VintageData {
    private Long id;
    private String month;
    @TableField("disbursed_amount")
    private BigDecimal disbursedAmount;
    @TableField("m1_rate")
    private BigDecimal m1Rate;
    @TableField("m2_rate")
    private BigDecimal m2Rate;
    @TableField("m3_rate")
    private BigDecimal m3Rate;
    @TableField("create_time")
    private Date createTime;
}
```

---

## 4. Java 实体类设计（征信库 `credit_data_db`）

包路径：**`org.example.risklendpro.entity.credit`**。均为 POJO + Lombok `@Data`，由 `mapper.credit` 下 XML / 注解映射，**不使用** MyBatis-Plus `@TableName`。

| 实体类 | 表 | 主要消费方 |
|--------|-----|-----------|
| `Blacklist` | `blacklist` | `CreditScoreEngineImpl`、黑名单同步 API |
| `UserExternalFeatures` | `user_external_features` | `CreditScoreEngineImpl`（A 卡 WOE 原始值） |
| `ScoringRules` | `scoring_rules` | `CreditScoreEngineImpl` |
| `UserBehaviorFeatures` | `user_behavior_features` | `BehaviorScoreEngineImpl` |
| `BehaviorScoringRules` | `behavior_scoring_rules` | `BehaviorScoreEngineImpl` |

### 4.1 `UserExternalFeatures` 字段一览

与 [§2.2](#22-用户外部特征表-user_external_features) 表字段一一对应：`skIdCurr`、`idCard`、`daysBirth`、`daysEmployed`、`amtIncomeTotal`、`creditBureauWeek/Mon`、`daysLastPhoneChange`、`activeLoansCount`、`extSource2/3`、`flagOwnCar`、`genderMale`、`married`、`ownRealty`、`employmentStable`、`creditIncomeRatio`、`ccUtilization`、`loanOverdueMax6m`、`occupationType`、`educationType`、`target`、`prevRefusedCount`、`dataSource`、`updatedAt`。

### 4.2 `ScoringRules` 字段一览

`id`、`version`、`ruleContent`、`featureWeights`、`scorecard`、`applicationRuleBonus`、`featureScores`、`featureDerivation`、`intercept`、`thresholdAutoApprove`、`thresholdManualReview`、`isActive`、`trainedAt`、`trainingDataCount`、`accuracy`、`createdAt`。

### 4.3 `BehaviorScoringRules` 字段一览

`id`、`version`、`ruleContent`、`featureWeights`、`scorecard`、`intercept`、`thresholdWatch`、`thresholdReduceLimit`、`isActive`、`trainedAt`、`trainingDataCount`、`accuracy`、`createdAt`。

### 4.4 `UserBehaviorFeatures` / `Blacklist`

- `UserBehaviorFeatures`：`id`、`skIdCurr`、`idCard`、`featureJson`、`dataSource`、`updatedAt`
- `Blacklist`：`id`、`name`、`areaCode`、`birthYear`、`caseNo`、`courtName`、`dutyStatus`、`behaviorDetails`、`riskLevel`、`createdAt`、`expireAt`

源码路径：[`entity/credit/`](../src/main/java/org/example/risklendpro/entity/credit/)

---

## 5. 实体关系图

```
User "1" --- "0..*" RiskAssessment
User "1" --- "0..*" Loan
User "1" --- "1" UserCreditLimit
User "1" --- "0..*" RepaymentPlan
User "1" --- "0..*" UserBCardLog
Loan "1" --- "1" RepaymentPlan
RepaymentPlan "1" --- "0..*" RepaymentRecord
RiskAssessment "1" --- "0..*" RiskSupplementMaterial
Admin "1" --- "0..*" RiskAssessment
Admin "1" --- "0..*" Loan
Admin "1" --- "0..*" LimitAdjustLog
User "1" --- "0..1" UserExternalFeatures : id_card
User "1" --- "0..1" UserBehaviorFeatures : id_card
```

## 6. 业务流程说明

### 6.1 借款审批流程

1. **用户发起借款请求**
   - 系统检查用户是否有未处理逾期
   - 系统检查借款金额与剩余额度的关系
   - **额度内借款**：自动审批通过，状态变为 `DISBURRSED`，扣减额度，直接放款
   - **额度外借款**：状态变为 `PENDING_APPROVAL`，等待管理员审批
2. **管理员审批**
   - 管理员查看待审批贷款申请列表
   - **审批通过**：状态变为 `APPROVED`，计算实际发放金额，邮件通知用户
   - **审批拒绝**：状态变为 `REJECTED`，邮件通知用户
3. **放款处理**
   - 自动审批或管理员审批通过后，系统生成还款计划
   - 状态变为 `DISBURRSED`，记录放款时间

### 6.2 还款流程

1. **生成还款计划**：借款成功后，系统自动生成还款计划表
2. **用户还款**：用户执行单期还款操作
3. **状态更新**：还款后更新还款记录和还款计划状态
4. **逾期处理**：每日凌晨跑批更新逾期状态

## 7. 数据库索引优化

- **用户相关**：`user(phone_number, id_card, user_id)`
- **借款相关**：`loan(user_id, status)`
- **还款相关**：`repayment_plan(loan_id, user_id, status)`
- **风控相关**：`risk_assessment(id_card, user_id, status)`
- **额度相关**：`user_credit_limit(user_id)`

## 8. 注意事项

1. **数据一致性**：借款金额与额度扣减需要事务处理
2. **性能优化**：对频繁查询的字段建立索引
3. **安全考虑**：密码字段需要加密存储
4. **并发控制**：处理多用户同时操作的情况
5. **数据备份**：定期备份重要业务数据

## 9. 结论

本设计覆盖主库业务（用户、风控、借款、还款、额度、补充材料、B 卡历史）与征信库（黑名单、外部特征、A/B 卡规则）。主库实体位于 `org.example.risklendpro.entity`，征信库实体位于 `org.example.risklendpro.entity.credit`。

A 卡当前默认 **WOE v7**（`hc_woe_lr`），B 卡为 **v1.0-b-woe**；表结构与实体字段以 [`sql/table.sql`](../sql/table.sql) 及 [`load_to_mysql.py`](../risk-assessment/load_to_mysql.py) 为准。规则 JSON 人类可读副本见 [`output/scoring_rules.md`](../risk-assessment/output/scoring_rules.md)、[`output/b_scoring_rules.md`](../risk-assessment/output/b_scoring_rules.md)。
