package org.example.risklendpro.risk.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.common.cache.RedisCacheUtil;
import org.example.risklendpro.common.mail.EmailUtil;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.risk.assessment.RiskAssessmentRequest;
import org.example.risklendpro.risk.assessment.StatusEnum;
import org.example.risklendpro.risk.entity.RiskAssessment;
import org.example.risklendpro.risk.entity.UserBCardLog;
import org.example.risklendpro.risk.mapper.RiskAssessmentMapper;
import org.example.risklendpro.risk.mapper.UserBCardLogMapper;
import org.example.risklendpro.risk.score.BehaviorScoreService;
import org.example.risklendpro.risk.score.CreditScoreEngine;
import org.example.risklendpro.risk.supplement.SupplementMaterialService;
import org.example.risklendpro.user.entity.User;
import org.example.risklendpro.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminRiskQueryServiceImpl implements AdminRiskQueryService {

    private static final Logger log = LoggerFactory.getLogger(AdminRiskQueryServiceImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RiskAssessmentMapper riskAssessmentMapper;
    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private EmailUtil emailUtil;
    @Autowired
    private RedisCacheUtil redisCacheUtil;
    @Autowired
    private SupplementMaterialService supplementMaterialService;
    @Autowired
    private CreditScoreEngine creditScoreEngine;
    @Autowired
    private AdminReportDisplayBuilder adminReportDisplayBuilder;
    @Autowired
    private BehaviorScoreService behaviorScoreService;
    @Autowired
    private UserBCardLogMapper userBCardLogMapper;
    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;
    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    public Page<Map<String, Object>> getRiskList(Integer page, Integer size, String status) {
        Page<RiskAssessment> pageInfo = new Page<>(page, size);
        QueryWrapper<RiskAssessment> queryWrapper = new QueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            queryWrapper.eq("status", status);
        }
        queryWrapper.orderByDesc("submit_time");

        Page<RiskAssessment> resultPage = riskAssessmentMapper.selectPage(pageInfo, queryWrapper);
        Page<Map<String, Object>> responsePage = new Page<>(page, size);
        responsePage.setTotal(resultPage.getTotal());

        List<Map<String, Object>> records = new ArrayList<>();
        for (RiskAssessment assessment : resultPage.getRecords()) {
            Map<String, Object> record = new HashMap<>();
            record.put("applyId", assessment.getApplyId());
            record.put("userName", assessment.getName());
            record.put("phone", assessment.getPhone().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
            record.put("idCard", assessment.getIdCard().replaceAll("(\\d{3})\\d{9}(\\d{4})", "$1*********$2"));
            record.put("totalScore", assessment.getTotalScore());
            record.put("sysDecision", assessment.getSysDecision());
            record.put("status", assessment.getStatus());
            record.put("applyTime", assessment.getSubmitTime());
            record.put("auditRemark", assessment.getAuditRemark());
            record.put("riskTags", parseRiskTags(assessment.getAuditRemark()));
            records.add(record);
        }
        responsePage.setRecords(records);
        return responsePage;
    }

    private List<String> parseRiskTags(String auditRemark) {
        List<String> tags = new ArrayList<>();
        if (auditRemark == null || auditRemark.isEmpty()) {
            return tags;
        }
        
        if (auditRemark.contains("BLACKLIST_MATCH_LEVEL_3")) {
            tags.add("三级黑名单命中");
        } else if (auditRemark.contains("BLACKLIST_MATCH_LEVEL_2")) {
            tags.add("姓名+地域命中黑名单");
        } else if (auditRemark.contains("BLACKLIST_NAME_ONLY")) {
            tags.add("仅姓名命中黑名单");
        }
        
        if (auditRemark.contains("DATA_VERIFICATION_FAILED")) {
            tags.add("收入验真失败");
        } else if (auditRemark.contains("INCOME_OUTLIER")) {
            tags.add("收入异常(需人工复核)");
        } else if (auditRemark.contains("INCOME_TOLERANCE")) {
            tags.add("收入偏差(已自动通过)");
        }
        
        if (auditRemark.contains("RULE_SCORE_REJECT")) {
            tags.add("规则命中但分数过低");
        } else if (auditRemark.contains("SCORE_MANUAL_REVIEW")) {
            tags.add("信用分区间需人工审核");
        } else if (auditRemark.contains("SCORE_LOW")) {
            tags.add("信用分过低");
        } else if (auditRemark.contains("SCORE_MEDIUM")) {
            tags.add("信用分中等");
        }
        
        return tags;
    }

    private String inferRejectGate(String auditRemark) {
        if (auditRemark == null || auditRemark.isBlank()) {
            return "UNKNOWN";
        }
        if (auditRemark.contains("BLACKLIST_MATCH_LEVEL_3")) {
            return "BLACKLIST_L3";
        }
        if (auditRemark.contains("DATA_VERIFICATION_FAILED")) {
            return "INCOME_VERIFICATION";
        }
        if (auditRemark.contains("RULE_SCORE_REJECT")) {
            return "RULE_AND_SCORE_LOW";
        }
        if (auditRemark.contains("SCORE_LOW")) {
            return "SCORE_LOW";
        }
        return "UNKNOWN";
    }

    private String extractRemarkSegment(String auditRemark, String prefix) {
        if (auditRemark == null || prefix == null) {
            return null;
        }
        for (String part : auditRemark.split("[,;]")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(prefix)) {
                int idx = trimmed.indexOf(':');
                if (idx >= 0 && idx + 1 < trimmed.length()) {
                    return trimmed.substring(idx + 1).trim();
                }
                return trimmed;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void enrichIncomeVerification(Map<String, Object> report, String auditRemark) {
        if (report.containsKey("incomeVerification")) {
            return;
        }
        if (auditRemark == null) {
            return;
        }
        if (auditRemark.contains("DATA_VERIFICATION_FAILED")) {
            Map<String, Object> incomeVerification = new LinkedHashMap<>();
            incomeVerification.put("passed", false);
            incomeVerification.put("hardReject", true);
            incomeVerification.put("reason", extractRemarkSegment(auditRemark, "DATA_VERIFICATION_FAILED"));
            report.put("incomeVerification", incomeVerification);
        } else if (auditRemark.contains("INCOME_OUTLIER")) {
            Map<String, Object> incomeVerification = new LinkedHashMap<>();
            incomeVerification.put("passed", false);
            incomeVerification.put("needManualReview", true);
            incomeVerification.put("reason", extractRemarkSegment(auditRemark, "INCOME_OUTLIER"));
            report.put("incomeVerification", incomeVerification);
        } else if (auditRemark.contains("INCOME_TOLERANCE")) {
            Map<String, Object> incomeVerification = new LinkedHashMap<>();
            incomeVerification.put("passed", true);
            incomeVerification.put("reason", extractRemarkSegment(auditRemark, "INCOME_TOLERANCE"));
            report.put("incomeVerification", incomeVerification);
        }
    }

    @SuppressWarnings("unchecked")
    private void enrichBlacklistCheck(Map<String, Object> report, String auditRemark) {
        if (report.containsKey("blacklistCheck") || auditRemark == null) {
            return;
        }
        Map<String, Object> blacklistCheck = new LinkedHashMap<>();
        if (auditRemark.contains("BLACKLIST_MATCH_LEVEL_3")) {
            blacklistCheck.put("hit", true);
            blacklistCheck.put("level", "LEVEL_3");
            blacklistCheck.put("source", "credit_data_db");
            blacklistCheck.put("reason", "姓名+地域+出生年份命中黑名单（三级）");
        } else if (auditRemark.contains("BLACKLIST_MATCH_LEVEL_2")) {
            blacklistCheck.put("hit", true);
            blacklistCheck.put("level", "LEVEL_2");
            blacklistCheck.put("source", "credit_data_db");
            blacklistCheck.put("reason", "姓名+地域命中黑名单（二级）");
        } else if (auditRemark.contains("BLACKLIST_NAME_ONLY")) {
            blacklistCheck.put("hit", true);
            blacklistCheck.put("level", "NAME_ONLY");
            blacklistCheck.put("source", "credit_data_db");
            blacklistCheck.put("reason", "仅姓名命中黑名单（一级弱匹配）");
        } else {
            return;
        }
        report.put("blacklistCheck", blacklistCheck);
    }

    private String buildOutcomeSummary(Map<String, Object> report, RiskAssessment assessment) {
        String status = assessment.getStatus();
        String rejectGate = report.get("rejectGate") != null ? String.valueOf(report.get("rejectGate")) : null;
        if ("MANUAL_REVIEW".equals(status)) {
            String ruleGate = report.get("ruleGate") != null ? String.valueOf(report.get("ruleGate")) : null;
            if (ruleGate != null && !ruleGate.isBlank() && !"NONE".equals(ruleGate)) {
                return "进入人工复核：命中规则闸（" + ruleGate + "），请结合评分明细与补充材料审批";
            }
            Object score = report.get("totalScore");
            if (score != null) {
                return "进入人工复核：信用分处于人工审核档（642–787），请结合评分明细审批";
            }
            return "进入人工复核，请查看风控原因与外部特征";
        }
        if ("SYSTEM_REJECT".equals(status)) {
            return switch (rejectGate != null ? rejectGate : "") {
                case "BLACKLIST_L3" -> "系统拒绝：三级黑名单命中（姓名+地域+出生年），未进入正式算分流程";
                case "INCOME_VERIFICATION" -> "系统拒绝：自填收入与后台数据偏差过大（>50%），收入验真未通过";
                case "SCORE_LOW" -> "系统拒绝：信用分低于拒绝线（642），评分卡自动拒绝";
                case "RULE_AND_SCORE_LOW" -> "系统拒绝：虽命中规则闸，但信用分低于拒绝线，分数闸优先拒绝";
                default -> "系统拒绝，请查看 auditRemark 与风控标签";
            };
        }
        if ("FINAL_PASS".equals(status)) {
            return "评估已通过";
        }
        return null;
    }

    private void enrichReportFromAssessment(Map<String, Object> report, RiskAssessment assessment) {
        report.putIfAbsent("finalStatus", assessment.getStatus());
        report.putIfAbsent("auditRemark", assessment.getAuditRemark());
        if (!report.containsKey("riskTags")) {
            report.put("riskTags", parseRiskTags(assessment.getAuditRemark()));
        } else {
            Object tagsObj = report.get("riskTags");
            if (tagsObj instanceof List && ((List<?>) tagsObj).isEmpty()) {
                report.put("riskTags", parseRiskTags(assessment.getAuditRemark()));
            }
        }
        if (!report.containsKey("rejectGate") && StatusEnum.SYSTEM_REJECT.getValue().equals(assessment.getStatus())) {
            report.put("rejectGate", inferRejectGate(assessment.getAuditRemark()));
        }
        enrichIncomeVerification(report, assessment.getAuditRemark());
        enrichBlacklistCheck(report, assessment.getAuditRemark());
        String summary = buildOutcomeSummary(report, assessment);
        if (summary != null) {
            report.put("outcomeSummary", summary);
        }
    }

    private String resolveScoreZone(int totalScore) {
        if (totalScore < 642) {
            return "REJECT";
        }
        if (totalScore < 788) {
            return "MANUAL";
        }
        return "APPROVE";
    }

    @Override
    public Map<String, Object> getRiskReport(String applyId) {
        String cacheKey = RedisCacheUtil.getRiskReportKey(applyId);
        Map<String, Object> report = new HashMap<>();
        boolean cacheHit = redisCacheUtil.exists(cacheKey);
        report.put("reportCacheHit", cacheHit);
        if (cacheHit) {
            Map<String, Object> cachedReport = redisCacheUtil.get(cacheKey, Map.class);
            if (cachedReport != null) {
                report.putAll(cachedReport);
            }
        }
        RiskAssessment assessment = riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>().eq("apply_id", applyId));
        if (assessment != null) {
            if (!report.containsKey("totalScore") && assessment.getTotalScore() != null) {
                report.put("totalScore", assessment.getTotalScore());
            }
            if (assessment.getSysDecision() != null) {
                report.putIfAbsent("sysDecision", assessment.getSysDecision());
                report.putIfAbsent("systemDecision", assessment.getSysDecision());
            }
            if (!report.containsKey("scoreThresholds")) {
                Map<String, Object> thresholds = new LinkedHashMap<>();
                thresholds.put("rejectBelow", 642);
                thresholds.put("approveFrom", 788);
                thresholds.put("min", 350);
                thresholds.put("max", 950);
                report.put("scoreThresholds", thresholds);
            }
            if (assessment.getTotalScore() != null && !report.containsKey("scoreZone")) {
                report.put("scoreZone", resolveScoreZone(assessment.getTotalScore()));
            }
            String ruleTrigger = supplementMaterialService.resolveRuleTrigger(assessment);
            if (!"NONE".equals(ruleTrigger)) {
                report.putIfAbsent("ruleGate", ruleTrigger);
            }
            report.put("supplementStatus", assessment.getSupplementStatus());
            report.put("supplementRequirements",
                    supplementMaterialService.parseRequirements(assessment.getSupplementRequirements()));
            report.put("supplementMaterials", supplementMaterialService.listByApplyIdForReport(applyId));
            enrichReportFromAssessment(report, assessment);
            tryRecomputeScoreDetails(report, assessment);
        }
        if (report.containsKey("cachedAt")) {
            report.put("reportCachedAt", report.get("cachedAt"));
        } else {
            report.put("reportCachedAt", null);
        }
        report.put("reportDisplay", adminReportDisplayBuilder.build(report));
        report.put("applyId", applyId);
        return report;
    }

    private RiskAssessmentRequest toAssessmentRequest(RiskAssessment assessment) {
        RiskAssessmentRequest request = new RiskAssessmentRequest();
        request.setIdCard(assessment.getIdCard());
        request.setName(assessment.getName());
        request.setPhone(assessment.getPhone());
        request.setEmail(assessment.getEmail());
        request.setGender(assessment.getGender());
        if (assessment.getBirthday() != null) {
            request.setBirthday(assessment.getBirthday().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate().toString());
        }
        request.setEducation(assessment.getEducation());
        request.setMarriage(assessment.getMarriage());
        request.setJobType(assessment.getJobType());
        request.setMonthlyIncome(assessment.getMonthlyIncome());
        request.setHasHouse(assessment.getHasHouse());
        request.setHasCar(assessment.getHasCar());
        request.setContactPhone(assessment.getContactPhone());
        return request;
    }

    private boolean isScoreDetailsEmpty(Map<String, Object> report) {
        Object details = report.get("scoreDetails");
        return !(details instanceof List) || ((List<?>) details).isEmpty();
    }

    private void tryRecomputeScoreDetails(Map<String, Object> report, RiskAssessment assessment) {
        if (!isScoreDetailsEmpty(report)) {
            return;
        }
        if (assessment.getIdCard() == null || assessment.getIdCard().isBlank()) {
            return;
        }
        if (!creditScoreEngine.hasExternalFeaturesForScoring(assessment.getIdCard())) {
            return;
        }
        String rejectGate = report.get("rejectGate") != null
                ? String.valueOf(report.get("rejectGate"))
                : inferRejectGate(assessment.getAuditRemark());
        if ("BLACKLIST_L3".equals(rejectGate) && assessment.getTotalScore() == null) {
            return;
        }
        if ("INCOME_VERIFICATION".equals(rejectGate) && Boolean.FALSE.equals(report.get("scored"))
                && assessment.getTotalScore() == null) {
            return;
        }
        try {
            CreditScoreEngine.ScoreDetailReport detailReport =
                    creditScoreEngine.getScoreDetailReport(toAssessmentRequest(assessment));
            report.put("scoreDetails", buildScoreDetailsList(detailReport));
            report.put("scoreRecomputed", true);
            report.put("scored", true);
            if (!report.containsKey("totalScore") || report.get("totalScore") == null) {
                report.put("totalScore", (int) Math.round(detailReport.getTotalScore()));
            }
            report.putIfAbsent("systemDecision", detailReport.getDecision());
            log.info("Recomputed scoreDetails on report read, applyId={}, cacheHit={}",
                    assessment.getApplyId(), report.get("reportCacheHit"));
        } catch (Exception e) {
            log.warn("Recompute scoreDetails failed, applyId={}: {}", assessment.getApplyId(), e.getMessage());
        }
    }

    private List<Map<String, Object>> buildScoreDetailsList(CreditScoreEngine.ScoreDetailReport detailReport) {
        List<Map<String, Object>> scoreDetails = new ArrayList<>();
        if (detailReport.getScoreDetails() == null) {
            return scoreDetails;
        }
        for (CreditScoreEngine.ScoreContribution contribution : detailReport.getScoreDetails()) {
            Map<String, Object> detailMap = new LinkedHashMap<>();
            detailMap.put("feature", contribution.getFeature());
            try {
                detailMap.put("value", Double.parseDouble(contribution.getValue()));
            } catch (NumberFormatException e) {
                detailMap.put("value", contribution.getValue());
            }
            detailMap.put("weight", contribution.getWeight());
            detailMap.put("contribution", contribution.getContribution());
            detailMap.put("description", contribution.getDescription());
            AdminReportDisplayBuilder.enrichDetailFields(detailMap);
            scoreDetails.add(detailMap);
        }
        return scoreDetails;
    }

    @Override
    @Transactional
    public void approveRisk(RiskApproveRequest request) {
        RiskAssessment assessment = riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>().eq("apply_id", request.getApplyId())
        );
        if (assessment == null) {
            throw new RuntimeException("评估申请不存在");
        }

        if ("PASS".equals(request.getAuditResult())) {
            assessment.setStatus("FINAL_PASS");
            assessment.setCreditLimit(request.getCreditLimit());

            // 创建或更新用户额度记录
            createOrUpdateUserCreditLimit(assessment.getUserId(), request.getCreditLimit());
        } else {
            assessment.setStatus("FINAL_REJECT");
        }
        assessment.setIsFinal(true);
        assessment.setAuditRemark(request.getAuditRemark());
        assessment.setApprovalTime(new Date());
        riskAssessmentMapper.updateById(assessment);

        emailUtil.sendRiskAssessmentNotification(
                assessment.getEmail(),
                assessment.getName(),
                "PASS".equals(request.getAuditResult()) ? "评估通过" : "评估拒绝",
                request.getCreditLimit() != null ? request.getCreditLimit().toString() : "0"
        );
        supplementMaterialService.clearSupplementOnFinalApproval(request.getApplyId());
    }

    /**
     * 创建或更新用户额度记录
     */
    private void createOrUpdateUserCreditLimit(Long userId, BigDecimal creditLimit) {
        QueryWrapper<UserCreditLimit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        UserCreditLimit existingLimit = userCreditLimitMapper.selectOne(queryWrapper);

        if (existingLimit == null) {
            // 创建新的额度记录
            UserCreditLimit newLimit = new UserCreditLimit();
            newLimit.setUserId(userId);
            newLimit.setTotalLimit(creditLimit);
            newLimit.setUsedLimit(BigDecimal.ZERO);
            newLimit.setRemainingLimit(creditLimit);
            newLimit.setOverdueAmount(BigDecimal.ZERO);
            newLimit.setHasOverdue(false);
            newLimit.setLastUpdateTime(new Date());
            userCreditLimitMapper.insert(newLimit);
        } else {
            // 更新现有额度记录
            existingLimit.setTotalLimit(creditLimit);
            existingLimit.setRemainingLimit(creditLimit.subtract(existingLimit.getUsedLimit()));
            existingLimit.setLastUpdateTime(new Date());
            userCreditLimitMapper.updateById(existingLimit);
        }
    }

    public List<Map<String, Object>> getBCardMonitor() {
        List<UserCreditLimit> limits = userCreditLimitMapper.selectList(
                new QueryWrapper<UserCreditLimit>().eq("b_card_enabled", true));
        if (limits.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();

        for (UserCreditLimit limit : limits) {
            User user = userMapper.selectById(limit.getUserId());
            if (user == null) {
                continue;
            }

            UserBCardLog latestLog = userBCardLogMapper.selectOne(
                    new QueryWrapper<UserBCardLog>()
                            .eq("user_id", limit.getUserId())
                            .orderByDesc("id")
                            .last("LIMIT 1"));

            RepaymentPlan bestPlan = null;
            RepaymentRecord bestRecord = null;
            Integer daysToDue = null;
            int activePlanCount = 0;
            int bestPriority = Integer.MAX_VALUE;

            List<RepaymentPlan> plans = repaymentPlanMapper.selectList(
                    new QueryWrapper<RepaymentPlan>().eq("user_id", limit.getUserId()));
            for (RepaymentPlan plan : plans) {
                if ("COMPLETED".equals(plan.getStatus())) {
                    continue;
                }
                activePlanCount++;
                RepaymentRecord record = resolveFocusRecord(plan.getPlanId(), plan.getCurrentPeriod());
                if (record == null || record.getDueDate() == null) {
                    continue;
                }
                int d = (int) ChronoUnit.DAYS.between(today, toLocalDate(record.getDueDate()));
                int priority = planUrgencyPriority(plan, record, d);
                if (bestPlan == null
                        || priority < bestPriority
                        || (priority == bestPriority && (daysToDue == null || d < daysToDue))) {
                    bestPriority = priority;
                    daysToDue = d;
                    bestPlan = plan;
                    bestRecord = record;
                }
            }

            Map<String, Object> row = new HashMap<>();
            row.put("userId", limit.getUserId());
            row.put("userName", user.getRealName());
            row.put("phone", maskPhone(user.getPhoneNumber()));
            row.put("idCard", maskIdCard(user.getIdCard()));
            row.put("bScore", limit.getBScore());
            row.put("bScoreUpdatedAt", limit.getBScoreUpdatedAt());
            row.put("totalLimit", limit.getTotalLimit());
            row.put("hasOverdue", Boolean.TRUE.equals(limit.getHasOverdue()));
            row.put("activePlanCount", activePlanCount);

            if (latestLog != null) {
                row.put("baseScore", latestLog.getBaseScore());
                row.put("deltaScore", latestLog.getDeltaScore());
                row.put("liveFeatures", parseLiveFeaturesJson(latestLog.getLiveFeatures()));
            }

            if (bestPlan != null) {
                row.put("planId", bestPlan.getPlanId());
                row.put("planStatus", bestPlan.getStatus());
                row.put("overdueLevel", bestPlan.getOverdueLevel());
                row.put("overdueDays", bestPlan.getOverdueDays());
            }
            if (bestRecord != null) {
                row.put("dueDate", bestRecord.getDueDate());
                row.put("currentPeriod", bestRecord.getPeriod());
                row.put("recordStatus", bestRecord.getStatus());
            }
            row.put("daysToDue", daysToDue);

            String watchLevel = resolveWatchLevel(bestPlan, bestRecord, daysToDue);
            row.put("watchLevel", watchLevel);

            double multiplier = limit.getBScore() != null
                    ? behaviorScoreService.resolveLimitMultiplier(limit.getBScore().doubleValue())
                    : 1.0;
            row.put("limitMultiplier", multiplier);

            rows.add(row);
        }

        rows.sort(Comparator.comparingInt(r -> watchLevelOrder((String) r.get("watchLevel"))));
        return rows;
    }

    @Override
    @Transactional
    public Map<String, Object> recalculateBCard(Long userId) {
        if (userId == null) {
            throw new RuntimeException("userId 不能为空");
        }
        UserCreditLimit limit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId));
        if (limit == null || !Boolean.TRUE.equals(limit.getBCardEnabled())) {
            throw new RuntimeException("用户未启用 B 卡");
        }
        behaviorScoreService.recalculate(userId);
        limit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId));

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("bScore", limit.getBScore());
        result.put("bScoreUpdatedAt", limit.getBScoreUpdatedAt());
        if (limit.getBScore() != null) {
            result.put("limitMultiplier",
                    behaviorScoreService.resolveLimitMultiplier(limit.getBScore().doubleValue()));
        }
        return result;
    }

    private Map<String, Object> parseLiveFeaturesJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析 liveFeatures 失败: {}", raw, e);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("raw", raw);
            return fallback;
        }
    }

    /** 还款计划紧迫度：数值越小越优先展示 */
    private static int planUrgencyPriority(RepaymentPlan plan, RepaymentRecord record, int daysToDue) {
        if (plan != null && "OVERDUE".equals(plan.getStatus())) {
            return 0;
        }
        if (record != null && "OVERDUE".equals(record.getStatus())) {
            return 0;
        }
        if (daysToDue < 0) {
            return 0;
        }
        if (daysToDue == 0) {
            return 1;
        }
        if (daysToDue <= 3) {
            return 2;
        }
        return 3;
    }

    private static String resolveWatchLevel(RepaymentPlan plan, RepaymentRecord record, Integer daysToDue) {
        if (plan != null && "OVERDUE".equals(plan.getStatus())) {
            return "OVERDUE";
        }
        if (record != null && "OVERDUE".equals(record.getStatus())) {
            return "OVERDUE";
        }
        if (daysToDue == null) {
            return "NORMAL";
        }
        if (daysToDue < 0) {
            return "OVERDUE";
        }
        if (daysToDue == 0) {
            return "DUE_TODAY";
        }
        if (daysToDue <= 3) {
            return "DUE_SOON";
        }
        return "NORMAL";
    }

    private static int watchLevelOrder(String level) {
        if (level == null) {
            return 99;
        }
        return switch (level) {
            case "OVERDUE" -> 0;
            case "DUE_TODAY" -> 1;
            case "DUE_SOON" -> 2;
            case "NORMAL" -> 3;
            default -> 99;
        };
    }

    private static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** 监控展示用：当期已还清时推进到首个未还期次，避免误报逾期 */
    private RepaymentRecord resolveFocusRecord(Long planId, Integer currentPeriod) {
        if (planId == null || currentPeriod == null) {
            return null;
        }
        RepaymentRecord current = repaymentRecordMapper.selectOne(
                new QueryWrapper<RepaymentRecord>()
                        .eq("plan_id", planId)
                        .eq("period", currentPeriod));
        if (current != null && !isRepaidRecord(current)) {
            return current;
        }
        List<RepaymentRecord> records = repaymentRecordMapper.selectList(
                new QueryWrapper<RepaymentRecord>()
                        .eq("plan_id", planId)
                        .orderByAsc("period"));
        for (RepaymentRecord record : records) {
            if (!isRepaidRecord(record)) {
                return record;
            }
        }
        return current;
    }

    private static boolean isRepaidRecord(RepaymentRecord record) {
        String status = record.getStatus();
        return "COMPLETED".equals(status) || "PAID".equals(status) || "SETTLED".equals(status);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }

    private static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        return idCard.replaceAll("(\\d{3})\\d{9}(\\d{4})", "$1*********$2");
    }
}
