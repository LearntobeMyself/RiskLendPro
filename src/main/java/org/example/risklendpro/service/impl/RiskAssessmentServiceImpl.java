package org.example.risklendpro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.entity.RiskAssessment;
import org.example.risklendpro.entity.User;
import org.example.risklendpro.entity.UserCreditLimit;
import org.example.risklendpro.entity.credit.ScoringRules;
import org.example.risklendpro.entity.credit.UserExternalFeatures;
import org.example.risklendpro.enums.StatusEnum;
import org.example.risklendpro.mapper.RiskAssessmentMapper;
import org.example.risklendpro.mapper.UserCreditLimitMapper;
import org.example.risklendpro.mapper.UserMapper;
import org.example.risklendpro.mapper.credit.ScoringRulesMapper;
import org.example.risklendpro.mapper.credit.UserExternalFeaturesMapper;
import org.example.risklendpro.pojo.request.RiskAssessmentRequest;
import org.example.risklendpro.pojo.response.RiskAssessmentResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentStatusResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentResultResponse;
import org.example.risklendpro.service.CreditScoreEngine;
import org.example.risklendpro.service.RiskAssessmentService;
import org.example.risklendpro.utils.EmailUtil;
import org.example.risklendpro.utils.RedisCacheUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RiskAssessmentServiceImpl implements RiskAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(RiskAssessmentServiceImpl.class);

    @Autowired
    private RiskAssessmentMapper riskAssessmentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserExternalFeaturesMapper userExternalFeaturesMapper;

    @Autowired
    private ScoringRulesMapper scoringRulesMapper;

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private CreditScoreEngine creditScoreEngine;

    @Autowired
    private EmailUtil emailUtil;

    @Autowired
    private RedisCacheUtil redisCacheUtil;

    @Override
    @Transactional
    public RiskAssessmentResponse submit(Long userId, RiskAssessmentRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (!user.getIdCard().equals(request.getIdCard())) {
            throw new RuntimeException("身份信息不一致，请使用本人身份信息申请");
        }

        int age = calculateAge(request.getBirthday());
        if (age < 18) {
            throw new RuntimeException("年龄不足无法提供服务");
        }

        String applyId = generateApplyId();

        RiskAssessment riskAssessment = new RiskAssessment();
        riskAssessment.setApplyId(applyId);
        riskAssessment.setUserId(userId);
        riskAssessment.setIdCard(request.getIdCard());
        riskAssessment.setName(request.getName());
        riskAssessment.setPhone(request.getPhone());
        riskAssessment.setEmail(request.getEmail());
        riskAssessment.setGender(request.getGender());
        riskAssessment.setBirthday(java.sql.Date.valueOf(request.getBirthday()));
        riskAssessment.setEducation(request.getEducation());
        riskAssessment.setMarriage(request.getMarriage());
        riskAssessment.setJobType(request.getJobType());
        riskAssessment.setMonthlyIncome(request.getMonthlyIncome());
        riskAssessment.setHasHouse(request.getHasHouse());
        riskAssessment.setHasCar(request.getHasCar());
        riskAssessment.setContactPhone(request.getContactPhone());
        riskAssessment.setStatus(StatusEnum.WAITING.getValue());
        riskAssessment.setSubmitTime(new Date());
        riskAssessment.setIsFinal(false);

        CreditScoreEngine.BlacklistMatchResult blacklistResult = creditScoreEngine.checkBlacklist(request.getName(), request.getIdCard());
        
        List<String> riskTags = new ArrayList<>();
        
        if (blacklistResult.isReject()) {
            riskAssessment.setStatus(StatusEnum.SYSTEM_REJECT.getValue());
            riskAssessment.setIsFinal(true);
            riskAssessment.setAuditRemark("BLACKLIST_MATCH_LEVEL_3: 姓名+地域+出生年份命中黑名单");
            riskAssessmentMapper.insert(riskAssessment);
            
            user.setAssessmentStatus(StatusEnum.SYSTEM_REJECT.getValue());
            userMapper.updateById(user);
            
            RiskAssessmentResponse response = new RiskAssessmentResponse();
            response.setApplyId(applyId);
            response.setSubmitTime(new Date());
            response.setStatus(StatusEnum.SYSTEM_REJECT.getValue());
            return response;
        }
        
        boolean forceManualReviewByRule = false;

        if (blacklistResult.isNeedManualReview()) {
            forceManualReviewByRule = true;
            riskAssessment.setAuditRemark("BLACKLIST_MATCH_LEVEL_2: 姓名+地域命中黑名单");
            riskTags.add("姓名+地域命中黑名单");
        }

        if (blacklistResult.getMatchLevel() == CreditScoreEngine.MatchLevel.NAME_ONLY) {
            riskTags.add("仅姓名命中黑名单");
            appendAuditRemark(riskAssessment, "BLACKLIST_NAME_ONLY: 仅姓名命中黑名单");
        }

        UserExternalFeatures externalFeatures = resolveExternalFeatures(request.getIdCard());
        DataVerificationResult verificationResult = verifyUserData(request, externalFeatures);
        
        if (verificationResult.isReject()) {
            riskAssessment.setStatus(StatusEnum.SYSTEM_REJECT.getValue());
            riskAssessment.setIsFinal(true);
            riskAssessment.setAuditRemark("DATA_VERIFICATION_FAILED: " + verificationResult.getReason());
            riskAssessmentMapper.insert(riskAssessment);
            
            user.setAssessmentStatus(StatusEnum.SYSTEM_REJECT.getValue());
            userMapper.updateById(user);
            
            RiskAssessmentResponse response = new RiskAssessmentResponse();
            response.setApplyId(applyId);
            response.setSubmitTime(new Date());
            response.setStatus(StatusEnum.SYSTEM_REJECT.getValue());
            return response;
        }
        
        if (verificationResult.isNeedManualReview()) {
            forceManualReviewByRule = true;
            riskAssessment.setAuditRemark("INCOME_OUTLIER: " + verificationResult.getReason());
            riskTags.add("收入异常(需人工复核)");
        }

        if (verificationResult.getReason() != null && !verificationResult.isNeedManualReview()) {
            riskTags.add("收入偏差(已自动通过)");
            appendAuditRemark(riskAssessment, "INCOME_TOLERANCE: " + verificationResult.getReason());
        }

        RiskAssessment existingAssessment = riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>()
                        .eq("user_id", userId)
                        .orderByDesc("submit_time")
                        .last("LIMIT 1")
        );

        if (existingAssessment != null) {
            String status = existingAssessment.getStatus();
            if ("FINAL_PASS".equals(status)) {
                throw new RuntimeException("您的评估申请已通过，请勿重复提交");
            } else if ("WAITING".equals(status) || "MANUAL_REVIEW".equals(status)) {
                throw new RuntimeException("您已有正在处理中的评估申请，请等待处理完成");
            } else {
                long daysSinceLastApply = calculateDaysSinceLastApply(existingAssessment.getSubmitTime());
                if (daysSinceLastApply < 30) {
                    throw new RuntimeException("建议您保持良好的信用记录，" + (30 - daysSinceLastApply) + "天后可尝试再次申请");
                }
            }
        }

        riskAssessmentMapper.insert(riskAssessment);

        executeRiskAssessment(riskAssessment, request, forceManualReviewByRule, riskTags);

        RiskAssessmentResponse response = new RiskAssessmentResponse();
        response.setApplyId(applyId);
        response.setSubmitTime(riskAssessment.getSubmitTime());
        response.setStatus(riskAssessment.getStatus());

        return response;
    }

    private long calculateDaysSinceLastApply(Date lastSubmitTime) {
        if (lastSubmitTime == null) {
            return 30;
        }
        long diff = new Date().getTime() - lastSubmitTime.getTime();
        return diff / (1000 * 60 * 60 * 24);
    }

    @Override
    public RiskAssessmentStatusResponse getStatus(String applyId) {
        RiskAssessment riskAssessment = riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>().eq("apply_id", applyId)
        );
        if (riskAssessment == null) {
            throw new RuntimeException("评估申请不存在");
        }

        RiskAssessmentStatusResponse response = new RiskAssessmentStatusResponse();
        response.setApplyId(applyId);
        response.setStatus(riskAssessment.getStatus());
        response.setStatusTitle(getStatusTitle(riskAssessment.getStatus()));
        response.setFinal(riskAssessment.getIsFinal());

        return response;
    }

    @Override
    @Transactional
    public RiskAssessmentResultResponse getResult(String applyId) {
        RiskAssessment riskAssessment = riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>().eq("apply_id", applyId)
        );
        if (riskAssessment == null) {
            throw new RuntimeException("评估申请不存在");
        }

        if (!riskAssessment.getIsFinal()) {
            throw new RuntimeException("评估尚未完成");
        }

        if (StatusEnum.FINAL_PASS.getValue().equals(riskAssessment.getStatus()) && riskAssessment.getCreditLimit() != null) {
            UserCreditLimit existingLimit = userCreditLimitMapper.selectOne(
                    new QueryWrapper<UserCreditLimit>().eq("user_id", riskAssessment.getUserId())
            );

            if (existingLimit == null) {
                UserCreditLimit creditLimit = new UserCreditLimit();
                creditLimit.setUserId(riskAssessment.getUserId());
                creditLimit.setTotalLimit(riskAssessment.getCreditLimit());
                creditLimit.setUsedLimit(BigDecimal.ZERO);
                creditLimit.setRemainingLimit(riskAssessment.getCreditLimit());
                creditLimit.setOverdueAmount(BigDecimal.ZERO);
                creditLimit.setHasOverdue(false);
                creditLimit.setLastUpdateTime(new Date());
                userCreditLimitMapper.insert(creditLimit);
            } else {
                existingLimit.setTotalLimit(riskAssessment.getCreditLimit());
                existingLimit.setRemainingLimit(riskAssessment.getCreditLimit().subtract(existingLimit.getUsedLimit()));
                existingLimit.setLastUpdateTime(new Date());
                userCreditLimitMapper.updateById(existingLimit);
            }

            User user = userMapper.selectById(riskAssessment.getUserId());
            if (user != null) {
                user.setAssessmentStatus("APPROVED");
                userMapper.updateById(user);
            }
        }

        RiskAssessmentResultResponse response = new RiskAssessmentResultResponse();
        response.setApplyId(applyId);
        response.setTotalScore(riskAssessment.getTotalScore());
        response.setCreditLimit(riskAssessment.getCreditLimit());
        response.setExpireDate(riskAssessment.getExpireDate());
        response.setStatus(riskAssessment.getStatus());
        response.setApprovalTime(riskAssessment.getApprovalTime());

        return response;
    }

    @Transactional
    public void executeRiskAssessment(RiskAssessment riskAssessment, RiskAssessmentRequest request) {
        executeRiskAssessment(riskAssessment, request, false, new ArrayList<>());
    }

    @Transactional
    public void executeRiskAssessment(
            RiskAssessment riskAssessment,
            RiskAssessmentRequest request,
            boolean forceManualReviewByRule,
            List<String> riskTags) {
        try {
            UserExternalFeatures externalRow = resolveExternalFeatures(request.getIdCard());
            if (!creditScoreEngine.hasExternalFeaturesForScoring(request.getIdCard())) {
                handleMissingThirdPartyFeatures(riskAssessment, request, forceManualReviewByRule, riskTags, externalRow);
                return;
            }

            CreditScoreEngine.ScoreDetailReport detailReport = creditScoreEngine.getScoreDetailReport(request);
            double score = detailReport.getTotalScore();
            String decision = detailReport.getDecision();

            double creditLimit = creditScoreEngine.calculateCreditLimitWithFeatures(
                score, request.getMonthlyIncome(), request.getIdCard());

            riskAssessment.setTotalScore((int) Math.round(score));
            riskAssessment.setSysDecision(decision);
            riskAssessment.setApprovalTime(new Date());

            String scoreTag = getScoreTag((int) Math.round(score));
            if (forceManualReviewByRule || "MANUAL_REVIEW".equals(decision)) {
                appendAuditRemark(riskAssessment, scoreTag);
            }

            Map<String, Object> report = buildRiskReport(
                    request, detailReport, creditLimit, riskTags, riskAssessment.getAuditRemark(), externalRow);
            report.put("modelVersion", "v6.0-hc");
            report.put("thirdPartyLinked", true);
            report.put("scored", true);
            warnIfScoreDetailsEmpty(riskAssessment.getApplyId(), report);
            cacheRiskReport(riskAssessment.getApplyId(), report);

            if (forceManualReviewByRule) {
                riskAssessment.setStatus(StatusEnum.MANUAL_REVIEW.getValue());
                riskAssessment.setIsFinal(false);
            } else {
                switch (decision) {
                    case "APPROVE":
                        riskAssessment.setStatus(StatusEnum.FINAL_PASS.getValue());
                        riskAssessment.setIsFinal(true);
                        if (creditLimit > 0) {
                            riskAssessment.setCreditLimit(BigDecimal.valueOf(creditLimit));
                            Calendar calendar = Calendar.getInstance();
                            calendar.add(Calendar.YEAR, 1);
                            riskAssessment.setExpireDate(calendar.getTime());
                        }
                        sendNotification(riskAssessment, "评估通过", String.valueOf((int) creditLimit));
                        break;
                    case "MANUAL_REVIEW":
                        riskAssessment.setStatus(StatusEnum.MANUAL_REVIEW.getValue());
                        riskAssessment.setIsFinal(false);
                        break;
                    case "REJECT":
                        riskAssessment.setStatus(StatusEnum.SYSTEM_REJECT.getValue());
                        riskAssessment.setIsFinal(true);
                        riskAssessment.setCreditLimit(BigDecimal.ZERO);
                        if (riskAssessment.getAuditRemark() == null || riskAssessment.getAuditRemark().isEmpty()) {
                            riskAssessment.setAuditRemark("SCORE_LOW: 信用分过低");
                        }
                        sendNotification(riskAssessment, "评估拒绝", "0");
                        break;
                    default:
                        break;
                }
            }

            riskAssessmentMapper.updateById(riskAssessment);
            syncUserAssessmentStatus(riskAssessment);

        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("THIRD_PARTY_MISSING")) {
                UserExternalFeatures externalRow = resolveExternalFeatures(request.getIdCard());
                handleMissingThirdPartyFeatures(riskAssessment, request, forceManualReviewByRule, riskTags, externalRow);
            } else {
                failRiskAssessment(riskAssessment, e.getMessage());
            }
        } catch (Exception e) {
            failRiskAssessment(riskAssessment, "风控评估异常: " + e.getMessage());
        }
    }

    private void handleMissingThirdPartyFeatures(
            RiskAssessment riskAssessment,
            RiskAssessmentRequest request,
            boolean forceManualReviewByRule,
            List<String> riskTags,
            UserExternalFeatures externalRow) {
        if (riskTags != null) {
            riskTags.add("第三方征信未关联");
        }
        appendAuditRemark(riskAssessment,
                "THIRD_PARTY_MISSING: 未找到或未入库第三方特征，请对 id_card 执行 load_to_mysql.py 后重试");

        riskAssessment.setStatus(StatusEnum.MANUAL_REVIEW.getValue());
        riskAssessment.setIsFinal(false);
        riskAssessment.setSysDecision("MANUAL_REVIEW");
        riskAssessment.setApprovalTime(new Date());

        Map<String, Object> report = buildRiskReportWithoutScore(
                request, riskTags, riskAssessment.getAuditRemark(), externalRow);
        report.put("modelVersion", "v6.0-hc");
        report.put("thirdPartyLinked", false);
        report.put("externalFeaturesMissing", true);
        report.put("scored", false);

        cacheRiskReport(riskAssessment.getApplyId(), report);

        riskAssessmentMapper.updateById(riskAssessment);
        syncUserAssessmentStatus(riskAssessment);
    }

    private void failRiskAssessment(RiskAssessment riskAssessment, String message) {
        riskAssessment.setStatus(StatusEnum.SYSTEM_REJECT.getValue());
        riskAssessment.setIsFinal(true);
        riskAssessment.setAuditRemark(message);
        riskAssessmentMapper.updateById(riskAssessment);
        syncUserAssessmentStatus(riskAssessment);
    }

    private void syncUserAssessmentStatus(RiskAssessment riskAssessment) {
        User user = userMapper.selectById(riskAssessment.getUserId());
        if (user == null) {
            return;
        }
        String status = riskAssessment.getStatus();
        if (StatusEnum.FINAL_PASS.getValue().equals(status)) {
            user.setAssessmentStatus(StatusEnum.FINAL_PASS.getValue());
        } else if (StatusEnum.MANUAL_REVIEW.getValue().equals(status)) {
            user.setAssessmentStatus(StatusEnum.MANUAL_REVIEW.getValue());
        } else if (StatusEnum.SYSTEM_REJECT.getValue().equals(status)) {
            user.setAssessmentStatus(StatusEnum.SYSTEM_REJECT.getValue());
        } else {
            user.setAssessmentStatus(StatusEnum.WAITING.getValue());
        }
        userMapper.updateById(user);
    }

    /** 风控报告写入 Redis，TTL 见 {@link RedisCacheUtil#CACHE_EXPIRE_DAYS}（1 天）。 */
    private void cacheRiskReport(String applyId, Map<String, Object> report) {
        redisCacheUtil.set(RedisCacheUtil.getRiskReportKey(applyId), report);
    }

    @SuppressWarnings("unchecked")
    private void warnIfScoreDetailsEmpty(String applyId, Map<String, Object> report) {
        Object details = report.get("scoreDetails");
        if (!(details instanceof List) || ((List<?>) details).isEmpty()) {
            log.warn("LR scoring finished but Redis report has empty scoreDetails, applyId={}", applyId);
        }
    }

    private void appendAuditRemark(RiskAssessment riskAssessment, String remark) {
        if (remark == null || remark.isEmpty()) {
            return;
        }
        if (riskAssessment.getAuditRemark() == null || riskAssessment.getAuditRemark().isEmpty()) {
            riskAssessment.setAuditRemark(remark);
        } else if (!riskAssessment.getAuditRemark().contains(remark)) {
            riskAssessment.setAuditRemark(riskAssessment.getAuditRemark() + ", " + remark);
        }
    }

    private Map<String, Object> buildRiskReport(RiskAssessmentRequest request,
                                                CreditScoreEngine.ScoreDetailReport detailReport,
                                                double creditLimit,
                                                List<String> riskTags,
                                                String auditRemark,
                                                UserExternalFeatures externalRow) {
        Map<String, Object> report = buildRiskReportBase(request, riskTags, auditRemark, externalRow);

        report.put("totalScore", detailReport.getTotalScore());
        report.put("systemDecision", detailReport.getDecision());
        report.put("suggestedAmount", (int) creditLimit);

        List<Map<String, Object>> scoreDetails = new ArrayList<>();
        for (CreditScoreEngine.ScoreContribution contribution : detailReport.getScoreDetails()) {
            Map<String, Object> detailMap = new HashMap<>();
            detailMap.put("feature", contribution.getFeature());
            try {
                detailMap.put("value", Double.parseDouble(contribution.getValue()));
            } catch (NumberFormatException e) {
                detailMap.put("value", contribution.getValue());
            }
            detailMap.put("weight", contribution.getWeight());
            detailMap.put("contribution", contribution.getContribution());
            detailMap.put("description", contribution.getDescription());
            scoreDetails.add(detailMap);
        }
        report.put("scoreDetails", scoreDetails);
        if (scoreDetails.isEmpty()) {
            log.warn("buildRiskReport: scoreDetails empty for idCard={}", request.getIdCard());
        }

        Map<String, Object> blacklistCheck = new HashMap<>();
        blacklistCheck.put("hit", detailReport.getBlacklistCheck().isHit());
        blacklistCheck.put("source", detailReport.getBlacklistCheck().getSource());
        blacklistCheck.put("reason", detailReport.getBlacklistCheck().getReason());
        report.put("blacklistCheck", blacklistCheck);

        return report;
    }

    private Map<String, Object> buildRiskReportWithoutScore(
            RiskAssessmentRequest request,
            List<String> riskTags,
            String auditRemark,
            UserExternalFeatures externalRow) {
        Map<String, Object> report = buildRiskReportBase(request, riskTags, auditRemark, externalRow);
        report.put("totalScore", null);
        report.put("systemDecision", "MANUAL_REVIEW");
        report.put("suggestedAmount", 0);
        report.put("scoreDetails", new ArrayList<>());
        report.put("scored", false);
        CreditScoreEngine.BlacklistMatchResult blacklistResult =
                creditScoreEngine.checkBlacklist(request.getName(), request.getIdCard());
        Map<String, Object> blacklistCheck = new HashMap<>();
        blacklistCheck.put("hit", blacklistResult.getMatchLevel() != CreditScoreEngine.MatchLevel.NONE);
        blacklistCheck.put("source", "credit_data_db");
        blacklistCheck.put("reason", blacklistResult.getMatchLevel().getDescription());
        report.put("blacklistCheck", blacklistCheck);
        return report;
    }

    private Map<String, Object> buildRiskReportBase(
            RiskAssessmentRequest request,
            List<String> riskTags,
            String auditRemark,
            UserExternalFeatures externalRow) {
        Map<String, Object> report = new HashMap<>();

        Map<String, Object> userDetails = new HashMap<>();
        userDetails.put("name", maskName(request.getName()));
        userDetails.put("idCard", maskIdCard(request.getIdCard()));
        userDetails.put("education", request.getEducation());
        userDetails.put("marriage", request.getMarriage());
        userDetails.put("jobType", request.getJobType());
        userDetails.put("monthlyIncome", request.getMonthlyIncome());
        userDetails.put("hasHouse", request.getHasHouse());
        userDetails.put("hasCar", request.getHasCar());
        userDetails.put("age", calculateAge(request.getBirthday()));
        report.put("userDetails", userDetails);

        report.put("externalFeatures", buildExternalFeaturesMap(externalRow));
        if (riskTags != null && !riskTags.isEmpty()) {
            report.put("riskTags", new ArrayList<>(riskTags));
        }
        if (auditRemark != null && !auditRemark.isEmpty()) {
            report.put("auditRemark", auditRemark);
        }
        return report;
    }

    /** 与接口文档 6.2 externalFeatures 字段对齐（来自 user_external_features） */
    private Map<String, Object> buildExternalFeaturesMap(UserExternalFeatures ext) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (ext == null) {
            m.put("linked", false);
            return m;
        }
        m.put("linked", true);
        m.put("daysBirth", ext.getDaysBirth());
        m.put("daysEmployed", ext.getDaysEmployed());
        m.put("amtIncomeTotal", ext.getAmtIncomeTotal());
        m.put("creditBureauWeek", ext.getCreditBureauWeek());
        m.put("creditBureauMon", ext.getCreditBureauMon());
        m.put("daysLastPhoneChange", ext.getDaysLastPhoneChange());
        m.put("activeLoansCount", ext.getActiveLoansCount());
        m.put("extSource2", ext.getExtSource2());
        m.put("extSource3", ext.getExtSource3());
        m.put("flagOwnCar", ext.getFlagOwnCar());
        m.put("occupationType", ext.getOccupationType());
        m.put("educationType", ext.getEducationType());
        m.put("target", ext.getTarget());
        m.put("prevRefusedCount", ext.getPrevRefusedCount());
        m.put("dataSource", ext.getDataSource());
        if (ext.getUpdatedAt() != null) {
            m.put("updatedAt", ext.getUpdatedAt().toString());
        }
        return m;
    }

    private String maskName(String name) {
        if (name == null || name.length() == 0) {
            return "";
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return idCard;
        }
        return idCard.substring(0, 3) + "**********" + idCard.substring(13);
    }

    private int calculateAge(String birthday) {
        int birthYear = Integer.parseInt(birthday.substring(0, 4));
        int currentYear = java.time.LocalDate.now().getYear();
        return currentYear - birthYear;
    }

    private void sendNotification(RiskAssessment riskAssessment, String status, String creditLimit) {
        User user = userMapper.selectById(riskAssessment.getUserId());
        if (user != null) {
            emailUtil.sendRiskAssessmentNotification(
                    user.getEmail(),
                    user.getRealName(),
                    status,
                    creditLimit
            );
        }
    }

    private String generateApplyId() {
        return "L" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4);
    }

    private String getStatusTitle(String status) {
        return switch (status) {
            case "WAITING" -> "评估中";
            case "SYSTEM_REJECT" -> "系统拒绝";
            case "MANUAL_REVIEW" -> "人工复核中";
            case "FINAL_PASS" -> "已通过";
            case "FINAL_REJECT" -> "已拒绝";
            default -> "未知状态";
        };
    }

    private UserExternalFeatures resolveExternalFeatures(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return null;
        }
        String trimmed = idCard.trim();
        UserExternalFeatures byCard = userExternalFeaturesMapper.selectByIdCard(trimmed);
        if (byCard != null) {
            return byCard;
        }
        try {
            return userExternalFeaturesMapper.selectBySkIdCurr(Long.parseLong(trimmed));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private DataVerificationResult verifyUserData(RiskAssessmentRequest request, UserExternalFeatures externalFeatures) {
        if (externalFeatures == null) {
            return new DataVerificationResult(false, false, null);
        }

        double backendIncome = externalFeatures.getAmtIncomeTotal() != null 
                ? externalFeatures.getAmtIncomeTotal().doubleValue() / 12 
                : 0;

        if (backendIncome > 0) {
            double[] range = getIncomeRange(request.getMonthlyIncome());
            double lowerBound = range[0];
            double upperBound = range[1];
            
            if (backendIncome >= lowerBound && backendIncome <= upperBound) {
                return new DataVerificationResult(false, false, 
                        String.format("收入在自填区间内 (自填: %s, 后台: %.2f)", request.getMonthlyIncome(), backendIncome));
            } else if (backendIncome < lowerBound) {
                double ratio = (lowerBound - backendIncome) / backendIncome;
                if (ratio > 0.5) {
                    return new DataVerificationResult(true, false, 
                            String.format("收入虚报超过50%% (自填区间: %s, 后台: %.2f, 偏差: %.1f%%)", 
                                    request.getMonthlyIncome(), backendIncome, ratio * 100));
                } else if (ratio > 0.15) {
                    return new DataVerificationResult(false, true, 
                            String.format("收入偏差在15%%-50%%之间 (自填区间: %s, 后台: %.2f, 偏差: %.1f%%)", 
                                    request.getMonthlyIncome(), backendIncome, ratio * 100));
                } else {
                    return new DataVerificationResult(false, false, 
                            String.format("收入偏差在15%%以内 (自填区间: %s, 后台: %.2f, 已取后台值)", 
                                    request.getMonthlyIncome(), backendIncome));
                }
            }
        }

        return new DataVerificationResult(false, false, null);
    }

    private double[] getIncomeRange(String monthlyIncome) {
        return switch (monthlyIncome) {
            case "3000以下" -> new double[]{0, 3000};
            case "3000-5000" -> new double[]{3000, 5000};
            case "5000-8000" -> new double[]{5000, 8000};
            case "8000-15000" -> new double[]{8000, 15000};
            case "15000以上" -> new double[]{15000, Double.MAX_VALUE};
            default -> new double[]{0, Double.MAX_VALUE};
        };
    }

    private String getScoreTag(int score) {
        if (score < 580) {
            return "SCORE_LOW: 信用分过低";
        } else if (score >= 580 && score < 720) {
            return "SCORE_MANUAL_REVIEW: 信用分区间需人工审核";
        } else if (score >= 720) {
            return "SCORE_MEDIUM: 信用分中等";
        }
        return "";
    }

    private static class DataVerificationResult {
        private final boolean reject;
        private final boolean needManualReview;
        private final String reason;

        public DataVerificationResult(boolean reject, boolean needManualReview, String reason) {
            this.reject = reject;
            this.needManualReview = needManualReview;
            this.reason = reason;
        }

        public boolean isReject() {
            return reject;
        }

        public boolean isNeedManualReview() {
            return needManualReview;
        }

        public String getReason() {
            return reason;
        }
    }
}
