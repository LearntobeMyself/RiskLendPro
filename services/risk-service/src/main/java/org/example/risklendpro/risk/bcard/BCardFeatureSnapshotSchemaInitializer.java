package org.example.risklendpro.risk.bcard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * B 卡 V2 快照表结构自愈（阶段 A2）。
 *
 * <p>只做结构补齐，不触碰已有快照内容，也不会提前生成任何标签：
 * label_30dpd_6m / label_90dpd_12m 仍由标签任务在表现期结束后写入。</p>
 */
@Component
@ConditionalOnProperty(prefix = "risk.schema", name = "auto-migrate-b-card-v2",
        havingValue = "true", matchIfMissing = true)
public class BCardFeatureSnapshotSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BCardFeatureSnapshotSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public BCardFeatureSnapshotSchemaInitializer(
            @Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS b_card_feature_snapshot (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        as_of_date DATE NOT NULL,
                        snapshot_time DATETIME NOT NULL,
                        feature_version VARCHAR(32) NOT NULL,
                        observation_start DATE NOT NULL,
                        observation_end DATE NOT NULL,
                        performance_start DATE NULL,
                        performance_end DATE NULL,
                        sample_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
                        label_30dpd_6m TINYINT NULL,
                        label_90dpd_12m TINYINT NULL,
                        feature_json JSON NOT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        maturity_date_30dpd_6m DATE NULL,
                        maturity_date_90dpd_12m DATE NULL,
                        label_version VARCHAR(32) NULL,
                        label_calculated_at DATETIME NULL,
                        data_quality_status VARCHAR(20) NULL,
                        feature_hash CHAR(64) NULL,
                        data_source VARCHAR(128) NULL,
                        UNIQUE KEY uk_user_asof_version (user_id, as_of_date, feature_version),
                        KEY idx_asof_status (as_of_date, sample_status),
                        KEY idx_user_time (user_id, snapshot_time),
                        KEY idx_status_maturity (sample_status, maturity_date_30dpd_6m)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B卡V2真实特征快照'
                    """);

            boolean added6m = addColumnIfMissing("maturity_date_30dpd_6m",
                    "maturity_date_30dpd_6m DATE NULL COMMENT 'label_30dpd_6m 表现期结束日'");
            boolean added12m = addColumnIfMissing("maturity_date_90dpd_12m",
                    "maturity_date_90dpd_12m DATE NULL COMMENT 'label_90dpd_12m 表现期结束日'");
            addColumnIfMissing("label_version", "label_version VARCHAR(32) NULL COMMENT '标签口径版本'");
            addColumnIfMissing("label_calculated_at", "label_calculated_at DATETIME NULL COMMENT '标签计算时间'");
            addColumnIfMissing("data_quality_status",
                    "data_quality_status VARCHAR(20) NULL COMMENT 'OK / DEGRADED / INSUFFICIENT'");
            addColumnIfMissing("feature_hash", "feature_hash CHAR(64) NULL COMMENT 'feature_json 内容 SHA-256'");
            addColumnIfMissing("data_source", "data_source VARCHAR(128) NULL COMMENT '特征数据来源说明'");

            if (added6m) {
                jdbcTemplate.update("UPDATE b_card_feature_snapshot "
                        + "SET maturity_date_30dpd_6m = DATE_ADD(as_of_date, INTERVAL 6 MONTH) "
                        + "WHERE maturity_date_30dpd_6m IS NULL");
            }
            if (added12m) {
                jdbcTemplate.update("UPDATE b_card_feature_snapshot "
                        + "SET maturity_date_90dpd_12m = DATE_ADD(as_of_date, INTERVAL 12 MONTH) "
                        + "WHERE maturity_date_90dpd_12m IS NULL");
            }

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS user_credit_limit_history (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        snapshot_date DATE NOT NULL,
                        total_limit DECIMAL(15,2) NOT NULL DEFAULT 0,
                        used_limit DECIMAL(15,2) NOT NULL DEFAULT 0,
                        remaining_limit DECIMAL(15,2) NOT NULL DEFAULT 0,
                        overdue_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
                        has_overdue TINYINT(1) NOT NULL DEFAULT 0,
                        b_card_enabled TINYINT(1) NOT NULL DEFAULT 0,
                        b_score DECIMAL(10,2) NULL,
                        data_source VARCHAR(64) NOT NULL DEFAULT 'user_credit_limit_daily',
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_user_snapshot_date (user_id, snapshot_date),
                        KEY idx_user_date (user_id, snapshot_date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度历史快照（不可变，供 as-of 复现）'
                    """);

            log.info("B 卡 V2 特征快照与额度历史表检查完成");
        } catch (DataAccessException e) {
            log.warn("Skip B-card V2 schema migration: {}", e.getMessage());
        }
    }

    private boolean addColumnIfMissing(String column, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'b_card_feature_snapshot' AND COLUMN_NAME = ?",
                Integer.class, column);
        if (count != null && count > 0) {
            return false;
        }
        jdbcTemplate.execute("ALTER TABLE b_card_feature_snapshot ADD COLUMN " + ddl);
        log.info("B 卡快照表补齐列 {}", column);
        return true;
    }
}