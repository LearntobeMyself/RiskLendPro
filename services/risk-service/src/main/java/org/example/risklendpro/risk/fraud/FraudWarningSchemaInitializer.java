package org.example.risklendpro.risk.fraud;

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

/** 反欺诈预警表（阶段 A5+，幂等）。 */
@Component
@ConditionalOnProperty(prefix = "risk.schema", name = "auto-migrate-b-card-fraud",
        havingValue = "true", matchIfMissing = true)
public class FraudWarningSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FraudWarningSchemaInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public FraudWarningSchemaInitializer(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS b_card_fraud_warning (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        warn_date DATE NOT NULL,
                        rule_version VARCHAR(32) NOT NULL,
                        score INT NOT NULL,
                        level VARCHAR(16) NOT NULL,
                        summary VARCHAR(500) NULL,
                        signals_json TEXT NULL,
                        status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
                        acked_by VARCHAR(64) NULL,
                        acked_at DATETIME NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_fraud_user_date_rule (user_id, warn_date, rule_version),
                        KEY idx_fraud_level (warn_date, level)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B卡反欺诈/早期逾期预警'
                    """);
            log.info("反欺诈预警表检查完成");
        } catch (DataAccessException e) {
            log.warn("Skip fraud warning schema migration: {}", e.getMessage());
        }
    }
}