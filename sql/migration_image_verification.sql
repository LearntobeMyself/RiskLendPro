-- ========================================================
-- risk_supplement_material 表：图片核验功能字段迁移
-- 执行环境：RiskLendPro 主业务库
-- 说明：为每张补充材料记录增加 OCR / AI生成检测 / 篡改检测 / 最终状态 字段
--
-- 幂等：可重复执行。原版本使用裸 ALTER TABLE ADD COLUMN，
--       在已补过列的环境或多人共享库上会因 Duplicate column 直接失败，
--       导致后续部署脚本中断，这里改为先查 information_schema 再按需 ALTER。
-- 用法：mysql -u <user> -p RiskLendPro < sql/migration_image_verification.sql
-- ========================================================

DROP PROCEDURE IF EXISTS sp_add_supplement_verification_column;
DELIMITER $$
CREATE PROCEDURE sp_add_supplement_verification_column(IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'risk_supplement_material'
                     AND COLUMN_NAME = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE risk_supplement_material ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL sp_add_supplement_verification_column('verification_ocr',
    'verification_ocr TEXT NULL COMMENT ''OCR 识别到的完整文字内容''');
CALL sp_add_supplement_verification_column('verification_ai_score',
    'verification_ai_score DOUBLE NULL COMMENT ''AI 生成检测置信度（0-1）''');
CALL sp_add_supplement_verification_column('verification_tamper_score',
    'verification_tamper_score DOUBLE NULL COMMENT ''篡改痕迹检测置信度（0-1）''');
CALL sp_add_supplement_verification_column('verification_status',
    'verification_status VARCHAR(16) NULL COMMENT ''综合校验结果：PASS / WARN / FAIL / SKIPPED''');
CALL sp_add_supplement_verification_column('verification_time',
    'verification_time DATETIME NULL COMMENT ''校验执行时间''');

DROP PROCEDURE IF EXISTS sp_add_supplement_verification_column;

-- 自检：应返回 5
SELECT COUNT(*) AS verification_column_count
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'risk_supplement_material'
  AND COLUMN_NAME IN ('verification_ocr', 'verification_ai_score',
                      'verification_tamper_score', 'verification_status',
                      'verification_time');
