package org.example.risklendpro.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.entity.*;
import org.example.risklendpro.enums.LoanStatusEnum;
import org.example.risklendpro.enums.StatusEnum;
import org.example.risklendpro.mapper.*;
import org.example.risklendpro.pojo.request.LoanApproveRequest;
import org.example.risklendpro.pojo.request.RiskApproveRequest;
import org.example.risklendpro.pojo.request.RiskAssessmentRequest;
import org.example.risklendpro.pojo.response.LoanApproveResponse;
import org.example.risklendpro.service.AdminService;
import org.example.risklendpro.service.BehaviorScoreService;
import org.example.risklendpro.service.CreditScoreEngine;
import org.example.risklendpro.service.admin.AdminReportDisplayBuilder;
import org.example.risklendpro.service.supplement.SupplementMaterialService;
import org.example.risklendpro.utils.EmailUtil;
import org.example.risklendpro.utils.RedisCacheUtil;
import org.example.risklendpro.utils.RepaymentCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RiskAssessmentMapper riskAssessmentMapper;

    @Autowired
    private LoanMapper loanMapper;

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private VintageDataMapper vintageDataMapper;

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

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

    @Override
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

    @Override
    public Map<String, Object> getVintageData() {
        List<VintageData> vintageDataList = vintageDataMapper.selectList(null);
        if (vintageDataList != null && !vintageDataList.isEmpty()) {
            return buildVintageResponse(vintageDataList);
        }
        return computeVintageFromLoans();
    }

    private Map<String, Object> buildVintageResponse(List<VintageData> vintageDataList) {
        Map<String, Object> result = new HashMap<>();
        List<String> months = vintageDataList.stream()
                .map(VintageData::getMonth)
                .collect(Collectors.toList());
        result.put("months", months);

        List<Map<String, Object>> vintageData = new ArrayList<>();
        for (VintageData data : vintageDataList) {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("month", data.getMonth());
            dataMap.put("disbursedAmount", data.getDisbursedAmount());
            dataMap.put("M1Rate", data.getM1Rate());
            dataMap.put("M2Rate", data.getM2Rate());
            dataMap.put("M3Rate", data.getM3Rate());
            vintageData.add(dataMap);
        }
        result.put("vintageData", vintageData);
        return result;
    }

    private Map<String, Object> computeVintageFromLoans() {
        List<Loan> loans = loanMapper.selectList(new QueryWrapper<Loan>()
                .in("status", LoanStatusEnum.DISBURRSED.getCode(), LoanStatusEnum.REPAID.getCode(),
                        LoanStatusEnum.OVERDUE.getCode())
                .isNotNull("disbursement_time")
                .orderByAsc("disbursement_time"));

        Map<String, List<Loan>> byMonth = loans.stream()
                .collect(Collectors.groupingBy(l -> formatVintageMonth(l.getDisbursementTime())));

        List<String> months = new ArrayList<>(byMonth.keySet());
        Collections.sort(months);

        List<Map<String, Object>> vintageData = new ArrayList<>();
        for (String month : months) {
            List<Loan> cohortLoans = byMonth.get(month);
            BigDecimal disbursedAmount = cohortLoans.stream()
                    .map(l -> l.getAmount() != null ? l.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<Long> loanIds = cohortLoans.stream().map(Loan::getLoanId).toList();
            List<RepaymentPlan> plans = loanIds.isEmpty() ? List.of()
                    : repaymentPlanMapper.selectList(new QueryWrapper<RepaymentPlan>().in("loan_id", loanIds));

            int totalPlans = plans.size();
            long m1Count = plans.stream().filter(p -> containsOverdueLevel(p.getOverdueLevel(), "M1")).count();
            long m2Count = plans.stream().filter(p -> containsOverdueLevel(p.getOverdueLevel(), "M2")).count();
            long m3Count = plans.stream().filter(p -> containsOverdueLevel(p.getOverdueLevel(), "M3")).count();

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("month", month);
            dataMap.put("disbursedAmount", disbursedAmount);
            dataMap.put("M1Rate", totalPlans > 0 ? (double) m1Count / totalPlans : 0.0);
            dataMap.put("M2Rate", totalPlans > 0 ? (double) m2Count / totalPlans : 0.0);
            dataMap.put("M3Rate", totalPlans > 0 ? (double) m3Count / totalPlans : 0.0);
            vintageData.add(dataMap);
        }

        if (vintageData.isEmpty()) {
            log.info("Vintage compute: no disbursed loan cohorts found");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("months", months);
        result.put("vintageData", vintageData);
        return result;
    }

    private String formatVintageMonth(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private boolean containsOverdueLevel(String overdueLevel, String level) {
        return overdueLevel != null && overdueLevel.toUpperCase().contains(level);
    }

    @Override
    public Map<String, Object> getRollRateData() {
        Map<String, Object> result = new HashMap<>();
        result.put("currentStatus", "C");

        Map<String, Object> nextMonthStatus = calculateRollRate();
        result.put("nextMonthStatus", nextMonthStatus);

        return result;
    }

    @Override
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalApplications = riskAssessmentMapper.selectCount(null);
        long pendingReview = riskAssessmentMapper.selectCount(
                new QueryWrapper<RiskAssessment>().eq("status", "MANUAL_REVIEW")
        );

        long totalDisbursedCount = loanMapper.selectCount(
                new QueryWrapper<Loan>().eq("status", LoanStatusEnum.DISBURRSED.getCode())
        );

        long totalOverdueCount = loanMapper.selectCount(
                new QueryWrapper<Loan>().eq("status", LoanStatusEnum.OVERDUE.getCode())
        );

        long approvedToday = countLoansApprovedToday();
        long rejectedToday = countLoansRejectedToday();
        BigDecimal totalDisbursedAmount = calculateTotalDisbursedAmount();
        BigDecimal totalOverdueAmount = calculateTotalOverdueAmount();

        stats.put("totalApplications", totalApplications);
        stats.put("pendingReview", pendingReview);
        stats.put("approvedToday", approvedToday);
        stats.put("rejectedToday", rejectedToday);
        stats.put("totalDisbursed", totalDisbursedAmount);
        stats.put("totalOverdue", totalOverdueAmount);
        stats.put("overdueRate", totalDisbursedCount > 0 ? (double) totalOverdueCount / totalDisbursedCount : 0);

        return stats;
    }

    @Override
    public Page<Map<String, Object>> getLoanPendingList(Integer page, Integer size) {
        Page<Loan> pageInfo = new Page<>(page, size);
        QueryWrapper<Loan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", LoanStatusEnum.PENDING_APPROVAL.getCode());
        queryWrapper.orderByDesc("apply_time");

        Page<Loan> resultPage = loanMapper.selectPage(pageInfo, queryWrapper);
        Page<Map<String, Object>> responsePage = new Page<>(page, size);
        responsePage.setTotal(resultPage.getTotal());

        List<Map<String, Object>> records = new ArrayList<>();
        for (Loan loan : resultPage.getRecords()) {
            Map<String, Object> record = new HashMap<>();
            record.put("loanId", loan.getLoanId());
            record.put("userId", loan.getUserId());

            User user = userMapper.selectById(loan.getUserId());
            if (user != null) {
                record.put("userName", user.getRealName());
                record.put("phone", user.getPhoneNumber().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
                record.put("idCard", user.getIdCard().replaceAll("(\\d{3})\\d{9}(\\d{4})", "$1*********$2"));
            } else {
                record.put("userName", "未知用户");
                record.put("phone", "未知");
                record.put("idCard", "未知");
            }

            UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(
                    new QueryWrapper<UserCreditLimit>().eq("user_id", loan.getUserId())
            );
            if (creditLimit != null) {
                record.put("currentLimit", creditLimit.getTotalLimit());
                record.put("exceedAmount", loan.getAmount().subtract(creditLimit.getTotalLimit()));
            } else {
                record.put("currentLimit", BigDecimal.ZERO);
                record.put("exceedAmount", loan.getAmount());
            }

            record.put("amount", loan.getAmount());
            record.put("termMonths", loan.getTermMonths());
            record.put("repaymentMethod", loan.getRepaymentMethod());
            record.put("applyTime", loan.getApplyTime());
            record.put("status", loan.getStatus());
            records.add(record);
        }
        responsePage.setRecords(records);
        return responsePage;
    }

    @Override
    @Transactional
    public LoanApproveResponse approveLoan(LoanApproveRequest request) {
        Loan loan = loanMapper.selectById(request.getLoanId());
        if (loan == null) {
            throw new RuntimeException("贷款申请不存在");
        }

        User user = userMapper.selectById(loan.getUserId());

        LoanApproveResponse response = new LoanApproveResponse();
        response.setLoanId(loan.getLoanId());
        response.setUserId(loan.getUserId());

        if ("APPROVE".equals(request.getApproveResult())) {
            loan.setStatus(LoanStatusEnum.DISBURRSED.getCode());
            loan.setApproveTime(new Date());
            loan.setDisbursementTime(new Date());
            loan.setAdditionalLimit(request.getAdditionalLimit());

            UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(
                    new QueryWrapper<UserCreditLimit>().eq("user_id", loan.getUserId())
            );

            if (creditLimit != null) {
                BigDecimal newTotalLimit = creditLimit.getTotalLimit().add(request.getAdditionalLimit());
                creditLimit.setTotalLimit(newTotalLimit);
                creditLimit.setUsedLimit(creditLimit.getUsedLimit().add(loan.getAmount()));
                creditLimit.setRemainingLimit(newTotalLimit.subtract(creditLimit.getUsedLimit()));
                creditLimit.setLastUpdateTime(new Date());
                userCreditLimitMapper.updateById(creditLimit);

                response.setOriginalLimit(creditLimit.getTotalLimit().subtract(request.getAdditionalLimit()));
                response.setAdditionalLimit(request.getAdditionalLimit());
                response.setTotalLimit(newTotalLimit);
                response.setActualDisbursedAmount(loan.getAmount());
            }

            loanMapper.updateById(loan);

            generateRepaymentPlan(loan, loan.getRepaymentMethod());

            if (user != null && user.getIdCard() != null) {
                behaviorScoreService.activate(loan.getUserId(), user.getIdCard());
            }

            response.setStatus(LoanStatusEnum.DISBURRSED.getCode());
            response.setEmailSent(true);

            if (user != null) {
                emailUtil.sendLoanSuccessNotification(
                        user.getEmail(),
                        user.getRealName(),
                        loan.getAmount().toString()
                );
            }
        } else {
            loan.setStatus(LoanStatusEnum.REJECTED.getCode());
            loan.setApproveTime(new Date());
            loan.setRejectReason(request.getApproveRemark());

            loanMapper.updateById(loan);

            response.setStatus(LoanStatusEnum.REJECTED.getCode());
            response.setRejectReason(request.getApproveRemark());
            response.setEmailSent(true);

            if (user != null) {
                emailUtil.sendLoanRejectNotification(
                        user.getEmail(),
                        user.getRealName(),
                        loan.getAmount().toString(),
                        request.getApproveRemark()
                );
            }
        }

        response.setApproveTime(new Date());

        return response;
    }

    private Map<String, Object> calculateRollRate() {
        Map<String, Object> rollRate = new HashMap<>();

        long totalActive = repaymentPlanMapper.selectCount(
                new QueryWrapper<RepaymentPlan>().eq("status", "ACTIVE")
        );

        long movedToM1 = repaymentPlanMapper.selectCount(
                new QueryWrapper<RepaymentPlan>().eq("status", "OVERDUE").like("overdue_level", "M1")
        );

        long movedToM2 = repaymentPlanMapper.selectCount(
                new QueryWrapper<RepaymentPlan>().eq("status", "OVERDUE").like("overdue_level", "M2")
        );

        long movedToM3 = repaymentPlanMapper.selectCount(
                new QueryWrapper<RepaymentPlan>().eq("status", "OVERDUE").like("overdue_level", "M3")
        );

        if (totalActive > 0) {
            rollRate.put("C", (double) (totalActive - movedToM1 - movedToM2 - movedToM3) / totalActive);
            rollRate.put("M1", (double) movedToM1 / totalActive);
            rollRate.put("M2", (double) movedToM2 / totalActive);
            rollRate.put("M3", (double) movedToM3 / totalActive);
        } else {
            rollRate.put("C", 1.0);
            rollRate.put("M1", 0.0);
            rollRate.put("M2", 0.0);
            rollRate.put("M3", 0.0);
        }

        return rollRate;
    }

    private long countLoansApprovedToday() {
        LocalDate today = LocalDate.now();
        Date startOfDay = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endOfDay = Date.from(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        QueryWrapper<Loan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", LoanStatusEnum.APPROVED.getCode());
        queryWrapper.between("approve_time", startOfDay, endOfDay);

        return loanMapper.selectCount(queryWrapper);
    }

    private long countLoansRejectedToday() {
        LocalDate today = LocalDate.now();
        Date startOfDay = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endOfDay = Date.from(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        QueryWrapper<Loan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", LoanStatusEnum.REJECTED.getCode());
        queryWrapper.between("approve_time", startOfDay, endOfDay);

        return loanMapper.selectCount(queryWrapper);
    }

    private BigDecimal calculateTotalDisbursedAmount() {
        List<Loan> loans = loanMapper.selectList(
                new QueryWrapper<Loan>().eq("status", LoanStatusEnum.DISBURRSED.getCode())
        );

        return loans.stream()
                .map(Loan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalOverdueAmount() {
        List<Loan> loans = loanMapper.selectList(
                new QueryWrapper<Loan>().eq("status", LoanStatusEnum.OVERDUE.getCode())
        );

        return loans.stream()
                .map(Loan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void generateRepaymentPlan(Loan loan, String repaymentMethod) {
        RepaymentPlan plan = new RepaymentPlan();
        plan.setLoanId(loan.getLoanId());
        plan.setUserId(loan.getUserId());
        plan.setTotalAmount(loan.getAmount());
        plan.setPaidAmount(BigDecimal.ZERO);
        plan.setRemainingAmount(loan.getAmount());
        plan.setTotalPeriods(loan.getTermMonths());
        plan.setCurrentPeriod(1);
        plan.setStatus("ACTIVE");
        plan.setCreateTime(new Date());
        plan.setUpdateTime(new Date());

        repaymentPlanMapper.insert(plan);

        List<RepaymentCalculator.RepaymentDetail> details;
        switch (repaymentMethod) {
            case "等额本息":
                details = RepaymentCalculator.calculateEqualPrincipalAndInterest(
                        loan.getAmount(), loan.getInterestRate(), loan.getTermMonths());
                break;
            case "等额本金":
                details = RepaymentCalculator.calculateEqualPrincipal(
                        loan.getAmount(), loan.getInterestRate(), loan.getTermMonths());
                break;
            case "先息后本":
                details = RepaymentCalculator.calculateInterestFirst(
                        loan.getAmount(), loan.getInterestRate(), loan.getTermMonths());
                break;
            default:
                throw new RuntimeException("不支持的还款方式: " + repaymentMethod);
        }

        Date now = new Date();
        for (RepaymentCalculator.RepaymentDetail detail : details) {
            RepaymentRecord record = new RepaymentRecord();
            record.setPlanId(plan.getPlanId());
            record.setLoanId(loan.getLoanId());
            record.setPeriod(detail.getPeriod());
            record.setPrincipal(detail.getPrincipal());
            record.setInterest(detail.getInterest());
            record.setAmount(detail.getAmount());
            record.setActualAmount(BigDecimal.ZERO);

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(now);
            calendar.add(Calendar.MONTH, detail.getPeriod());
            record.setDueDate(calendar.getTime());

            record.setStatus("PENDING");
            record.setCreateTime(now);

            repaymentRecordMapper.insert(record);
        }
    }

    @Override
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
