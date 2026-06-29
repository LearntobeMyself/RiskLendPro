-- 用户启用/禁用（猫咪贷管用户管理）
USE RiskLendPro;

ALTER TABLE `user`
    ADD COLUMN `account_status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE/DISABLED' AFTER `assessment_status`;
