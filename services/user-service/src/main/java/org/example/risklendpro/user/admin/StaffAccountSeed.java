package org.example.risklendpro.user.admin;

import org.example.risklendpro.api.security.StaffRoles;
import org.example.risklendpro.user.entity.Admin;
import org.example.risklendpro.user.mapper.AdminMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 为五个运营角色各补一个演示账号（已存在则跳过）。密码与 README 管理端相同。
 */
@Component
@Order(30)
public class StaffAccountSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffAccountSeed.class);
    private static final String DEMO_PASSWORD = "Admin123456";

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;

    public StaffAccountSeed(AdminMapper adminMapper, PasswordEncoder passwordEncoder) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed("risk_mgr", StaffRoles.RISK_MANAGER, "13800001001", "risk.mgr@risklendpro.test");
            seed("collector", StaffRoles.COLLECTOR, "13800001002", "collector@risklendpro.test");
            seed("auditor", StaffRoles.AUDITOR, "13800001003", "auditor@risklendpro.test");
            seed("cs_agent", StaffRoles.CS_AGENT, "13800001004", "cs.agent@risklendpro.test");
        } catch (Exception e) {
            log.warn("Skip staff demo seed: {}", e.getMessage());
        }
    }

    private void seed(String username, String role, String phone, String email) {
        Admin existing = adminMapper.selectByUsername(username);
        if (existing != null) {
            if (existing.getRole() == null || existing.getRole().isBlank()) {
                existing.setRole(role);
                existing.setUpdateTime(new Date());
                adminMapper.updateById(existing);
            }
            return;
        }
        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        admin.setPhoneNumber(phone);
        admin.setEmail(email);
        admin.setRole(role);
        admin.setCreateTime(new Date());
        admin.setUpdateTime(new Date());
        adminMapper.insert(admin);
        log.info("Seeded staff account {} as {}", username, role);
    }

    public static List<String> demoUsernames() {
        return List.of("risk_mgr", "collector", "auditor", "cs_agent");
    }
}
