package org.example.risklendpro.risk.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.risk.entity.RiskAssessment;
import org.example.risklendpro.user.entity.User;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.risk.credit.UserExternalFeatures;
import org.example.risklendpro.risk.mapper.RiskAssessmentMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.user.mapper.UserMapper;
import org.example.risklendpro.risk.credit.mapper.UserExternalFeaturesMapper;
import org.example.risklendpro.risk.admin.AntiFraudHandleRequest;
import org.example.risklendpro.risk.admin.AdminRiskDataService;
import org.example.risklendpro.risk.admin.AdminRiskQueryService;
import org.example.risklendpro.risk.admin.AdminReportDisplayBuilder;
import org.example.risklendpro.common.admin.AdminDateHelper;
import org.example.risklendpro.common.admin.AdminExportHelper;
import org.example.risklendpro.common.admin.AdminPageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminRiskDataServiceImpl implements AdminRiskDataService {

    private final Map<String, Map<String, Object>> antiFraudHandleStore = new ConcurrentHashMap<>();

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RiskAssessmentMapper riskAssessmentMapper;
    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;
    @Autowired
    private UserExternalFeaturesMapper userExternalFeaturesMapper;
    @Autowired
    private AdminExportHelper adminExportHelper;
    @Autowired
    private AdminRiskQueryService adminRiskQueryService;
    @Autowired
    private AdminReportDisplayBuilder adminReportDisplayBuilder;

    @Override
    public Map<String, Object> getOverview() {
        long userCount = userMapper.selectCount(null);
        List<RiskAssessment> finals = riskAssessmentMapper.selectList(
                new QueryWrapper<RiskAssessment>().eq("is_final", true));
        long scoreHigh = finals.stream().filter(a -> a.getTotalScore() != null && a.getTotalScore() >= 80).count();
        long scoreMid = finals.stream().filter(a -> a.getTotalScore() != null && a.getTotalScore() >= 60 && a.getTotalScore() < 80).count();
        long scoreLow = finals.stream().filter(a -> a.getTotalScore() != null && a.getTotalScore() < 60).count();

        List<UserCreditLimit> limits = userCreditLimitMapper.selectList(
                new QueryWrapper<UserCreditLimit>().eq("b_card_enabled", true));
        long bHigh = limits.stream().filter(l -> l.getBScore() != null && l.getBScore().doubleValue() >= 700).count();
        long bMid = limits.stream().filter(l -> l.getBScore() != null && l.getBScore().doubleValue() >= 600 && l.getBScore().doubleValue() < 700).count();
        long bLow = limits.stream().filter(l -> l.getBScore() == null || l.getBScore().doubleValue() < 600).count();

        Map<String, Object> data = new HashMap<>();
        data.put("totalUsers", userCount);
        data.put("aScoreDistribution", List.of(
                Map.of("level", "HIGH", "count", scoreHigh),
                Map.of("level", "MID", "count", scoreMid),
                Map.of("level", "LOW", "count", scoreLow)
        ));
        data.put("bScoreDistribution", List.of(
                Map.of("level", "HIGH", "count", bHigh),
                Map.of("level", "MID", "count", bMid),
                Map.of("level", "LOW", "count", bLow)
        ));
        return data;
    }

    @Override
    public Map<String, Object> listCreditScores(Integer page, Integer size, String userName, String riskLevel) {
        Page<RiskAssessment> pageInfo = new Page<>(page, size);
        QueryWrapper<RiskAssessment> qw = new QueryWrapper<RiskAssessment>().eq("is_final", true);
        if (userName != null && !userName.isBlank()) {
            qw.like("name", userName);
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            if ("LOW".equals(riskLevel)) {
                qw.ge("total_score", 80);
            } else if ("MEDIUM".equals(riskLevel)) {
                qw.ge("total_score", 60).lt("total_score", 80);
            } else if ("HIGH".equals(riskLevel)) {
                qw.lt("total_score", 60);
            }
        }
        qw.orderByDesc("submit_time");
        Page<RiskAssessment> result = riskAssessmentMapper.selectPage(pageInfo, qw);
        List<Map<String, Object>> list = result.getRecords().stream().map(this::toScoreItem).toList();
        return AdminPageHelper.toListPage(list, result.getTotal());
    }

    @Override
    public Map<String, Object> getUserRiskDetail(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        RiskAssessment assessment = resolveUserAssessment(userId);
        if (assessment == null || assessment.getApplyId() == null) {
            Map<String, Object> data = new HashMap<>();
            data.put("userId", userId);
            data.put("userName", user.getRealName());
            data.put("hasRiskAssessment", false);
            appendExternalFeatures(data, user);
            return data;
        }

        Map<String, Object> data = new HashMap<>(adminRiskQueryService.getRiskReport(assessment.getApplyId()));
        data.put("userId", userId);
        data.put("userName", user.getRealName());
        appendExternalFeatures(data, user);
        data.put("reportDisplay", adminReportDisplayBuilder.build(data));
        return data;
    }

    private RiskAssessment resolveUserAssessment(Long userId) {
        RiskAssessment finalAssessment = riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>()
                        .eq("user_id", userId)
                        .eq("is_final", true)
                        .orderByDesc("submit_time")
                        .last("LIMIT 1"));
        if (finalAssessment != null) {
            return finalAssessment;
        }
        return riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>()
                        .eq("user_id", userId)
                        .orderByDesc("submit_time")
                        .last("LIMIT 1"));
    }

    private void appendExternalFeatures(Map<String, Object> data, User user) {
        if (user.getIdCard() == null) {
            return;
        }
        UserExternalFeatures ext = userExternalFeaturesMapper.selectByIdCard(user.getIdCard());
        if (ext == null) {
            return;
        }
        Map<String, Object> extSummary = new HashMap<>();
        extSummary.put("activeLoansCount", ext.getActiveLoansCount());
        extSummary.put("creditBureauMon", ext.getCreditBureauMon());
        extSummary.put("amtIncomeTotal", ext.getAmtIncomeTotal());
        extSummary.put("prevRefusedCount", ext.getPrevRefusedCount());
        data.put("externalFeaturesDb", extSummary);
        Object existing = data.get("externalFeatures");
        if (existing instanceof Map<?, ?> existingMap) {
            Map<String, Object> merged = new HashMap<>();
            existingMap.forEach((k, v) -> merged.put(String.valueOf(k), v));
            merged.putAll(extSummary);
            data.put("externalFeatures", merged);
        } else {
            data.put("externalFeatures", extSummary);
        }
    }

    @Override
    public Map<String, Object> listAntiFraud(Integer page, Integer size, String status) {
        List<RiskAssessment> candidates = riskAssessmentMapper.selectList(
                new QueryWrapper<RiskAssessment>()
                        .and(w -> w.like("audit_remark", "黑名单")
                                .or().like("audit_remark", "收入异常")
                                .or().like("audit_remark", "多头")
                                .or().eq("status", "MANUAL_REVIEW"))
                        .orderByDesc("submit_time"));
        List<Map<String, Object>> all = new ArrayList<>();
        for (RiskAssessment a : candidates) {
            String id = a.getApplyId();
            Map<String, Object> handle = antiFraudHandleStore.getOrDefault(id, Map.of("status", "PENDING"));
            if (status != null && !status.isBlank() && !status.equals(handle.get("status"))) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", id);
            item.put("userId", a.getUserId());
            item.put("userName", a.getName());
            item.put("alertType", detectAlertType(a.getAuditRemark()));
            item.put("description", a.getAuditRemark());
            item.put("riskLevel", a.getTotalScore() != null && a.getTotalScore() < 60 ? "HIGH" : "MEDIUM");
            item.put("status", handle.get("status"));
            item.put("createTime", AdminDateHelper.formatDateTime(a.getSubmitTime()));
            all.add(item);
        }
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        List<Map<String, Object>> slice = from >= all.size() ? List.of() : all.subList(from, to);
        return AdminPageHelper.toListPage(slice, all.size());
    }

    @Override
    public Map<String, Object> handleAntiFraud(String id, AntiFraudHandleRequest request, Long adminId) {
        Map<String, Object> record = new HashMap<>();
        record.put("status", mapActionToStatus(request.getAction()));
        record.put("remark", request.getRemark());
        record.put("handlerId", adminId);
        record.put("handleTime", AdminDateHelper.formatDateTime(new java.util.Date()));
        antiFraudHandleStore.put(id, record);
        return Map.of("id", id, "status", record.get("status"));
    }

    @Override
    public Map<String, Object> listMultiLoan(Integer page, Integer size, Integer minActiveLoans) {
        int threshold = minActiveLoans != null ? minActiveLoans : 2;
        List<User> users = userMapper.selectList(null);
        List<Map<String, Object>> matched = new ArrayList<>();
        for (User user : users) {
            if (user.getIdCard() == null) {
                continue;
            }
            UserExternalFeatures ext = userExternalFeaturesMapper.selectByIdCard(user.getIdCard());
            if (ext != null && ext.getActiveLoansCount() != null && ext.getActiveLoansCount() >= threshold) {
                Map<String, Object> item = new HashMap<>();
                item.put("userId", user.getId());
                item.put("userName", user.getRealName());
                item.put("idCard", user.getIdCard());
                item.put("activeLoansCount", ext.getActiveLoansCount());
                item.put("creditBureauMon", ext.getCreditBureauMon());
                item.put("riskLevel", ext.getActiveLoansCount() >= 5 ? "HIGH" : "MEDIUM");
                matched.add(item);
            }
        }
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(matched.size(), from + size);
        return AdminPageHelper.toListPage(from >= matched.size() ? List.of() : matched.subList(from, to), matched.size());
    }

    @Override
    public Map<String, Object> listCreditReports(Integer page, Integer size, String status) {
        Page<RiskAssessment> pageInfo = new Page<>(page, size);
        QueryWrapper<RiskAssessment> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq("status", status);
        }
        qw.orderByDesc("submit_time");
        Page<RiskAssessment> result = riskAssessmentMapper.selectPage(pageInfo, qw);
        List<Map<String, Object>> list = result.getRecords().stream().map(a -> {
            Map<String, Object> item = new HashMap<>();
            item.put("applyId", a.getApplyId());
            item.put("userId", a.getUserId());
            item.put("userName", a.getName());
            item.put("totalScore", a.getTotalScore());
            item.put("status", a.getStatus());
            item.put("sysDecision", a.getSysDecision());
            item.put("submitTime", AdminDateHelper.formatDateTime(a.getSubmitTime()));
            return item;
        }).toList();
        return AdminPageHelper.toListPage(list, result.getTotal());
    }

    @Override
    public Map<String, Object> getCreditReportDetail(Long userId) {
        return getUserRiskDetail(userId);
    }

    @Override
    public Map<String, Object> exportRiskData(String status) {
        QueryWrapper<RiskAssessment> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq("status", status);
        }
        List<RiskAssessment> list = riskAssessmentMapper.selectList(qw);
        List<String> headers = List.of("applyId", "userId", "name", "totalScore", "status", "submitTime");
        List<List<String>> rows = list.stream().map(a -> List.of(
                a.getApplyId(),
                String.valueOf(a.getUserId()),
                a.getName(),
                String.valueOf(a.getTotalScore()),
                a.getStatus(),
                AdminDateHelper.formatDateTime(a.getSubmitTime())
        )).toList();
        String path = adminExportHelper.writeCsv("risk-export", headers, rows);
        return adminExportHelper.downloadMeta(path);
    }

    private Map<String, Object> toScoreItem(RiskAssessment a) {
        Map<String, Object> item = new HashMap<>();
        item.put("userId", a.getUserId());
        item.put("userName", a.getName());
        item.put("applyId", a.getApplyId());
        item.put("totalScore", a.getTotalScore());
        item.put("creditLimit", a.getCreditLimit());
        item.put("status", a.getStatus());
        item.put("riskLevel", scoreLevel(a.getTotalScore()));
        item.put("submitTime", AdminDateHelper.formatDateTime(a.getSubmitTime()));
        return item;
    }

    private String scoreLevel(Integer score) {
        if (score == null) {
            return "UNKNOWN";
        }
        if (score >= 80) {
            return "LOW";
        }
        if (score >= 60) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private String detectAlertType(String remark) {
        if (remark == null) {
            return "MANUAL_REVIEW";
        }
        if (remark.contains("黑名单")) {
            return "BLACKLIST";
        }
        if (remark.contains("收入")) {
            return "INCOME_ANOMALY";
        }
        if (remark.contains("多头")) {
            return "MULTI_LOAN";
        }
        return "MANUAL_REVIEW";
    }

    private String mapActionToStatus(String action) {
        if (action == null) {
            return "HANDLED";
        }
        return switch (action) {
            case "DISMISS" -> "DISMISSED";
            case "ESCALATE" -> "ESCALATED";
            default -> "CONFIRMED";
        };
    }
}
