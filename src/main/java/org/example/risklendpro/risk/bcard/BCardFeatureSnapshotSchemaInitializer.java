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
                        UNIQUE KEY uk_user_asof_version (user_id, as_of_date, feature_version),
                        KEY idx_asof_status (as_of_date, sample_status),
                        KEY idx_user_time (user_id, snapshot_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B卡V2真实特征快照'
                    """);
            log.info("B 卡 V2 特征快照表检查完成");
        } catch (DataAccessException e) {
            log.warn("Skip B-card V2 schema migration: {}", e.getMessage());
        }
    }
}
