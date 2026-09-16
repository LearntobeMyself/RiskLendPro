package org.example.risklendpro.risk.assessment;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.api.dto.CreditLimitGrantCommand;
import org.example.risklendpro.api.dto.UserAssessmentStatusCommand;
import org.example.risklendpro.api.dto.UserSummary;
import org.example.risklendpro.risk.client.LoanServiceClient;
import org.example.risklendpro.risk.client.UserServiceClient;
import org.example.risklendpro.risk.entity.RiskAssessment;
import org.example.risklendpro.risk.credit.ScoringRules;
import org.example.risklendpro.risk.credit.UserExternalFeatures;
import org.example.risklendpro.risk.assessment.StatusEnum;
import org.example.risklendpro.risk.mapper.RiskAssessmentMapper;
import org.example.risklendpro.risk.credit.mapper.ScoringRulesMapper;
import org.example.risklendpro.risk.credit.mapper.UserExternalFeaturesMapper;
import org.example.risklendpro.risk.assessment.RiskAssessmentRequest;
import org.example.risklendpro.risk.assessment.RiskAssessmentResponse;
import org.example.risklendpro.risk.assessment.RiskAssessmentStatusResponse;
import org.example.risklendpro.risk.assessment.RiskAssessmentResultResponse;
import org.example.risklendpro.risk.assessment.RiskAssessmentSubmitEligibilityResponse;
import org.example.risklendpro.common.DuplicateApplyException;
import org.example.risklendpro.risk.supplement.SupplementRequirement;
import org.example.risklendpro.risk.score.CreditScoreEngine;
import org.example.risklendpro.risk.assessment.RiskAssessmentService;
import org.example.risklendpro.risk.admin.AdminReportDisplayBuilder;
import org.example.risklendpro.risk.supplement.SupplementMaterialService;
import org.example.risklendpro.common.mail.EmailUtil;
import org.example.risklendpro.common.cache.RedisCacheUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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

    /** 信用分档位（须与 CreditScoreEngine 的 autoApprove/manualReview 阈值保持一致） */
    private static final double AUTO_APPROVE_THRESHOLD = 720;
    private static final double MANUAL_REVIEW_THRESHOLD = 580;

    @Autowired
    private RiskAssessmentMapper riskAssessmentMapper;

    @Autowired
    private UserExternalFeaturesMapper userExternalFeaturesMapper;

    @Autowired
    private ScoringRulesMapper scoringRulesMapper;

    @Autowired
    private CreditScoreEngine creditScoreEngine;

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private LoanServiceClient loanServiceClient;

    @Autowired
    private EmailUtil emailUtil;

    @Autowired
    private RedisCacheUtil redisCacheUtil;

    @Autowired
    private SupplementMaterialService supplementMaterialService;

    @Override
    @Transactional
    public RiskAssessmentResponse submit(Long userId, RiskAssessmentRequest request) {
        UserSummary user = userServiceClient.getUser(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (!user.idCard().equals(request.getIdCard())) {
            throw new RuntimeException("身份信息不一致，请使用本人身份信息申请");
        }

        int age = calculateAge(request.getBirthday());
        if (age < 18) {
            throw new RuntimeException("年龄不足无法提供服务");
        }

        assertCanSubmit(userId);

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

            UserExternalFeatures externalForReport = resolveExternalFeatures(request.getIdCard());
            cacheSystemRejectReport(
                    applyId, request, riskAssessment, riskTags,
                    riskAssessment.getAuditRemark(), externalForReport,
                    "BLACKLIST_L3", blacklistResult, null);
            if (riskAssessment.getTotalScore() != null) {
                riskAssessmentMapper.updateById(riskAssessment);
            }

            userServiceClient.updateAssessmentStatus(
                    new UserAssessmentStatusCommand(userId, StatusEnum.SYSTEM_REJECT.getValue()));

            RiskAssessmentResponse response = new RiskAssessmentResponse();
            response.setApplyId(applyId);
            response.setSubmitTime(new Date());
            response.setStatus(StatusEnum.SYSTEM_REJECT.getValue());
            return response;
        }
        
        boolean forceManualReviewByRule = false;

        if (blacklistResult.isNeedManualReview()) {
            forceManualReviewByRule = true;
            appendAuditRemark(riskAssessment, "BLACKLIST_MATCH_LEVEL_2: 姓名+地域命中黑名单");
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

            cacheSystemRejectReport(
                    applyId, request, riskAssessment, riskTags,
                    riskAssessment.getAuditRemark(), externalFeatures,
                    "INCOME_VERIFICATION", blacklistResult, verificationResult);
            if (riskAssessment.getTotalScore() != null) {
                riskAssessmentMapper.updateById(riskAssessment);
            }

            userServiceClient.updateAssessmentStatus(
                    new UserAssessmentStatusCommand(userId, StatusEnum.SYSTEM_REJECT.getValue()));

            RiskAssessmentResponse response = new RiskAssessmentResponse();
            response.setApplyId(applyId);
            response.setSubmitTime(new Date());
            response.setStatus(StatusEnum.SYSTEM_REJECT.getValue());
            return response;
        }
        
        if (verificationResult.isNeedManualReview()) {
            forceManualReviewByRule = true;
            appendAuditRemark(riskAssessment, "INCOME_OUTLIER: " + verificationResult.getReason());
            riskTags.add("收入异常(需人工复核)");
        }

        if (verificationResult.getReason() != null && !verificationResult.isNeedManualReview()) {
            riskTags.add("收入偏差(已自动通过)");
            appendAuditRemark(riskAssessment, "INCOME_TOLERANCE: " + verificationResult.getReason());
        }

        riskAssessmentMapper.insert(riskAssessment);

        executeRiskAssessment(riskAssessment, request, forceManualReviewByRule, riskTags);

        RiskAssessmentResponse response = new RiskAssessmentResponse();
        response.setApplyId(applyId);
        response.setSubmitTime(riskAssessment.getSubmitTime());
        response.setStatus(riskAssessment.getStatus());

        return response;
    }

    @Override
    public RiskAssessmentSubmitEligibilityResponse getSubmitEligibility(Long userId) {
        SubmitEligibilityResult result = evaluateSubmitEligibility(userId);
        return toEligibilityResponse(result);
    }

    private void assertCanSubmit(Long userId) {
        SubmitEligibilityResult result = evaluateSubmitEligibility(userId);
        if (!result.canSubmit) {
            throw new DuplicateApplyException(
                    result.reason,
                    result.existingApplyId,
                    result.existingStatus,
                    result.retryAfterDays);
        }
    }

    private SubmitEligibilityResult evaluateSubmitEligibility(Long userId) {
        SubmitEligibilityResult result = new SubmitEligibilityResult();
        result.canSubmit = true;
        RiskAssessment existing = findLatestByUserId(userId);
        if (existing == null) {
            return result;
        }
        result.existingApplyId = existing.getApplyId();
        result.existingStatus = existing.getStatus();
        String status = existing.getStatus();
        if ("FINAL_PASS".equals(status)) {
            result.canSubmit = false;
            result.reason = "您的评估申请已通过，请勿重复提交";
            return result;
        }
        if ("WAITING".equals(status) || "MANUAL_REVIEW".equals(status)) {
            result.canSubmit = false;
            result.reason = "您已有正在处理中的评估申请，请等待处理完成后再试";
            return result;
        }
        long daysSinceLastApply = calculateDaysSinceLastApply(existing.getSubmitTime());
        if (daysSinceLastApply < 30) {
            result.canSubmit = false;
            result.retryAfterDays = 30 - daysSinceLastApply;
            result.reason = "建议您保持良好的信用记录，" + result.retryAfterDays + "天后可尝试再次申请";
        }
        return result;
    }

    private RiskAssessmentSubmitEligibilityResponse toEligibilityResponse(SubmitEligibilityResult result) {
        RiskAssessmentSubmitEligibilityResponse response = new RiskAssessmentSubmitEligibilityResponse();
        response.setCanSubmit(result.canSubmit);
        response.setReason(result.reason);
        response.setExistingApplyId(result.existingApplyId);
        response.setExistingStatus(result.existingStatus);
        response.setRetryAfterDays(result.retryAfterDays);
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
    public RiskAssessmentStatusResponse getStatusForUser(Long userId, String applyIdOptional) {
        RiskAssessment riskAssessment = resolveAssessmentForUser(userId, applyIdOptional);
        RiskAssessmentStatusResponse response = new RiskAssessmentStatusResponse();
        response.setApplyId(riskAssessment.getApplyId());
        response.setStatus(riskAssessment.getStatus());
        response.setStatusTitle(getStatusTitle(riskAssessment.getStatus()));
        response.setFinal(riskAssessment.getIsFinal());
        fillSupplementFields(response, riskAssessment);
        return response;
    }

    @Override
    public RiskAssessmentResultResponse getResultForUser(Long userId, String applyIdOptional) {
        // 注意：本方法无本地写操作，且会触发跨服务 Feign 调用，因此不开事务，避免持有 DB 连接跨 HTTP。
        RiskAssessment riskAssessment = resolveAssessmentForUser(userId, applyIdOptional);
        return buildResultResponse(riskAssessment);
    }

    private RiskAssessment resolveAssessmentForUser(Long userId, String applyIdOptional) {
        RiskAssessment riskAssessment;
        if (applyIdOptional != null && !applyIdOptional.isBlank()) {
            riskAssessment = riskAssessmentMapper.selectOne(
                    new QueryWrapper<RiskAssessment>().eq("apply_id", applyIdOptional));
            if (riskAssessment == null) {
                throw new RuntimeException("评估申请不存在");
            }
            if (!userId.equals(riskAssessment.getUserId())) {
                throw new RuntimeException("无权查看该评估申请");
            }
        } else {
            riskAssessment = findLatestByUserId(userId);
            if (riskAssessment == null) {
                throw new RuntimeException("暂无评估申请记录");
            }
        }
        return riskAssessment;
    }

    private RiskAssessment findLatestByUserId(Long userId) {
        return riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>()
                        .eq("user_id", userId)
                        .orderByDesc("submit_time")
                        .last("LIMIT 1"));
    }

    private void fillSupplementFields(RiskAssessmentStatusResponse response, RiskAssessment assessment) {
        String supplementStatus = assessment.getSupplementStatus();
        if (supplementStatus == null || supplementStatus.isBlank()) {
            supplementStatus = SupplementMaterialService.STATUS_NONE;
        }
        response.setSupplementStatus(supplementStatus);
        List<SupplementRequirement> requirements =
                supplementMaterialService.parseRequirements(assessment.getSupplementRequirements());
        response.setRequiredMaterials(requirements);
        response.setTotalScore(assessment.getTotalScore());
        response.setSysDecision(assessment.getSysDecision());
        String ruleTrigger = supplementMaterialService.resolveRuleTrigger(assessment);
        response.setRuleTrigger("NONE".equals(ruleTrigger) ? null : ruleTrigger);
        response.setUploadedMaterialTypes(supplementMaterialService.listUploadedTypes(assessment.getApplyId()));
        if (SupplementMaterialService.STATUS_REQUIRED.equals(supplementStatus)
                || SupplementMaterialService.STATUS_SUBMITTED.equals(supplementStatus)) {
            response.setSupplementDeadline(supplementMaterialService.computeDeadline(requirements));
        }
    }

    private RiskAssessmentResultResponse buildResultResponse(RiskAssessment riskAssessment) {
        if (!riskAssessment.getIsFinal()) {
            throw new RuntimeException("评估尚未完成");
        }

        if (StatusEnum.FINAL_PASS.getValue().equals(riskAssessment.getStatus()) && riskAssessment.getCreditLimit() != null) {
            loanServiceClient.grantCreditLimit(new CreditLimitGrantCommand(
                    riskAssessment.getUserId(),
                    riskAssessment.getApplyId(),
                    riskAssessment.getCreditLimit()));

            userServiceClient.updateAssessmentStatus(
                    new UserAssessmentStatusCommand(riskAssessment.getUserId(), "APPROVED"));
        }

        RiskAssessmentResultResponse response = new RiskAssessmentResultResponse();
        response.setApplyId(riskAssessment.getApplyId());
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
            report.put("modelVersion", resolveModelVersion());
            report.put("thirdPartyLinked", true);
            report.put("scored", true);

            String notificationStatus = null;
            String notificationLimit = null;

            if (forceManualReviewByRule) {
                if ("REJECT".equals(decision)) {
                    riskAssessment.setStatus(StatusEnum.SYSTEM_REJECT.getValue());
                    riskAssessment.setIsFinal(true);
                    riskAssessment.setCreditLimit(BigDecimal.ZERO);
                    appendAuditRemark(riskAssessment, "RULE_SCORE_REJECT: 规则命中但信用分低于拒绝线");
                    report.put("finalStatus", StatusEnum.SYSTEM_REJECT.getValue());
                    report.put("rejectGate", "RULE_AND_SCORE_LOW");
                    String ruleGate = supplementMaterialService.resolveRuleGate(riskTags);
                    if (!"NONE".equals(ruleGate)) {
                        report.put("ruleGate", ruleGate);
                    }
                    report.put("outcomeSummary", "系统拒绝：虽命中规则闸，但信用分低于拒绝线（" + (int) MANUAL_REVIEW_THRESHOLD + "），分数闸优先拒绝");
                    riskAssessment.setSupplementStatus(SupplementMaterialService.STATUS_NONE);
                    riskAssessment.setSupplementRequirements(null);
                    notificationStatus = "评估拒绝";
                    notificationLimit = "0";
                } else {
                    riskAssessment.setStatus(StatusEnum.MANUAL_REVIEW.getValue());
                    riskAssessment.setIsFinal(false);
                    report.put("finalStatus", StatusEnum.MANUAL_REVIEW.getValue());
                    String ruleGate = supplementMaterialService.resolveRuleGate(riskTags);
                    if (!"NONE".equals(ruleGate)) {
                        report.put("ruleGate", ruleGate);
                        report.put("outcomeSummary", "进入人工复核：命中规则闸（" + ruleGate + "），请结合评分明细与补充材料审批");
                    } else {
                        report.put("outcomeSummary", "进入人工复核：请结合评分明细与风控原因审批");
                    }
                }
            } else {
                if ("APPROVE".equals(decision)) {
                    report.put("finalStatus", StatusEnum.FINAL_PASS.getValue());
                } else if ("MANUAL_REVIEW".equals(decision)) {
                    report.put("finalStatus", StatusEnum.MANUAL_REVIEW.getValue());
                    report.put("outcomeSummary", "进入人工复核：信用分处于人工审核档（" + (int) MANUAL_REVIEW_THRESHOLD + "–" + (int) AUTO_APPROVE_THRESHOLD + "），请结合评分明细审批");
                } else if ("REJECT".equals(decision)) {
                    report.put("finalStatus", StatusEnum.SYSTEM_REJECT.getValue());
                    report.put("rejectGate", "SCORE_LOW");
                    report.put("outcomeSummary", "系统拒绝：信用分低于拒绝线（" + (int) MANUAL_REVIEW_THRESHOLD + "），评分卡自动拒绝");
                }
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
                        notificationStatus = "评估通过";
                        notificationLimit = String.valueOf((int) creditLimit);
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
                        notificationStatus = "评估拒绝";
                        notificationLimit = "0";
                        break;
                    default:
                        break;
                }
            }

            if (StatusEnum.MANUAL_REVIEW.getValue().equals(riskAssessment.getStatus())) {
                supplementMaterialService.setupManualReviewSupplement(riskAssessment, riskTags);
            }

            riskAssessmentMapper.updateById(riskAssessment);
            syncUserAssessmentStatus(riskAssessment);

            warnIfScoreDetailsEmpty(riskAssessment.getApplyId(), report);
            safeCacheRiskReport(riskAssessment.getApplyId(), report);

            if (SupplementMaterialService.STATUS_REQUIRED.equals(riskAssessment.getSupplementStatus())) {
                supplementMaterialService.sendSupplementNoticeEmail(
                        riskAssessment,
                        supplementMaterialService.parseRequirements(riskAssessment.getSupplementRequirements()));
            }

            if (notificationStatus != null) {
                safeSendNotification(riskAssessment, notificationStatus, notificationLimit);
            }

            log.debug("Risk assessment completed applyId={}, decision={}, score={}",
                    riskAssessment.getApplyId(), decision, score);

        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("THIRD_PARTY_MISSING")) {
                UserExternalFeatures externalRow = resolveExternalFeatures(request.getIdCard());
                handleMissingThirdPartyFeatures(riskAssessment, request, forceManualReviewByRule, riskTags, externalRow);
            } else {
                failRiskAssessment(riskAssessment, request, e.getMessage());
            }
        } catch (Exception e) {
            failRiskAssessment(riskAssessment, request, "风控评估异常: " + e.getMessage());
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
        report.put("modelVersion", resolveModelVersion());
        report.put("thirdPartyLinked", false);
        report.put("externalFeaturesMissing", true);
        report.put("scored", false);
        report.put("finalStatus", riskAssessment.getStatus());

        supplementMaterialService.setupManualReviewSupplement(riskAssessment, riskTags);

        riskAssessmentMapper.updateById(riskAssessment);
        syncUserAssessmentStatus(riskAssessment);
        safeCacheRiskReport(riskAssessment.getApplyId(), report);

        if (SupplementMaterialService.STATUS_REQUIRED.equals(riskAssessment.getSupplementStatus())) {
            supplementMaterialService.sendSupplementNoticeEmail(
                    riskAssessment,
                    supplementMaterialService.parseRequirements(riskAssessment.getSupplementRequirements()));
        }
    }

    private void failRiskAssessment(RiskAssessment riskAssessment, RiskAssessmentRequest request, String message) {
        riskAssessment.setStatus(StatusEnum.SYSTEM_REJECT.getValue());
        riskAssessment.setIsFinal(true);
        appendAuditRemark(riskAssessment, message);
        UserExternalFeatures externalRow = resolveExternalFeatures(request.getIdCard());
        try {
            cacheSystemRejectReport(
                    riskAssessment.getApplyId(), request, riskAssessment, new ArrayList<>(),
                    riskAssessment.getAuditRemark(), externalRow, "SYSTEM_ERROR", null, null);
        } catch (Exception e) {
            log.warn("failRiskAssessment: redis report failed, applyId={}", riskAssessment.getApplyId(), e);
        }
        try {
            riskAssessmentMapper.updateById(riskAssessment);
            syncUserAssessmentStatus(riskAssessment);
        } catch (Exception e) {
            log.error("failRiskAssessment: db update failed, applyId={}", riskAssessment.getApplyId(), e);
            throw e;
        }
    }

    /**
     * 系统拒绝（黑名单/收入校验/异常）时也写入 Redis，便于管理端查看拒绝原因；
     * 若已关联第三方特征则附带完整评分明细（shadow score）。
     */
    private void cacheSystemRejectReport(
            String applyId,
            RiskAssessmentRequest request,
            RiskAssessment riskAssessment,
            List<String> riskTags,
            String auditRemark,
            UserExternalFeatures externalRow,
            String rejectGate,
            CreditScoreEngine.BlacklistMatchResult blacklistResult,
            DataVerificationResult verificationResult) {
        Map<String, Object> report;
        boolean scored = false;

        if (creditScoreEngine.hasExternalFeaturesForScoring(request.getIdCard())) {
            try {
                CreditScoreEngine.ScoreDetailReport detailReport = creditScoreEngine.getScoreDetailReport(request);
                double creditLimit = creditScoreEngine.calculateCreditLimitWithFeatures(
                        detailReport.getTotalScore(), request.getMonthlyIncome(), request.getIdCard());
                report = buildRiskReport(request, detailReport, creditLimit, riskTags, auditRemark, externalRow);
                scored = true;
                riskAssessment.setTotalScore((int) Math.round(detailReport.getTotalScore()));
                riskAssessment.setSysDecision(detailReport.getDecision());
                warnIfScoreDetailsEmpty(applyId, report);
            } catch (Exception e) {
                log.warn("Early reject shadow score failed, applyId={}, gate={}: {}", applyId, rejectGate, e.getMessage());
                report = buildRiskReportWithoutScore(request, riskTags, auditRemark, externalRow);
            }
        } else {
            report = buildRiskReportWithoutScore(request, riskTags, auditRemark, externalRow);
        }

        report.put("modelVersion", resolveModelVersion());
        report.put("thirdPartyLinked", externalRow != null);
        report.put("scored", scored);
        report.put("finalStatus", StatusEnum.SYSTEM_REJECT.getValue());
        report.put("rejectGate", rejectGate);
        report.put("systemDecision", "REJECT");
        report.put("outcomeSummary", buildEarlyRejectSummary(rejectGate));

        if (blacklistResult != null) {
            Map<String, Object> blacklistCheck = new HashMap<>();
            blacklistCheck.put("hit", blacklistResult.getMatchLevel() != CreditScoreEngine.MatchLevel.NONE);
            blacklistCheck.put("source", "credit_data_db");
            blacklistCheck.put("level", blacklistResult.getMatchLevel().name());
            blacklistCheck.put("reason", blacklistResult.getMatchLevel().getDescription());
            report.put("blacklistCheck", blacklistCheck);
        }

        if (verificationResult != null) {
            Map<String, Object> incomeVerification = new HashMap<>();
            incomeVerification.put("passed", false);
            incomeVerification.put("reason", verificationResult.getReason());
            report.put("incomeVerification", incomeVerification);
        }

        safeCacheRiskReport(applyId, report);
    }

    private String buildEarlyRejectSummary(String rejectGate) {
        if (rejectGate == null) {
            return "系统拒绝";
        }
        return switch (rejectGate) {
            case "BLACKLIST_L3" -> "系统拒绝：三级黑名单命中（姓名+地域+出生年），未进入正式算分流程";
            case "INCOME_VERIFICATION" -> "系统拒绝：自填收入与后台数据偏差过大（>50%），收入验真未通过";
            case "SYSTEM_ERROR" -> "系统拒绝：评估过程异常";
            default -> "系统拒绝";
        };
    }

    private String resolveModelVersion() {
        try {
            ScoringRules rules = scoringRulesMapper.selectActiveRule();
            if (rules != null && rules.getVersion() != null && !rules.getVersion().isBlank()) {
                return rules.getVersion();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve active model version: {}", e.getMessage());
        }
        return "unknown";
    }

    private void syncUserAssessmentStatus(RiskAssessment riskAssessment) {
        String status = riskAssessment.getStatus();
        String target;
        if (StatusEnum.FINAL_PASS.getValue().equals(status)) {
            target = StatusEnum.FINAL_PASS.getValue();
        } else if (StatusEnum.MANUAL_REVIEW.getValue().equals(status)) {
            target = StatusEnum.MANUAL_REVIEW.getValue();
        } else if (StatusEnum.SYSTEM_REJECT.getValue().equals(status)) {
            target = StatusEnum.SYSTEM_REJECT.getValue();
        } else {
            target = StatusEnum.WAITING.getValue();
        }
        userServiceClient.updateAssessmentStatus(
                new UserAssessmentStatusCommand(riskAssessment.getUserId(), target));
    }

    /** 风控报告写入 Redis，TTL 见 {@link RedisCacheUtil#CACHE_EXPIRE_DAYS}（1 天）。 */
    private void cacheRiskReport(String applyId, Map<String, Object> report) {
        redisCacheUtil.set(RedisCacheUtil.getRiskReportKey(applyId), report);
    }

    private void safeCacheRiskReport(String applyId, Map<String, Object> report) {
        report.put("cachedAt", Instant.now().toString());
        report.remove("cacheWriteFailed");
        try {
            cacheRiskReport(applyId, report);
            log.debug("Redis risk report cached, applyId={}", applyId);
        } catch (Exception e) {
            report.put("cacheWriteFailed", true);
            log.error("Redis risk report cache failed, applyId={}", applyId, e);
        }
    }

    private void safeSendNotification(RiskAssessment riskAssessment, String status, String creditLimit) {
        try {
            sendNotification(riskAssessment, status, creditLimit);
        } catch (Exception e) {
            log.warn("Risk assessment email notification failed, applyId={}, status={}",
                    riskAssessment.getApplyId(), status, e);
        }
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
            AdminReportDisplayBuilder.enrichDetailFields(detailMap);
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
        UserSummary user = userServiceClient.getUser(riskAssessment.getUserId());
        if (user != null) {
            emailUtil.sendRiskAssessmentNotification(
                    user.email(),
                    user.realName(),
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
        if (score < MANUAL_REVIEW_THRESHOLD) {
            return "SCORE_LOW: 信用分过低";
        } else if (score < AUTO_APPROVE_THRESHOLD) {
            return "SCORE_MANUAL_REVIEW: 信用分区间需人工审核";
        } else {
            return "SCORE_MEDIUM: 信用分中等";
        }
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

    private static class SubmitEligibilityResult {
        boolean canSubmit;
        String reason;
        String existingApplyId;
        String existingStatus;
        Long retryAfterDays;
    }
}
