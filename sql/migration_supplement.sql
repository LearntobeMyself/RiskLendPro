-- 人工复核补充材料：新表 + risk_assessment 扩展字段
-- 兼容 MySQL 5.7+（不使用 ADD COLUMN IF NOT EXISTS）
USE RiskLendPro;

CREATE TABLE IF NOT EXISTS `risk_supplement_material` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `apply_id` VARCHAR(30) NOT NULL COMMENT '评估申请ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `material_type` VARCHAR(50) NOT NULL COMMENT '材料类型',
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `stored_path` VARCHAR(500) NOT NULL COMMENT '服务器存储路径',
    `file_size` BIGINT COMMENT '文件大小(字节)',
    `mime_type` VARCHAR(100) COMMENT 'MIME类型',
    `remark` VARCHAR(500) COMMENT '用户备注',
    `upload_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `expire_at` DATETIME NOT NULL COMMENT '过期时间',
    INDEX `idx_apply_id` (`apply_id`),
    INDEX `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控人工复核补充材料';

-- 若列已存在会报 Duplicate column，可忽略
ALTER TABLE `risk_assessment`
    ADD COLUMN `supplement_status` VARCHAR(20) DEFAULT 'NONE'
        COMMENT 'NONE/REQUIRED/SUBMITTED' AFTER `audit_remark`;

ALTER TABLE `risk_assessment`
    ADD COLUMN `supplement_requirements` JSON NULL
        COMMENT '需补充材料 JSON 数组' AFTER `supplement_status`;
