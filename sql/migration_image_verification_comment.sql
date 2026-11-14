-- ========================================================
-- 图片核验字段：仅修正列注释，不改类型、不动数据
-- 背景：verification_ai_score 原先注释为"AI 生成检测置信度"，
--       实际取值来自 EXIF 元数据规则（Software / Make / Model），
--       不具备判断图片是否由 AI 生成的能力，注释会误导后续维护者与验收。
-- 幂等：可重复执行。
-- 用法：mysql -u <user> -p RiskLendPro < sql/migration_image_verification_comment.sql
-- ========================================================

ALTER TABLE risk_supplement_material
    MODIFY COLUMN verification_ai_score DOUBLE NULL
    COMMENT 'EXIF 元数据风险分（0-1），非 AI 生成概率';

ALTER TABLE risk_supplement_material
    MODIFY COLUMN verification_tamper_score DOUBLE NULL
    COMMENT '编辑痕迹提示分（0-1，依据 EXIF Software 字段）';

-- 自检
SELECT COLUMN_NAME, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'risk_supplement_material'
  AND COLUMN_NAME IN ('verification_ai_score', 'verification_tamper_score');
