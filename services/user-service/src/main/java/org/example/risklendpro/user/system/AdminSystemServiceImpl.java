package org.example.risklendpro.user.system;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.api.dto.OperationLogItem;
import org.example.risklendpro.user.client.LoanAdminQueryClient;
import org.example.risklendpro.user.client.RiskAdminQueryClient;
import org.example.risklendpro.user.entity.Admin;
import org.example.risklendpro.user.mapper.AdminMapper;
import org.example.risklendpro.user.admin.AdminCreateRequest;
import org.example.risklendpro.user.admin.AdminUpdateRequest;
import org.example.risklendpro.user.system.SystemConfigUpdateRequest;
import org.example.risklendpro.user.system.AdminSystemService;
import org.example.risklendpro.common.admin.AdminDateHelper;
import org.example.risklendpro.common.admin.AdminPageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
public class AdminSystemServiceImpl implements AdminSystemService {

    private final Map<String, Object> configStore = new ConcurrentHashMap<>(Map.of(
            "emailEnabled", true,
            "logRetentionDays", 90,
            "backupRetentionDays", 30,
            "systemName", "RiskLendPro"
    ));

    @Value("${risk.backup.dir:upload/backup}")
    private String backupDir;

    @Autowired
    private AdminMapper adminMapper;
    @Autowired
    private LoanAdminQueryClient loanAdminQueryClient;
    @Autowired
    private RiskAdminQueryClient riskAdminQueryClient;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public Map<String, Object> listAdmins(Integer page, Integer size) {
        Page<Admin> pageInfo = new Page<>(page, size);
        Page<Admin> result = adminMapper.selectPage(pageInfo, new QueryWrapper<Admin>().orderByDesc("create_time"));
        List<Map<String, Object>> list = result.getRecords().stream().map(admin -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", admin.getId());
            item.put("username", admin.getUsername());
            item.put("phoneNumber", admin.getPhoneNumber());
            item.put("email", admin.getEmail());
            item.put("role", "SUPER_ADMIN");
            item.put("createTime", AdminDateHelper.formatDateTime(admin.getCreateTime()));
            return item;
        }).toList();
        return AdminPageHelper.toListPage(list, result.getTotal());
    }

    @Override
    public Map<String, Object> createAdmin(AdminCreateRequest request) {
        Admin existing = adminMapper.selectByUsername(request.getUsername());
        if (existing != null) {
            throw new RuntimeException("用户名已存在");
        }
        Admin admin = new Admin();
        admin.setUsername(request.getUsername());
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setPhoneNumber(request.getPhoneNumber());
        admin.setEmail(request.getEmail());
        admin.setCreateTime(new Date());
        admin.setUpdateTime(new Date());
        adminMapper.insert(admin);
        return Map.of("adminId", admin.getId());
    }

    @Override
    public Map<String, Object> updateAdmin(Long adminId, AdminUpdateRequest request) {
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new RuntimeException("管理员不存在");
        }
        if (request.getUsername() != null) {
            admin.setUsername(request.getUsername());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            admin.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getPhoneNumber() != null) {
            admin.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getEmail() != null) {
            admin.setEmail(request.getEmail());
        }
        admin.setUpdateTime(new Date());
        adminMapper.updateById(admin);
        return Map.of("adminId", adminId);
    }

    @Override
    public void deleteAdmin(Long adminId) {
        if (adminMapper.selectCount(null) <= 1) {
            throw new RuntimeException("至少保留一名管理员");
        }
        adminMapper.deleteById(adminId);
    }

    @Override
    public Map<String, Object> listOperationLogs(Integer page, Integer size, String module,
                                                String startDate, String endDate) {
        List<Map<String, Object>> all = new ArrayList<>();
        Date start = AdminDateHelper.parseDateStart(startDate);
        Date end = AdminDateHelper.parseDateEnd(endDate);

        for (OperationLogItem log : loanAdminQueryClient.listCreditAdjustLogs()) {
            if (!inRange(log.createTime(), start, end)) {
                continue;
            }
            all.add(operationLogEntry(log));
        }
        for (OperationLogItem log : loanAdminQueryClient.listLoanApprovalLogs()) {
            if (!inRange(log.createTime(), start, end)) {
                continue;
            }
            all.add(operationLogEntry(log));
        }
        for (OperationLogItem log : riskAdminQueryClient.listRiskApprovalLogs()) {
            if (!inRange(log.createTime(), start, end)) {
                continue;
            }
            all.add(operationLogEntry(log));
        }

        if (module != null && !module.isBlank()) {
            all = all.stream().filter(l -> module.equals(l.get("module"))).toList();
        }
        all.sort(Comparator.comparing(l -> (String) l.get("createTime"), Comparator.reverseOrder()));

        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        return AdminPageHelper.toListPage(from >= all.size() ? List.of() : all.subList(from, to), all.size());
    }

    @Override
    public Map<String, Object> getConfig() {
        return new HashMap<>(configStore);
    }

    @Override
    public Map<String, Object> updateConfig(SystemConfigUpdateRequest request) {
        if (request.getEmailEnabled() != null) {
            configStore.put("emailEnabled", request.getEmailEnabled());
        }
        if (request.getLogRetentionDays() != null) {
            configStore.put("logRetentionDays", request.getLogRetentionDays());
        }
        if (request.getBackupRetentionDays() != null) {
            configStore.put("backupRetentionDays", request.getBackupRetentionDays());
        }
        if (request.getSystemName() != null) {
            configStore.put("systemName", request.getSystemName());
        }
        return getConfig();
    }

    @Override
    public Map<String, Object> listBackups(Integer page, Integer size) {
        ensureBackupDir();
        List<Map<String, Object>> all = new ArrayList<>();
        try (Stream<Path> stream = Files.list(Paths.get(backupDir))) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                Map<String, Object> item = new HashMap<>();
                String filename = path.getFileName().toString();
                item.put("id", filename);
                item.put("filename", filename);
                try {
                    item.put("size", Files.size(path));
                    item.put("createTime", AdminDateHelper.formatDateTime(
                            new Date(Files.getLastModifiedTime(path).toMillis())));
                } catch (IOException ignored) {
                    item.put("size", 0);
                }
                item.put("status", "READY");
                all.add(item);
            });
        } catch (IOException e) {
            throw new RuntimeException("读取备份目录失败", e);
        }
        all.sort(Comparator.comparing(l -> (String) l.get("createTime"), Comparator.reverseOrder()));
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        return AdminPageHelper.toListPage(from >= all.size() ? List.of() : all.subList(from, to), all.size());
    }

    @Override
    public Map<String, Object> createBackup() {
        ensureBackupDir();
        String filename = "backup-" + System.currentTimeMillis() + ".sql";
        Path file = Paths.get(backupDir, filename);
        try {
            Files.writeString(file, "-- RiskLendPro placeholder backup\n-- generated at "
                    + AdminDateHelper.formatDateTime(new Date()) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("创建备份失败", e);
        }
        return Map.of("backupId", filename, "filename", filename, "status", "CREATED");
    }

    @Override
    public Map<String, Object> restoreBackup(String backupId) {
        Path file = Paths.get(backupDir, backupId);
        if (!Files.exists(file)) {
            throw new RuntimeException("备份文件不存在");
        }
        return Map.of("backupId", backupId, "status", "RESTORED", "message", "占位恢复成功，生产环境需接入真实备份恢复流程");
    }

    @Override
    public byte[] downloadBackup(String backupId) {
        Path file = Paths.get(backupDir, backupId);
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new RuntimeException("下载备份失败", e);
        }
    }

    private void ensureBackupDir() {
        try {
            Files.createDirectories(Paths.get(backupDir));
        } catch (IOException e) {
            throw new RuntimeException("创建备份目录失败", e);
        }
    }

    private Map<String, Object> operationLogEntry(OperationLogItem log) {
        long time = log.createTime() != null ? log.createTime() : System.currentTimeMillis();
        Map<String, Object> item = new HashMap<>();
        item.put("id", log.module() + "-" + time);
        item.put("module", log.module());
        item.put("action", log.action());
        item.put("operatorId", log.operatorId());
        item.put("operatorName", log.operatorName() != null ? log.operatorName() : "System");
        item.put("detail", log.detail() != null ? log.detail() : "");
        item.put("createTime", AdminDateHelper.formatDateTime(new Date(time)));
        return item;
    }

    private boolean inRange(Long time, Date start, Date end) {
        return time != null && inRange(new Date(time), start, end);
    }

    private boolean inRange(Date time, Date start, Date end) {
        if (time == null) {
            return false;
        }
        if (start != null && time.before(start)) {
            return false;
        }
        return end == null || time.before(end);
    }
}
