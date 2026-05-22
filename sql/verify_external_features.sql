-- 第三方特征入库自检（与 application.yaml 中 credit_data_db 同一实例）
-- 将下方 id_card 替换为测试用例 sql/测试用例.md 中的值

USE credit_data_db;

-- 示例：赵六（高分）
SELECT id_card, sk_id_curr, ext_source_2, ext_source_3, active_loans_count,
       credit_bureau_mon, amt_income_total, data_source
FROM user_external_features
WHERE id_card = '110112199604210012';

-- 示例：王五（低 ext_source）
SELECT id_card, ext_source_2, ext_source_3
FROM user_external_features
WHERE id_card = '110112198511190019';

-- 示例：张小梁（仅姓名黑名单联调）
SELECT id_card, ext_source_2, ext_source_3
FROM user_external_features
WHERE id_card = '110112198207080000';

-- 统计是否有 id_card 列数据
SELECT COUNT(*) AS total_rows,
       SUM(id_card IS NOT NULL AND id_card <> '') AS with_id_card
FROM user_external_features;

-- 激活的评分规则版本
SELECT version, is_active, intercept, threshold_auto_approve, threshold_manual_review
FROM scoring_rules
WHERE is_active = 1;
