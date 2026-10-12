-- ========================================================
-- risk_supplement_material 表：图片核验功能字段迁移
-- 执行环境：RiskLendPro 主业务库
-- 说明：为每张补充材料记录增加 OCR / AI生成检测 / 篡改检测 / 最终状态 字段
-- ========================================================

ALTER TABLE risk_supplement_material
    ADD COLUMN verification_ocr           TEXT         NULL COMMENT 'OCR 识别到的完整文字内容',
    ADD COLUMN verification_ai_score      DOUBLE       NULL COMMENT 'AI 生成检测置信度（0-1）',
    ADD COLUMN verification_tamper_score  DOUBLE       NULL COMMENT '篡改痕迹检测置信度（0-1）',
    ADD COLUMN verification_status        VARCHAR(16)  NULL COMMENT '综合校验结果：PASS / WARN / FAIL / SKIPPED',
    ADD COLUMN verification_time          DATETIME     NULL COMMENT '校验执行时间';
