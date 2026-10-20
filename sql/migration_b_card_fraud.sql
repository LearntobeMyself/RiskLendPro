-- 阶段 A5+：B 卡反欺诈 / 早期逾期预警表
CREATE TABLE IF NOT EXISTS `b_card_fraud_warning` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `warn_date` DATE NOT NULL,
    `rule_version` VARCHAR(32) NOT NULL,
    `score` INT NOT NULL,
    `level` VARCHAR(16) NOT NULL,
    `summary` VARCHAR(500) NULL,
    `signals_json` TEXT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    `acked_by` VARCHAR(64) NULL,
    `acked_at` DATETIME NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_fraud_user_date_rule` (`user_id`, `warn_date`, `rule_version`),
    KEY `idx_fraud_level` (`warn_date`, `level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B卡反欺诈/早期逾期预警';