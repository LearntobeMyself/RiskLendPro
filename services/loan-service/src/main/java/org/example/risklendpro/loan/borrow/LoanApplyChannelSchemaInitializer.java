package org.example.risklendpro.loan.borrow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Order(40)
@ConditionalOnProperty(prefix = "loan.schema", name = "auto-migrate-apply-channel", havingValue = "true", matchIfMissing = true)
public class LoanApplyChannelSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoanApplyChannelSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public LoanApplyChannelSchemaInitializer(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            addColumnIfMissing("product_id",
                    "ALTER TABLE `loan` ADD COLUMN `product_id` BIGINT NULL COMMENT '目录产品ID，直接借款为空' AFTER `operator_id`");
            addColumnIfMissing("product_name",
                    "ALTER TABLE `loan` ADD COLUMN `product_name` VARCHAR(64) NULL COMMENT '申请时产品名称快照' AFTER `product_id`");
            addColumnIfMissing("apply_channel",
                    "ALTER TABLE `loan` ADD COLUMN `apply_channel` VARCHAR(16) NULL DEFAULT 'DIRECT' COMMENT 'DIRECT/PRODUCT' AFTER `product_name`");
        } catch (CannotGetJdbcConnectionException e) {
            log.warn("Skip loan apply-channel migration: database unreachable ({})", e.getMessage());
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "loan 申请通路列自动迁移失败，请手工执行 sql/migration_loan_apply_channel.sql。原因: " + e.getMessage(), e);
        }
    }

    private void addColumnIfMissing(String column, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'loan' AND COLUMN_NAME = ?",
                Integer.class, column);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(ddl);
        log.info("Added loan.{}", column);
    }
}
