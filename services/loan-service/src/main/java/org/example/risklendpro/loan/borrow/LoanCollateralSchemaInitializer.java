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
@Order(41)
@ConditionalOnProperty(prefix = "loan.schema", name = "auto-migrate-collateral", havingValue = "true", matchIfMissing = true)
public class LoanCollateralSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoanCollateralSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public LoanCollateralSchemaInitializer(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            addColumnIfMissing("collateral_type",
                    "ALTER TABLE `loan` ADD COLUMN `collateral_type` VARCHAR(16) NULL COMMENT '抵押物类型 HOUSE/VEHICLE，信用类为空' AFTER `apply_channel`");
            addColumnIfMissing("collateral_value",
                    "ALTER TABLE `loan` ADD COLUMN `collateral_value` DECIMAL(18,2) NULL COMMENT '抵押物评估价（管理员确认时录入）' AFTER `collateral_type`");
            addColumnIfMissing("collateral_rate",
                    "ALTER TABLE `loan` ADD COLUMN `collateral_rate` DECIMAL(6,2) NULL COMMENT '抵押率（百分数），放款时按评估价×抵押率封顶' AFTER `collateral_value`");
        } catch (CannotGetJdbcConnectionException e) {
            log.warn("Skip loan collateral migration: database unreachable ({})", e.getMessage());
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "loan 抵押物列自动迁移失败，请手工执行 sql/migration_loan_collateral.sql。原因: " + e.getMessage(), e);
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
