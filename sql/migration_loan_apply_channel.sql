-- 借款快照：直接借 / 选产品。历史行 product_id 为空、apply_channel 视为 DIRECT。
ALTER TABLE `loan`
    ADD COLUMN `product_id` BIGINT NULL COMMENT '目录产品ID，直接借款为空' AFTER `operator_id`,
    ADD COLUMN `product_name` VARCHAR(64) NULL COMMENT '申请时产品名称快照' AFTER `product_id`,
    ADD COLUMN `apply_channel` VARCHAR(16) NULL DEFAULT 'DIRECT' COMMENT 'DIRECT/PRODUCT' AFTER `product_name`;
