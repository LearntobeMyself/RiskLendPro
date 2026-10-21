-- ============================================================================
-- 阶段 A1：B 卡规则「单事务原子激活 / 回滚」模板
--
-- 用法：
--   1) 先改下面的 @target_version；
--   2) 整体执行本文件；
--   3) 服务端无需重启，规则会在 TTL（risk.b-card.rule-cache-ttl-seconds，默认 60 秒）
--      到期后自动切换到新版本，也可立即调用人工刷新入口：
--      POST /internal/behavior-scores/rules/refresh
--
-- 保证：
--   * 激活过程在单个事务内完成（先全部下线、再目标上线），不存在「两个 active」的中间态；
--   * 由唯一索引 uk_bsr_single_active 在数据库层兜底；
--   * 历史版本只改 is_active 标记，不删除任何规则行。
-- ============================================================================

SET @target_version = 'v1.1-b-woe';

-- 前置检查：目标版本必须存在且唯一；若 rows = 0 请先发布该版本
SELECT COUNT(*) AS target_rows FROM behavior_scoring_rules WHERE version = @target_version;

START TRANSACTION;

UPDATE behavior_scoring_rules SET is_active = 0 WHERE is_active = 1;

UPDATE behavior_scoring_rules
SET is_active = 1,
    activated_at = NOW()
WHERE version = @target_version;

COMMIT;

-- 激活后校验：active_count 必须为 1
SELECT COUNT(*) AS active_count FROM behavior_scoring_rules WHERE is_active = 1;
SELECT id, version, is_active, activated_at, rule_hash, threshold_source
FROM behavior_scoring_rules
WHERE is_active = 1;

-- ----------------------------------------------------------------------------
-- 回滚模板：把 @rollback_version 换成上一稳定版本后整体执行
-- ----------------------------------------------------------------------------
-- SET @rollback_version = 'v1.0-b-woe';
-- START TRANSACTION;
-- UPDATE behavior_scoring_rules SET is_active = 0 WHERE is_active = 1;
-- UPDATE behavior_scoring_rules SET is_active = 1, activated_at = NOW()
--  WHERE version = @rollback_version;
-- COMMIT;