-- ============================================================================
-- 阶段 A1：B 卡规则治理迁移（幂等，可重复执行）
--
-- 目标：
--   1) 任意时刻只有一个 is_active = 1 的版本，并加数据库级唯一约束兜底；
--   2) 规则选择顺序不再依赖 version 字符串排序（避免 v1.10 < v1.9 的字典序错误）；
--   3) 补充规则哈希 / 激活时间 / 模型元数据 / 阈值来源列，便于审计与回滚。
--
-- 不做的事：
--   * 不改变任何已有规则内容、WOE、系数、截距、阈值或 PDO 参数；
--   * 不删除任何历史规则版本（只调整 is_active 标记）。
--
-- 执行前请先备份 behavior_scoring_rules。
-- 用法：mysql -u <user> -p <db> < sql/migration_b_card_rule_governance.sql
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 步骤 1：把历史遗留的多 active 收敛为唯一 active（保留 id 最大即最新写入的一条）
-- ---------------------------------------------------------------------------
UPDATE behavior_scoring_rules
SET is_active = 0
WHERE is_active = 1
  AND id <> (SELECT keep_id FROM (SELECT MAX(id) AS keep_id
                                 FROM behavior_scoring_rules
                                 WHERE is_active = 1) AS t);

-- ---------------------------------------------------------------------------
-- 步骤 2：rule_content 由 JSON 改为 LONGTEXT
-- 原因：JSON 列在 MySQL 内部会被规范化（键排序、去空白），读回文本与写入文本不一致，
--       会导致「发布端计算的内容哈希」与「消费端读回后计算的内容哈希」不相等。
--       改为 LONGTEXT 后文本原样保存，两端哈希一致，内容哈希校验才有意义。
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_a1_rule_content_to_text;
DELIMITER $$
CREATE PROCEDURE sp_a1_rule_content_to_text()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'behavior_scoring_rules'
                 AND COLUMN_NAME = 'rule_content'
                 AND DATA_TYPE = 'json') THEN
        ALTER TABLE behavior_scoring_rules
            MODIFY COLUMN rule_content LONGTEXT NOT NULL
            COMMENT '规则内容原文（内容哈希的唯一依据，禁止依赖 JSON 列的规范化文本）';
    END IF;
END$$
DELIMITER ;
CALL sp_a1_rule_content_to_text();
DROP PROCEDURE IF EXISTS sp_a1_rule_content_to_text;

-- ---------------------------------------------------------------------------
-- 步骤 3：补充治理列
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_a1_add_column_if_absent;
DELIMITER $$
CREATE PROCEDURE sp_a1_add_column_if_absent(IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'behavior_scoring_rules'
                     AND COLUMN_NAME = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE behavior_scoring_rules ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL sp_a1_add_column_if_absent('rule_hash',
    'rule_hash CHAR(64) NULL COMMENT ''规则内容 SHA-256（对 rule_content 原文计算，发布端与消费端一致）''');
CALL sp_a1_add_column_if_absent('activated_at',
    'activated_at DATETIME NULL COMMENT ''该版本被激活的时间；用于稳定、可解释的版本选择''');
CALL sp_a1_add_column_if_absent('model_type',
    'model_type VARCHAR(32) NULL COMMENT ''模型类型，期望 b_card_woe''');
CALL sp_a1_add_column_if_absent('card_type',
    'card_type VARCHAR(8) NULL COMMENT ''卡类型，期望 B''');
CALL sp_a1_add_column_if_absent('threshold_source',
    'threshold_source VARCHAR(32) NULL COMMENT ''阈值来源：DB_COLUMN / RULE_CONTENT / DEFAULT''');

DROP PROCEDURE IF EXISTS sp_a1_add_column_if_absent;

-- 历史行没有可信的哈希来源，保持 NULL，由下一次发布覆盖，
-- 避免写入与消费端口径不一致的哈希值。

-- ---------------------------------------------------------------------------
-- 步骤 4：数据库级保证「任意时刻只有一个 active 版本」
-- 生成列 active_slot：active 行取 1，非 active 行取 NULL；
-- 在它上面建唯一索引，即可在数据库层阻止第二条 active 行写入。
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_a1_add_single_active_index;
DELIMITER $$
CREATE PROCEDURE sp_a1_add_single_active_index()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'behavior_scoring_rules'
                     AND COLUMN_NAME = 'active_slot') THEN
        ALTER TABLE behavior_scoring_rules
            ADD COLUMN active_slot TINYINT
            GENERATED ALWAYS AS (IF(is_active = 1, 1, NULL)) VIRTUAL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'behavior_scoring_rules'
                     AND INDEX_NAME = 'uk_bsr_single_active') THEN
        CREATE UNIQUE INDEX uk_bsr_single_active ON behavior_scoring_rules (active_slot);
    END IF;
END$$
DELIMITER ;
CALL sp_a1_add_single_active_index();
DROP PROCEDURE IF EXISTS sp_a1_add_single_active_index;

-- ---------------------------------------------------------------------------
-- 步骤 5：版本选择辅助索引 + 校验
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_a1_add_activation_index;
DELIMITER $$
CREATE PROCEDURE sp_a1_add_activation_index()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'behavior_scoring_rules'
                     AND INDEX_NAME = 'idx_bsr_active_activation') THEN
        CREATE INDEX idx_bsr_active_activation
            ON behavior_scoring_rules (is_active, activated_at, id);
    END IF;
END$$
DELIMITER ;
CALL sp_a1_add_activation_index();
DROP PROCEDURE IF EXISTS sp_a1_add_activation_index;

-- 迁移后自检：active_count 必须为 0 或 1
SELECT COUNT(*) AS active_count FROM behavior_scoring_rules WHERE is_active = 1;
SELECT id, version, is_active, activated_at, rule_hash, model_type, card_type, threshold_source
FROM behavior_scoring_rules
ORDER BY id DESC;