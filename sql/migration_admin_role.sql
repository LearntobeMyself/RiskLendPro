-- 给云库 admin 表补角色列（可与启动器 AdminRoleSchemaInitializer 二选一，重复执行时若列已存在会报 Duplicate column，忽略即可）
USE RiskLendPro;

-- 1) 补列（已存在则跳过本句）
ALTER TABLE `admin`
    ADD COLUMN `role` VARCHAR(32) NOT NULL DEFAULT 'SYS_ADMIN'
    COMMENT 'RISK_MANAGER/COLLECTOR/AUDITOR/CS_AGENT/SYS_ADMIN' AFTER `email`;

-- 2) 存量管理员先收成系统管理员（不能再审批）
UPDATE `admin`
SET `role` = 'SYS_ADMIN'
WHERE `role` IS NULL OR `role` = '' OR `role` IN ('ADMIN', 'SUPER_ADMIN');

-- 3) 演示账号：密码与现有 admin 相同（README 为 Admin123456）
INSERT INTO `admin` (username, password, phone_number, email, role, create_time, update_time)
SELECT 'risk_mgr', password, '13800001001', 'risk.mgr@risklendpro.test', 'RISK_MANAGER', NOW(), NOW()
FROM `admin` WHERE username = 'admin' AND NOT EXISTS (SELECT 1 FROM `admin` d WHERE d.username = 'risk_mgr') LIMIT 1;

INSERT INTO `admin` (username, password, phone_number, email, role, create_time, update_time)
SELECT 'collector', password, '13800001002', 'collector@risklendpro.test', 'COLLECTOR', NOW(), NOW()
FROM `admin` WHERE username = 'admin' AND NOT EXISTS (SELECT 1 FROM `admin` d WHERE d.username = 'collector') LIMIT 1;

INSERT INTO `admin` (username, password, phone_number, email, role, create_time, update_time)
SELECT 'auditor', password, '13800001003', 'auditor@risklendpro.test', 'AUDITOR', NOW(), NOW()
FROM `admin` WHERE username = 'admin' AND NOT EXISTS (SELECT 1 FROM `admin` d WHERE d.username = 'auditor') LIMIT 1;

INSERT INTO `admin` (username, password, phone_number, email, role, create_time, update_time)
SELECT 'cs_agent', password, '13800001004', 'cs.agent@risklendpro.test', 'CS_AGENT', NOW(), NOW()
FROM `admin` WHERE username = 'admin' AND NOT EXISTS (SELECT 1 FROM `admin` d WHERE d.username = 'cs_agent') LIMIT 1;
