-- 孙七 L2 黑名单测试种子：姓名 `孙*` + 地域 `110112`（二级），出生年 1986 ≠ 用户 1987（非三级）
-- 执行前请确认 credit_data_db 可写

USE credit_data_db;

DELETE FROM blacklist
WHERE name = '孙*七' AND area_code = '110112';

INSERT INTO blacklist (
    name, area_code, birth_year, case_no, court_name,
    duty_status, behavior_details, created_at, expire_at, risk_level
)
SELECT
    '孙*', '110112', 1986,
    '（2025）京0112执测L2号', '北京市通州区人民法院',
    '全部未履行', '有履行能力而拒不履行生效法律文书确定义务',
    NOW(), NULL, 'HIGH'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM blacklist
    WHERE name = '孙*' AND area_code = '110112' AND birth_year = 1986
);
