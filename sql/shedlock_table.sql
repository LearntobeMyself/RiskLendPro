-- ShedLock 分布式调度锁表（放主库 PRIMARY，需正式/预发布时手工执行一次）
CREATE TABLE IF NOT EXISTS `shedlock` (
    `name`       VARCHAR(64)  NOT NULL,
    `lock_until` TIMESTAMP(3) NULL,
    `locked_at`  TIMESTAMP(3) NULL,
    `locked_by`  VARCHAR(255) NOT NULL,
    PRIMARY KEY (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;