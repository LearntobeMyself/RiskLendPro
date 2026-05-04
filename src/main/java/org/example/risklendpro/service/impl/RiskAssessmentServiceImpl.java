package org.example.risklendpro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.entity.RiskAssessment;
import org.example.risklendpro.entity.User;
import org.example.risklendpro.entity.UserCreditLimit;
import org.example.risklendpro.enums.StatusEnum;
import org.example.risklendpro.mapper.RiskAssessmentMapper;
import org.example.risklendpro.mapper.UserCreditLimitMapper;
import org.example.risklendpro.mapper.UserMapper;
import org.example.risklendpro.pojo.request.RiskAssessmentRequest;
import org.example.risklendpro.pojo.response.RiskAssessmentResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentStatusResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentResultResponse;
import org.example.risklendpro.service.CreditScoreEngine;
import org.example.risklendpro.service.RiskAssessmentService;
import org.example.risklendpro.utils.EmailUtil;
import org.example.risklendpro.utils.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RiskAssessmentServiceImpl implements RiskAssessmentService {

    @Autowired
    private RiskAssessmentMapper riskAssessmentMapper;

    @Autowired
    private UserMapper userMapper;

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

        RiskAssessment existingAssessment = riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>()
                        .eq("user_id", userId)
                        .eq("status", "WAITING")
                        .eq("is_final", false)
        );
        if (existingAssessment != null) {
            throw new RuntimeException("您已有正在处理中的评估申请，请等待处理完成");
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

        riskAssessmentMapper.insert(riskAssessment);

        user.setAssessmentStatus(StatusEnum.WAITING.getValue());
        userMapper.updateById(user);

        executeRiskAssessment(riskAssessment, request);

        RiskAssessmentResponse response = new RiskAssessmentResponse();
        response.setApplyId(applyId);
        response.setSubmitTime(riskAssessment.getSubmitTime());
        response.setStatus(riskAssessment.getStatus());

        return response;
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
        try {
            String idCard = request.getIdCard();

            if (creditScoreEngine.isInBlacklist(idCard)) {
                riskAssessment.setStatus(StatusEnum.SYSTEM_REJECT.getValue());
                riskAssessment.setSysDecision("REJECT");
                riskAssessment.setTotalScore(0);
                riskAssessment.setCreditLimit(BigDecimal.ZERO);
                riskAssessment.setIsFinal(true);
                riskAssessment.setAuditRemark("命中黑名单");
                riskAssessment.setApprovalTime(new Date());
                riskAssessmentMapper.updateById(riskAssessment);

                sendNotification(riskAssessment, "评估拒绝", "0");
                return;
            }

            int score = creditScoreEngine.calculateScore(request);

            String decision = creditScoreEngine.getDecision(score);

            double creditLimit = creditScoreEngine.calculateCreditLimitWithFeatures(
                score, request.getMonthlyIncome(), request.getIdCard());

            riskAssessment.setTotalScore(score);
            riskAssessment.setSysDecision(decision);
            riskAssessment.setApprovalTime(new Date());

            Map<String, Object> report = buildRiskReport(request, score, decision, creditLimit);
            String cacheKey = RedisCacheUtil.getRiskReportKey(riskAssessment.getApplyId());
            redisCacheUtil.set(cacheKey, report);

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
                case "REVIEW":
                    riskAssessment.setStatus(StatusEnum.MANUAL_REVIEW.getValue());
                    riskAssessment.setIsFinal(false);
                    break;
                case "REJECT":
                    riskAssessment.setStatus(StatusEnum.SYSTEM_REJECT.getValue());
                    riskAssessment.setIsFinal(true);
                    riskAssessment.setCreditLimit(BigDecimal.ZERO);
                    sendNotification(riskAssessment, "评估拒绝", "0");
                    break;
            }

            riskAssessmentMapper.updateById(riskAssessment);

        } catch (Exception e) {
            riskAssessment.setStatus(StatusEnum.SYSTEM_REJECT.getValue());
            riskAssessment.setIsFinal(true);
            riskAssessment.setAuditRemark("风控评估异常: " + e.getMessage());
            riskAssessmentMapper.updateById(riskAssessment);
        }
    }

    private Map<String, Object> buildRiskReport(RiskAssessmentRequest request, int score, String decision, double creditLimit) {
        Map<String, Object> report = new HashMap<>();

        CreditScoreEngine.ScoreDetailReport detailReport = creditScoreEngine.getScoreDetailReport(request);

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

        report.put("totalScore", score);
        report.put("systemDecision", decision);
        report.put("suggestedAmount", (int) creditLimit);

        List<Map<String, Object>> scoreDetails = new ArrayList<>();
        for (CreditScoreEngine.ScoreContribution contribution : detailReport.getScoreDetails()) {
            Map<String, Object> detailMap = new HashMap<>();
            detailMap.put("feature", contribution.getFeature());
            detailMap.put("value", contribution.getValue());
            detailMap.put("weight", contribution.getWeight());
            detailMap.put("contribution", contribution.getContribution());
            detailMap.put("description", contribution.getDescription());
            scoreDetails.add(detailMap);
        }
        report.put("scoreDetails", scoreDetails);

        Map<String, Object> externalFeatures = new HashMap<>();
        externalFeatures.put("creditScore", detailReport.getExternalFeatures().getCreditScore());
        externalFeatures.put("overdueCount12m", detailReport.getExternalFeatures().getOverdueCount12m());
        externalFeatures.put("creditQueryCount3m", detailReport.getExternalFeatures().getCreditQueryCount3m());
        externalFeatures.put("multiHeadLoanCount", detailReport.getExternalFeatures().getMultiHeadLoanCount());
        externalFeatures.put("multiHeadLoanTotalAmount", detailReport.getExternalFeatures().getMultiHeadLoanTotalAmount());
        externalFeatures.put("deviceIsVirtual", detailReport.getExternalFeatures().getDeviceIsVirtual());
        externalFeatures.put("ipIsProxy", detailReport.getExternalFeatures().getIpIsProxy());
        externalFeatures.put("dataSource", detailReport.getExternalFeatures().getDataSource());
        externalFeatures.put("updatedAt", detailReport.getExternalFeatures().getUpdatedAt());
        report.put("externalFeatures", externalFeatures);

        Map<String, Object> blacklistCheck = new HashMap<>();
        blacklistCheck.put("hit", detailReport.getBlacklistCheck().isHit());
        blacklistCheck.put("source", detailReport.getBlacklistCheck().getSource());
        blacklistCheck.put("reason", detailReport.getBlacklistCheck().getReason());
        report.put("blacklistCheck", blacklistCheck);

        return report;
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
}
