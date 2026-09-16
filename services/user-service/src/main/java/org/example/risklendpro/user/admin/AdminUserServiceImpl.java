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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class AdminUserServiceImpl implements AdminUserService {

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
        List<Map<String, Object>> list = result.getRecords().stream().map(this::toUserListItem).toList();
        return AdminPageHelper.toListPage(list, result.getTotal());
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
        List<String> headers = List.of("id", "name", "phone", "status", "creditScore", "creditLimit");
        List<List<String>> rows = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> item = toUserListItem(user);
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
