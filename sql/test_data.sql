-- 测试数据插入脚本
-- 注意：请在创建表结构后执行此脚本

-- 使用数据库
use RiskLendPro;

-- 统一使用密码: $2a$10$Tn04hLggLH0TxCE3pND/sOtqW7ySxjwtk/iiubE/80orx3tn5Cvry=123456

INSERT INTO `user`
(`real_name`, `phone_number`, `email`, `id_card`, `password`, `role`, `assessment_status`)
VALUES
('黑名单用户', '13800138000', 'blacklist@test.com', '510106197503070400',
 '$2a$10$Tn04hLggLH0TxCE3pND/sOtqW7ySxjwtk/iiubE/80orx3tn5Cvry', 'USER', 'NOT_ASSESSED'),
('高信用用户', '13800138001', 'high@test.com', '510106198502280000',
 '$2a$10$Tn04hLggLH0TxCE3pND/sOtqW7ySxjwtk/iiubE/80orx3tn5Cvry', 'USER', 'NOT_ASSESSED'),
('中等信用用户', '13800138002', 'medium@test.com', '510106199903170001',
 '$2a$10$Tn04hLggLH0TxCE3pND/sOtqW7ySxjwtk/iiubE/80orx3tn5Cvry', 'USER', 'NOT_ASSESSED'),
('优质用户', '13800138003', 'excellent@test.com', '510106198812260002',
 '$2a$10$Tn04hLggLH0TxCE3pND/sOtqW7ySxjwtk/iiubE/80orx3tn5Cvry', 'USER', 'NOT_ASSESSED'),
('极好信用用户', '13800138004', 'top@test.com', '510106199505260003',
 '$2a$10$Tn04hLggLH0TxCE3pND/sOtqW7ySxjwtk/iiubE/80orx3tn5Cvry', 'USER', 'NOT_ASSESSED'),
('良好信用用户', '13800138005', 'good@test.com', '510106199209120004',
 '$2a$10$Tn04hLggLH0TxCE3pND/sOtqW7ySxjwtk/iiubE/80orx3tn5Cvry', 'USER', 'NOT_ASSESSED');