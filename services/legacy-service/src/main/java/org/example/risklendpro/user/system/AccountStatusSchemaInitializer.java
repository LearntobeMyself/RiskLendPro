package org.example.risklendpro.user.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@ConditionalOnProperty(prefix = "risk.schema", name = "auto-migrate-account-status",
        havingValue = "true", matchIfMissing = true)
public class AccountStatusSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public AccountStatusSchemaInitializer(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'account_status'",
                    Integer.class);
            if (count != null && count > 0) {
                return;
            }
            log.info("Adding missing column user.account_status");
            jdbcTemplate.execute(
                    "ALTER TABLE `user` ADD COLUMN `account_status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' " +
                            "COMMENT 'ACTIVE/DISABLED/FROZEN' AFTER `assessment_status`");
        } catch (CannotGetJdbcConnectionException e) {
            log.warn("Skip account_status migration: database unreachable ({})", e.getMessage());
        } catch (DataAccessException e) {
            log.warn("Skip account_status migration: {}", e.getMessage());
        }
    }
}
