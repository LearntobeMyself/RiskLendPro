package org.example.risklendpro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.entity.LimitAdjustLog;
import org.example.risklendpro.entity.RiskAssessment;
import org.example.risklendpro.entity.User;
import org.example.risklendpro.entity.UserCreditLimit;
import org.example.risklendpro.entity.Admin;
import org.example.risklendpro.mapper.LimitAdjustLogMapper;
import org.example.risklendpro.mapper.RiskAssessmentMapper;
import org.example.risklendpro.mapper.UserCreditLimitMapper;
import org.example.risklendpro.mapper.UserMapper;
import org.example.risklendpro.mapper.AdminMapper;
import org.example.risklendpro.pojo.request.BatchCreditAdjustRequest;
import org.example.risklendpro.pojo.request.LimitAdjustRequest;
import org.example.risklendpro.pojo.response.LimitAdjustResponse;
import org.example.risklendpro.service.AdminCreditQueryService;
import org.example.risklendpro.service.UserCreditLimitService;
import org.example.risklendpro.utils.AdminDateHelper;
import org.example.risklendpro.utils.AdminEntityMapper;
import org.example.risklendpro.utils.AdminPageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminCreditQueryServiceImpl implements AdminCreditQueryService {

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AdminMapper adminMapper;
    @Autowired
    private RiskAssessmentMapper riskAssessmentMapper;
    @Autowired
    private LimitAdjustLogMapper limitAdjustLogMapper;
    @Autowired
    private UserCreditLimitService userCreditLimitService;

    @Override
    public Map<String, Object> getStats() {
        List<UserCreditLimit> limits = userCreditLimitMapper.selectList(null);
        BigDecimal totalLimit = sum(limits, UserCreditLimit::getTotalLimit);
        BigDecimal usedLimit = sum(limits, UserCreditLimit::getUsedLimit);
        BigDecimal available = sum(limits, UserCreditLimit::getRemainingLimit);
        double avgUsage = totalLimit.compareTo(BigDecimal.ZERO) > 0
                ? usedLimit.multiply(BigDecimal.valueOf(100))
                .divide(totalLimit, 1, RoundingMode.HALF_UP).doubleValue() : 0;
        Map<String, Object> data = new HashMap<>();
        data.put("totalCreditLimit", totalLimit);
        data.put("usedCreditLimit", usedLimit);
        data.put("availableCreditLimit", available);
        data.put("averageUsageRate", avgUsage);
        data.put("userCount", limits.size());
        data.put("trends", Map.of());
        return data;
    }

    @Override
    public Map<String, Object> listLimits(Integer page, Integer size, String userName, String phone,
                                          Boolean hasOverdue, String status) {
        QueryWrapper<UserCreditLimit> qw = new QueryWrapper<>();
        if (hasOverdue != null) {
            qw.eq("has_overdue", hasOverdue);
        }
        List<Long> userIdFilter = resolveUserIdFilter(userName, phone);
        if (userIdFilter != null) {
            if (userIdFilter.isEmpty()) {
                return AdminPageHelper.toListPage(List.of(), 0);
            }
            qw.in("user_id", userIdFilter);
        }
        qw.orderByDesc("last_update_time");

        if (status != null && !status.isBlank()) {
            List<UserCreditLimit> allLimits = userCreditLimitMapper.selectList(qw);
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (UserCreditLimit limit : allLimits) {
                User user = userMapper.selectById(limit.getUserId());
                if (!AdminEntityMapper.matchesCreditLimitStatus(status, user, limit)) {
                    continue;
                }
                RiskAssessment assessment = AdminEntityMapper.findLatestFinalAssessment(
                        riskAssessmentMapper, limit.getUserId());
                filtered.add(AdminEntityMapper.toCreditLimitItem(user, limit, assessment));
            }
            int from = Math.max(0, (page - 1) * size);
            int to = Math.min(filtered.size(), from + size);
            List<Map<String, Object>> pageList = from >= filtered.size() ? List.of() : filtered.subList(from, to);
            return AdminPageHelper.toListPage(pageList, filtered.size());
        }

        Page<UserCreditLimit> pageInfo = new Page<>(page, size);
        Page<UserCreditLimit> result = userCreditLimitMapper.selectPage(pageInfo, qw);
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserCreditLimit limit : result.getRecords()) {
            User user = userMapper.selectById(limit.getUserId());
            RiskAssessment assessment = AdminEntityMapper.findLatestFinalAssessment(
                    riskAssessmentMapper, limit.getUserId());
            list.add(AdminEntityMapper.toCreditLimitItem(user, limit, assessment));
        }
        return AdminPageHelper.toListPage(list, result.getTotal());
    }

    private List<Long> resolveUserIdFilter(String userName, String phone) {
        boolean hasUserName = userName != null && !userName.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        if (!hasUserName && !hasPhone) {
            return null;
        }
        QueryWrapper<User> userQw = new QueryWrapper<>();
        if (hasUserName) {
            userQw.like("real_name", userName);
        }
        if (hasPhone) {
            userQw.like("phone_number", phone);
        }
        return userMapper.selectList(userQw).stream().map(User::getId).toList();
    }

    @Override
    public Map<String, Object> adjustLimit(Long userId, LimitAdjustRequest request, Long operatorId) {
        request.setUserId(userId);
        LimitAdjustResponse response = userCreditLimitService.adjustLimit(request, operatorId);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("oldLimit", response.getOldLimit());
        data.put("newLimit", response.getNewLimit());
        return data;
    }

    @Override
    public Map<String, Object> batchAdjust(BatchCreditAdjustRequest request, Long operatorId) {
        int success = 0;
        int fail = 0;
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Long userId : request.getUserIds()) {
            try {
                UserCreditLimit limit = userCreditLimitMapper.selectOne(
                        new QueryWrapper<UserCreditLimit>().eq("user_id", userId));
                if (limit == null) {
                    throw new RuntimeException("额度不存在");
                }
                BigDecimal newLimit = calcNewLimit(limit.getTotalLimit(), request.getMode(), request.getValue());
                LimitAdjustRequest adjust = new LimitAdjustRequest();
                adjust.setUserId(userId);
                adjust.setNewLimit(newLimit);
                adjust.setReason(request.getReason());
                userCreditLimitService.adjustLimit(adjust, operatorId);
                success++;
            } catch (Exception e) {
                fail++;
                failed.add(Map.of("userId", userId, "reason", e.getMessage()));
            }
        }
        return Map.of("successCount", success, "failCount", fail, "failedItems", failed);
    }

    @Override
    public Map<String, Object> getAdjustHistory(Long userId, Integer page, Integer size) {
        Page<LimitAdjustLog> pageInfo = new Page<>(page, size);
        QueryWrapper<LimitAdjustLog> qw = new QueryWrapper<LimitAdjustLog>()
                .eq("user_id", userId).orderByDesc("adjust_time");
        Page<LimitAdjustLog> result = limitAdjustLogMapper.selectPage(pageInfo, qw);
        List<Map<String, Object>> list = result.getRecords().stream().map(log -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", log.getId());
            item.put("userId", log.getUserId());
            item.put("oldLimit", log.getOldLimit());
            item.put("newLimit", log.getNewLimit());
            item.put("reason", log.getReason());
            item.put("operatorId", log.getOperatorId());
            Admin admin = log.getOperatorId() != null && log.getOperatorId() > 0
                    ? adminMapper.selectById(log.getOperatorId()) : null;
            item.put("operatorName", AdminEntityMapper.resolveOperatorLabel(
                    log.getOperatorId(), admin != null ? admin.getUsername() : null));
            item.put("adjustTime", AdminDateHelper.formatDateTime(log.getAdjustTime()));
            return item;
        }).toList();
        return AdminPageHelper.toListPage(list, result.getTotal());
    }

    @Override
    public Map<String, Object> getOverdueRules() {
        Map<String, Object> data = new HashMap<>();
        data.put("bCardCoefficients", List.of(
                Map.of("minScore", 700, "maxScore", 850, "multiplier", 1.0),
                Map.of("minScore", 600, "maxScore", 699, "multiplier", 0.9),
                Map.of("minScore", 0, "maxScore", 599, "multiplier", 0.7)
        ));
        data.put("overdueRules", List.of(
                Map.of("level", "M1", "multiplier", 0.8, "description", "逾期M1降额20%"),
                Map.of("level", "M2", "multiplier", 0.5, "description", "逾期M2降额50%"),
                Map.of("level", "M3", "multiplier", 0.3, "description", "逾期M3降额70%"),
                Map.of("level", "M4", "multiplier", 0.0, "description", "逾期M4冻结额度")
        ));
        return data;
    }

    private BigDecimal calcNewLimit(BigDecimal current, String mode, BigDecimal value) {
        if (mode == null || value == null) {
            throw new RuntimeException("批量调整参数不完整");
        }
        return switch (mode) {
            case "FIXED" -> value;
            case "INCREASE" -> current.add(value);
            case "DECREASE" -> current.subtract(value).max(BigDecimal.ZERO);
            case "PERCENT" -> current.multiply(value).setScale(2, RoundingMode.HALF_UP);
            default -> throw new RuntimeException("不支持的调整模式: " + mode);
        };
    }

    private BigDecimal sum(List<UserCreditLimit> limits, java.util.function.Function<UserCreditLimit, BigDecimal> getter) {
        return limits.stream()
                .map(l -> {
                    BigDecimal v = getter.apply(l);
                    return v != null ? v : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
