-- 抵押类借据：房抵 / 车抵。信用类借据此三列为空。
-- collateral_value / collateral_rate 由管理员在确认抵押物时录入。
ALTER TABLE `loan`
    ADD COLUMN `collateral_type` VARCHAR(16) NULL COMMENT '抵押物类型 HOUSE/VEHICLE，信用类为空' AFTER `apply_channel`,
    ADD COLUMN `collateral_value` DECIMAL(18,2) NULL COMMENT '抵押物评估价（管理员确认时录入）' AFTER `collateral_type`,
    ADD COLUMN `collateral_rate` DECIMAL(6,2) NULL COMMENT '抵押率（百分数），放款时按评估价×抵押率封顶' AFTER `collateral_value`;
