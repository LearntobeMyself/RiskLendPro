package org.example.risklendpro.user.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.api.dto.CreditLimitSnapshot;
import org.example.risklendpro.api.dto.LoanUserSummaryItem;
import org.example.risklendpro.api.dto.RiskAssessmentSummary;
import org.example.risklendpro.user.client.LoanServiceClient;
import org.example.risklendpro.user.client.RiskServiceClient;
import org.example.risklendpro.user.entity.User;
import org.example.risklendpro.user.mapper.UserMapper;
import org.example.risklendpro.user.admin.AdminUserCreateRequest;
import org.example.risklendpro.user.admin.AdminUserStatusRequest;
import org.example.risklendpro.user.admin.AdminUserUpdateRequest;
import org.example.risklendpro.user.admin.AdminUserService;
import org.example.risklendpro.common.admin.AdminDateHelper;
import org.example.risklendpro.common.admin.AdminEntityMapper;
import org.example.risklendpro.common.admin.AdminExportHelper;
import org.example.risklendpro.common.admin.AdminPageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserServiceImpl.class);

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private LoanServiceClient loanServiceClient;
    @Autowired
    private RiskServiceClient riskServiceClient;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AdminExportHelper adminExportHelper;

    @Value("${risk.admin.default-user-password:123456}")
    private String defaultPassword;

    @Override
    public Map<String, Object> listUsers(Integer page, Integer size, String status, String name, String phone) {
        Page<User> pageInfo = new Page<>(page, size);
        QueryWrapper<User> qw = buildUserQuery(status, name, phone);
        Page<User> result = userMapper.selectPage(pageInfo, qw);
        List<User> records = result.getRecords();

        // 批量装配跨域数据，避免每行 3 次 Feign 调用（N+1）
        Map<Long, CreditLimitSnapshot> limits = batchCreditLimits(records);
        Map<Long, LoanUserSummaryItem> loanSummaries = batchLoanSummaries(records);
        Map<Long, RiskAssessmentSummary> assessments = batchLatestFinalAssessments(records);

        List<Map<String, Object>> list = records.stream()
                .map(u -> AdminEntityMapper.toUserListItem(
                        u,
                        limits.get(u.getId()),
                        assessments.get(u.getId()),
                        loanSummaries.get(u.getId())))
                .toList();
        return AdminPageHelper.toListPage(list, result.getTotal());
    }

    private Map<Long, CreditLimitSnapshot> batchCreditLimits(List<User> records) {
        List<Long> ids = records.stream().map(User::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, CreditLimitSnapshot> limits = loanServiceClient.listCreditLimits(ids);
            return limits == null ? Map.of() : limits;
        } catch (RuntimeException ex) {
            log.warn("credit limit enrichment skipped: {}", ex.getMessage());
            return Map.of();
        }
    }

    private Map<Long, LoanUserSummaryItem> batchLoanSummaries(List<User> records) {
        List<Long> ids = records.stream().map(User::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, LoanUserSummaryItem> summaries = loanServiceClient.listUserLoanSummaries(ids);
            return summaries == null ? Map.of() : summaries;
        } catch (RuntimeException ex) {
            log.warn("loan summary enrichment skipped: {}", ex.getMessage());
            return Map.of();
        }
    }

    private Map<Long, RiskAssessmentSummary> batchLatestFinalAssessments(List<User> records) {
        List<Long> ids = records.stream().map(User::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, RiskAssessmentSummary> assessments = riskServiceClient.listLatestFinalAssessments(ids);
            return assessments == null ? Map.of() : assessments;
        } catch (RuntimeException ex) {
            log.warn("risk assessment enrichment skipped: {}", ex.getMessage());
            return Map.of();
        }
    }

    @Override
    public Map<String, Object> getUserDetail(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        Map<String, Object> data = toUserListItem(user);
        data.put("email", user.getEmail());
        data.put("idCard", user.getIdCard());
        data.put("lastLoginTime", AdminDateHelper.formatDateTime(user.getUpdateTime()));
        return data;
    }

    @Override
    public Map<String, Object> createUser(AdminUserCreateRequest request) {
        if (request.getPhone() != null) {
            User existing = userMapper.selectOne(new QueryWrapper<User>().eq("phone_number", request.getPhone()));
            if (existing != null) {
                throw new RuntimeException("手机号已存在");
            }
        }
        User user = new User();
        user.setRealName(request.getName());
        user.setPhoneNumber(request.getPhone());
        user.setEmail(request.getEmail());
        user.setIdCard(request.getIdCard());
        user.setPassword(passwordEncoder.encode(defaultPassword));
        user.setRole("USER");
        user.setAccountStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());
        userMapper.insert(user);
        return Map.of("userId", user.getId());
    }

    @Override
    public Map<String, Object> updateUser(Long userId, AdminUserUpdateRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (request.getName() != null) {
            user.setRealName(request.getName());
        }
        if (request.getPhone() != null) {
            user.setPhoneNumber(request.getPhone());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getIdCard() != null) {
            user.setIdCard(request.getIdCard());
        }
        if (request.getStatus() != null) {
            user.setAccountStatus(request.getStatus());
        }
        user.setUpdateTime(new Date());
        userMapper.updateById(user);
        return Map.of("userId", userId);
    }

    @Override
    public Map<String, Object> updateUserStatus(Long userId, AdminUserStatusRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        String status = request.getStatus();
        if (status == null && request.getEnabled() != null) {
            status = Boolean.TRUE.equals(request.getEnabled()) ? "ACTIVE" : "DISABLED";
        }
        if (status == null) {
            throw new RuntimeException("状态不能为空");
        }
        user.setAccountStatus(status);
        user.setUpdateTime(new Date());
        userMapper.updateById(user);
        return Map.of("userId", userId, "status", status);
    }

    @Override
    public Map<String, Object> exportUsers(String status, String name, String phone) {
        List<User> users = userMapper.selectList(buildUserQuery(status, name, phone));
        // 批量预取额度/评估/贷款摘要，替代逐行 3 次 Feign（1 万用户即 3 万次 HTTP 的 N+1）
        List<Long> userIds = users.stream().map(User::getId).filter(Objects::nonNull).distinct().toList();
        Map<Long, CreditLimitSnapshot> limitMap = batchSafely(
                () -> loanServiceClient.listCreditLimits(userIds), Map.of());
        Map<Long, RiskAssessmentSummary> assessmentMap = batchSafely(
                () -> riskServiceClient.listLatestFinalAssessments(userIds), Map.of());
        Map<Long, LoanUserSummaryItem> loanMap = batchSafely(
                () -> loanServiceClient.listUserLoanSummaries(userIds), Map.of());
        List<String> headers = List.of("id", "name", "phone", "status", "creditScore", "creditLimit");
        List<List<String>> rows = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> item = AdminEntityMapper.toUserListItem(user,
                    limitMap.get(user.getId()), assessmentMap.get(user.getId()), loanMap.get(user.getId()));
            rows.add(List.of(
                    String.valueOf(item.get("id")),
                    String.valueOf(item.get("name")),
                    String.valueOf(item.get("phone")),
                    String.valueOf(item.get("status")),
                    String.valueOf(item.get("creditScore")),
                    String.valueOf(item.get("creditLimit"))
            ));
        }
        String path = adminExportHelper.writeCsv("users", headers, rows);
        return adminExportHelper.downloadMeta(path);
    }

    /** 批量调用失败时返回兜底空集合，不阻断导出主流程。 */
    private <K, V> Map<K, V> batchSafely(java.util.function.Supplier<Map<K, V>> call, Map<K, V> fallback) {
        try {
            Map<K, V> result = call.get();
            return result != null ? result : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private QueryWrapper<User> buildUserQuery(String status, String name, String phone) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq("account_status", status);
        }
        if (name != null && !name.isBlank()) {
            qw.like("real_name", name);
        }
        if (phone != null && !phone.isBlank()) {
            qw.like("phone_number", phone);
        }
        qw.orderByDesc("create_time");
        return qw;
    }

    private Map<String, Object> toUserListItem(User user) {
        CreditLimitSnapshot limit = loanServiceClient.getCreditLimit(user.getId());
        RiskAssessmentSummary assessment = riskServiceClient.getLatestFinalAssessment(user.getId());
        LoanUserSummaryItem loans = loanServiceClient.getUserLoanSummary(user.getId());
        return AdminEntityMapper.toUserListItem(user, limit, assessment, loans);
    }
}
