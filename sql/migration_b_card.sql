-- B 卡贷后监控：与 A 卡授信分离，借款成功后才启用

ALTER TABLE user_credit_limit
  ADD COLUMN b_card_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'B卡是否已启动';

ALTER TABLE user_credit_limit
  ADD COLUMN b_score DECIMAL(6,1) NULL COMMENT '最新B卡综合分';

ALTER TABLE user_credit_limit
  ADD COLUMN b_score_updated_at DATETIME NULL COMMENT 'B分更新时间';

CREATE TABLE IF NOT EXISTS user_b_card_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  base_score DECIMAL(6,1) NULL COMMENT 'HC基线B分',
  delta_score DECIMAL(6,1) NULL COMMENT '本项目还款动态修正',
  final_score DECIMAL(6,1) NULL COMMENT '最终B分',
  live_features JSON NULL COMMENT '实时还款特征快照',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B卡评分历史';
