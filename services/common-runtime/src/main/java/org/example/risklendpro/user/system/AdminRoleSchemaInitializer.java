package org.example.risklendpro.user.system;

import org.example.risklendpro.api.security.StaffRoles;
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

/**
 * 给云库 {@code admin} 表补 {@code role} 列，并把存量管理员归一为 SYS_ADMIN。
 * 必须在第一次 AdminMapper 查询之前完成，否则 MyBatis 会报 Unknown column 'role'。
 */
@Component
@Order(20)
@ConditionalOnProperty(prefix = "risk.schema", name = "auto-migrate-admin-role",
        havingValue = "true", matchIfMissing = true)
public class AdminRoleSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminRoleSchemaInitializer.class);

    private static final String[][] DEMO_STAFF = {
            {"risk_mgr", StaffRoles.RISK_MANAGER, "13800001001", "risk.mgr@risklendpro.test"},
            {"collector", StaffRoles.COLLECTOR, "13800001002", "collector@risklendpro.test"},
            {"auditor", StaffRoles.AUDITOR, "13800001003", "auditor@risklendpro.test"},
            {"cs_agent", StaffRoles.CS_AGENT, "13800001004", "cs.agent@risklendpro.test"}
    };

    private final JdbcTemplate jdbcTemplate;

    public AdminRoleSchemaInitializer(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin' AND COLUMN_NAME = 'role'",
                    Integer.class);
            if (count == null || count == 0) {
                log.info("Remote admin table missing column role, adding VARCHAR(32) NOT NULL DEFAULT SYS_ADMIN");
                jdbcTemplate.execute(
                        "ALTER TABLE `admin` ADD COLUMN `role` VARCHAR(32) NOT NULL DEFAULT 'SYS_ADMIN' "
                                + "COMMENT 'RISK_MANAGER/COLLECTOR/AUDITOR/CS_AGENT/SYS_ADMIN' AFTER `email`");
                log.info("Added admin.role on {}", currentSchema());
            } else {
                log.info("admin.role already present");
            }
            int updated = jdbcTemplate.update(
                    "UPDATE `admin` SET `role` = ? WHERE `role` IS NULL OR `role` = '' OR `role` IN ('ADMIN','SUPER_ADMIN')",
                    StaffRoles.SYS_ADMIN);
            if (updated > 0) {
                log.info("Normalized {} admin.role value(s) to SYS_ADMIN", updated);
            }
            seedDemoStaff();
        } catch (CannotGetJdbcConnectionException e) {
            log.warn("Skip admin.role migration: database unreachable ({})", e.getMessage());
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "admin.role 自动迁移失败，请手工执行 sql/migration_admin_role.sql。原因: " + e.getMessage(), e);
        }
    }

    private void seedDemoStaff() {
        Integer adminExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `admin` WHERE username = 'admin'", Integer.class);
        if (adminExists == null || adminExists == 0) {
            log.warn("Skip staff seed: no username=admin row to copy password from");
            return;
        }
        for (String[] row : DEMO_STAFF) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `admin` WHERE username = ?", Integer.class, row[0]);
            if (exists != null && exists > 0) {
                continue;
            }
            int inserted = jdbcTemplate.update(
                    "INSERT INTO `admin` (username, password, phone_number, email, role, create_time, update_time) "
                            + "SELECT ?, password, ?, ?, ?, NOW(), NOW() FROM `admin` WHERE username = 'admin' LIMIT 1",
                    row[0], row[2], row[3], row[1]);
            if (inserted > 0) {
                log.info("Seeded staff account {} as {}", row[0], row[1]);
            }
        }
    }

    private String currentSchema() {
        try {
            return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        } catch (DataAccessException ignored) {
            return "RiskLendPro";
        }
    }
}
