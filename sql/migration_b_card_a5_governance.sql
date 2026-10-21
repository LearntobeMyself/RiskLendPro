-- ============================================================================
-- 阶段 A5：生产治理、监控与回滚
--
-- 1) b_card_model_registry    模型注册 / 审批 / 激活 / 回滚（历史版本永不删除）
-- 2) b_card_decision_audit    线上决策追溯（模型/特征/策略/规则哈希/数据快照）
-- 3) b_card_monitor_alert     监控告警与人工确认闭环
--
-- 幂等，可重复执行。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `b_card_model_registry` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `model_version` VARCHAR(64) NOT NULL,
    `model_hash` CHAR(64) NOT NULL COMMENT '模型产物内容 SHA-256',
    `feature_version` VARCHAR(32) NOT NULL,
    `label_version` VARCHAR(32) NOT NULL,
    `policy_version` VARCHAR(32) NOT NULL,
    `artifact_json` LONGTEXT NOT NULL COMMENT '模型产物（WOE/系数/校准/阈值）',
    `metrics_json` LONGTEXT NULL COMMENT 'OOT/校准/PSI 指标',
    `status` VARCHAR(16) NOT NULL DEFAULT 'CANDIDATE'
        COMMENT 'CANDIDATE / APPROVED / ACTIVE / RETIRED',
    `approved_by` VARCHAR(64) NULL,
    `approved_at` DATETIME NULL,
    `activated_at` DATETIME NULL,
    `retired_at` DATETIME NULL,
    `last_action` VARCHAR(16) NULL,
    `last_action_at` DATETIME NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `active_slot` TINYINT GENERATED ALWAYS AS (IF(status = 'ACTIVE', 1, NULL)) VIRTUAL,
    UNIQUE KEY `uk_model_version` (`model_version`),
    UNIQUE KEY `uk_model_single_active` (`active_slot`),
    KEY `idx_model_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B卡模型注册表（历史版本完整保留）';

CREATE TABLE IF NOT EXISTS `b_card_decision_audit` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `decision_type` VARCHAR(32) NOT NULL COMMENT 'QUOTA_MULTIPLIER / SHADOW',
    `model_version` VARCHAR(64) NULL,
    `model_hash` CHAR(64) NULL,
    `feature_version` VARCHAR(32) NULL,
    `policy_version` VARCHAR(32) NULL,
    `rule_id` BIGINT NULL,
    `rule_hash` CHAR(64) NULL,
    `snapshot_ref` VARCHAR(64) NULL COMMENT '数据快照引用，如 b_card_log:123',
    `data_status` VARCHAR(32) NULL,
    `score` DECIMAL(10,2) NULL,
    `probability` DECIMAL(10,6) NULL,
    `multiplier` DECIMAL(6,3) NULL,
    `decision_allowed` TINYINT(1) NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_audit_user` (`user_id`, `created_at`),
    KEY `idx_audit_versions` (`model_version`, `feature_version`, `policy_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B卡线上决策审计（可追溯到模型/特征/策略/快照）';

CREATE TABLE IF NOT EXISTS `b_card_monitor_alert` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `alert_date` DATE NOT NULL,
    `metric` VARCHAR(32) NOT NULL,
    `level` VARCHAR(16) NOT NULL COMMENT 'WARN / CRITICAL',
    `metric_value` DECIMAL(18,6) NOT NULL,
    `threshold_value` DECIMAL(18,6) NULL,
    `detail` VARCHAR(500) NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / ACKED / CLOSED',
    `acked_by` VARCHAR(64) NULL,
    `acked_at` DATETIME NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_alert_date_metric_level` (`alert_date`, `metric`, `level`),
    KEY `idx_alert_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B卡监控告警（人工确认闭环）';

SELECT COUNT(*) AS governance_tables FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('b_card_model_registry','b_card_decision_audit','b_card_monitor_alert');