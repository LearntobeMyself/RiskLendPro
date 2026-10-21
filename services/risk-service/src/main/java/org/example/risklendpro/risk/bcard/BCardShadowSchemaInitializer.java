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

/** 影子决策表结构（阶段 A4，幂等）。 */
@Component
@ConditionalOnProperty(prefix = "risk.schema", name = "auto-migrate-b-card-shadow",
        havingValue = "true", matchIfMissing = true)
public class BCardShadowSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BCardShadowSchemaInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public BCardShadowSchemaInitializer(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS b_card_shadow_decision (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        as_of_date DATE NOT NULL,
                        model_version VARCHAR(64) NOT NULL,
                        feature_version VARCHAR(32) NOT NULL,
                        model_probability DECIMAL(10,6) NULL,
                        model_score INT NULL,
                        model_multiplier DECIMAL(6,3) NULL,
                        current_multiplier DECIMAL(6,3) NULL,
                        decision_differs TINYINT(1) NOT NULL DEFAULT 0,
                        reason VARCHAR(200) NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_shadow_user_asof_model (user_id, as_of_date, model_version),
                        KEY idx_shadow_asof (as_of_date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B卡影子决策记录（只读影子，不影响额度）'
                    """);
            log.info("B 卡影子决策表检查完成");
        } catch (DataAccessException e) {
            log.warn("Skip B-card shadow schema migration: {}", e.getMessage());
        }
    }
}