-- ============================================================================
-- 阶段 A2：as-of 特征快照与标签体系
--
-- 目标：
--   1) 快照表补齐标签成熟元数据（成熟日、标签版本、计算时间）；
--   2) 补齐数据质量状态与特征哈希/数据来源，支持历史复现；
--   3) 新增不可变额度历史快照表，历史 asOfDate 的额度特征不再读取当前值。
--
-- 不做的事：
--   * 不改写任何已存在的快照 feature_json；
--   * 不提前生成未成熟标签（label_* 仍由标签任务在成熟后写入）；
--   * 不把快照接入当前 V1 在线推理。
--
-- 幂等，可重复执行。用法：
--   mysql -u <user> -p RiskLendPro < sql/migration_b_card_v2.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS `b_card_feature_snapshot` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `as_of_date` DATE NOT NULL,
    `snapshot_time` DATETIME NOT NULL,
    `feature_version` VARCHAR(32) NOT NULL,
    `observation_start` DATE NOT NULL,
    `observation_end` DATE NOT NULL,
    `performance_start` DATE NULL,
    `performance_end` DATE NULL,
    `sample_status` VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        COMMENT 'OPEN / MATURED / LABELED / EXCLUDED',
    `label_30dpd_6m` TINYINT NULL,
    `label_90dpd_12m` TINYINT NULL,
    `feature_json` JSON NOT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `maturity_date_30dpd_6m` DATE NULL COMMENT 'label_30dpd_6m 表现期结束日',
    `maturity_date_90dpd_12m` DATE NULL COMMENT 'label_90dpd_12m 表现期结束日',
    `label_version` VARCHAR(32) NULL COMMENT '标签口径版本',
    `label_calculated_at` DATETIME NULL COMMENT '标签计算时间',
    `data_quality_status` VARCHAR(20) NULL COMMENT 'OK / DEGRADED / INSUFFICIENT',
    `feature_hash` CHAR(64) NULL COMMENT 'feature_json 内容 SHA-256',
    `data_source` VARCHAR(128) NULL COMMENT '特征数据来源说明',
    UNIQUE KEY `uk_user_asof_version` (`user_id`, `as_of_date`, `feature_version`),
    KEY `idx_asof_status` (`as_of_date`, `sample_status`),
    KEY `idx_user_time` (`user_id`, `snapshot_time`),
    KEY `idx_status_maturity` (`sample_status`, `maturity_date_30dpd_6m`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B卡V2真实特征快照';

-- ---------------------------------------------------------------------------
-- 已存在的旧表补齐 A2 新增列（幂等）
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_a2_add_snapshot_column;
DELIMITER $$
CREATE PROCEDURE sp_a2_add_snapshot_column(IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'b_card_feature_snapshot'
                     AND COLUMN_NAME = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE b_card_feature_snapshot ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL sp_a2_add_snapshot_column('maturity_date_30dpd_6m',
    'maturity_date_30dpd_6m DATE NULL COMMENT ''label_30dpd_6m 表现期结束日''');
CALL sp_a2_add_snapshot_column('maturity_date_90dpd_12m',
    'maturity_date_90dpd_12m DATE NULL COMMENT ''label_90dpd_12m 表现期结束日''');
CALL sp_a2_add_snapshot_column('label_version',
    'label_version VARCHAR(32) NULL COMMENT ''标签口径版本''');
CALL sp_a2_add_snapshot_column('label_calculated_at',
    'label_calculated_at DATETIME NULL COMMENT ''标签计算时间''');
CALL sp_a2_add_snapshot_column('data_quality_status',
    'data_quality_status VARCHAR(20) NULL COMMENT ''OK / DEGRADED / INSUFFICIENT''');
CALL sp_a2_add_snapshot_column('feature_hash',
    'feature_hash CHAR(64) NULL COMMENT ''feature_json 内容 SHA-256''');
CALL sp_a2_add_snapshot_column('data_source',
    'data_source VARCHAR(128) NULL COMMENT ''特征数据来源说明''');

DROP PROCEDURE IF EXISTS sp_a2_add_snapshot_column;

-- 旧表已存在时，CREATE TABLE IF NOT EXISTS 不会补索引，这里单独补齐
DROP PROCEDURE IF EXISTS sp_a2_add_maturity_index;
DELIMITER $$
CREATE PROCEDURE sp_a2_add_maturity_index()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'b_card_feature_snapshot'
                     AND INDEX_NAME = 'idx_status_maturity') THEN
        CREATE INDEX idx_status_maturity
            ON b_card_feature_snapshot (sample_status, maturity_date_30dpd_6m);
    END IF;
END$$
DELIMITER ;
CALL sp_a2_add_maturity_index();
DROP PROCEDURE IF EXISTS sp_a2_add_maturity_index;

-- 历史快照的成熟日按 as_of_date 回填（不改动标签，标签仍由任务生成）
UPDATE b_card_feature_snapshot
SET maturity_date_30dpd_6m = DATE_ADD(as_of_date, INTERVAL 6 MONTH)
WHERE maturity_date_30dpd_6m IS NULL;

UPDATE b_card_feature_snapshot
SET maturity_date_90dpd_12m = DATE_ADD(as_of_date, INTERVAL 12 MONTH)
WHERE maturity_date_90dpd_12m IS NULL;

-- ---------------------------------------------------------------------------
-- 额度历史快照（不可变）：历史 asOfDate 的额度特征只能用该表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_credit_limit_history` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `snapshot_date` DATE NOT NULL COMMENT '该行反映的额度状态日期（当日日终）',
    `total_limit` DECIMAL(15,2) NOT NULL DEFAULT 0,
    `used_limit` DECIMAL(15,2) NOT NULL DEFAULT 0,
    `remaining_limit` DECIMAL(15,2) NOT NULL DEFAULT 0,
    `overdue_amount` DECIMAL(15,2) NOT NULL DEFAULT 0,
    `has_overdue` TINYINT(1) NOT NULL DEFAULT 0,
    `b_card_enabled` TINYINT(1) NOT NULL DEFAULT 0,
    `b_score` DECIMAL(10,2) NULL,
    `data_source` VARCHAR(64) NOT NULL DEFAULT 'user_credit_limit_daily',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_snapshot_date` (`user_id`, `snapshot_date`),
    KEY `idx_user_date` (`user_id`, `snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度历史快照（不可变，供 as-of 复现）';

-- 自检
SELECT COUNT(*) AS snapshot_missing_maturity
FROM b_card_feature_snapshot WHERE maturity_date_30dpd_6m IS NULL;