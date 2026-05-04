package org.example.risklendpro.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.entity.credit.Blacklist;
import org.example.risklendpro.entity.credit.ScoringRules;
import org.example.risklendpro.entity.credit.UserExternalFeatures;
import org.example.risklendpro.mapper.credit.BlacklistMapper;
import org.example.risklendpro.mapper.credit.ScoringRulesMapper;
import org.example.risklendpro.mapper.credit.UserExternalFeaturesMapper;
import org.example.risklendpro.pojo.request.RiskAssessmentRequest;
import org.example.risklendpro.service.CreditScoreEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CreditScoreEngineImpl implements CreditScoreEngine {

    @Autowired
    private BlacklistMapper blacklistMapper;

    @Autowired
    private UserExternalFeaturesMapper userExternalFeaturesMapper;

    @Autowired
    private ScoringRulesMapper scoringRulesMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static class ScoringConfig {
        Map<String, Object> featureScores;
        int autoApproveThreshold = 75;
        int manualReviewThreshold = 50;
    }

    @Override
    public boolean isInBlacklist(String idCard) {
        Blacklist blacklist = blacklistMapper.selectByIdCard(idCard);
        return blacklist != null;
    }

    private ScoringConfig loadScoringConfig() {
        ScoringConfig config = new ScoringConfig();
        ScoringRules rules = scoringRulesMapper.selectActiveRule();

        if (rules != null && rules.getRuleContent() != null) {
            try {
                Map<String, Object> ruleMap = objectMapper.readValue(rules.getRuleContent(),
                    new TypeReference<Map<String, Object>>() {});

                Object featureScoresObj = ruleMap.get("feature_scores");
                if (featureScoresObj instanceof Map) {
                    config.featureScores = (Map<String, Object>) featureScoresObj;
                }

                Object thresholdsObj = ruleMap.get("thresholds");
                if (thresholdsObj instanceof Map) {
                    Map<String, Object> thresholds = (Map<String, Object>) thresholdsObj;
                    Object autoApproveObj = thresholds.get("auto_approve");
                    if (autoApproveObj instanceof Number) {
                        config.autoApproveThreshold = ((Number) autoApproveObj).intValue();
                    }
                    Object manualReviewObj = thresholds.get("manual_review");
                    if (manualReviewObj instanceof Number) {
                        config.manualReviewThreshold = ((Number) manualReviewObj).intValue();
                    }
                }
            } catch (Exception e) {
                config.featureScores = null;
            }
        }

        return config;
    }

    private int getScoreFromGroups(int value, List<Map<String, Object>> groups) {
        for (Map<String, Object> group : groups) {
            List<Number> range = (List<Number>) group.get("range");
            double min = range.get(0).doubleValue();
            double max = range.get(1).doubleValue();
            if (value >= min && value < max) {
                return ((Number) group.get("score")).intValue();
            }
        }
        return 0;
    }

    private int getScoreFromDict(String key, Map<String, Object> scores) {
        if (scores.containsKey(key)) {
            return ((Number) scores.get(key)).intValue();
        }
        return 0;
    }

    @Override
    public int calculateScore(RiskAssessmentRequest request) {
        return calculateScoreWithDetails(request).getLeft();
    }

    private static class Pair<T, U> {
        private final T left;
        private final U right;

        public Pair(T left, U right) {
            this.left = left;
            this.right = right;
        }

        public T getLeft() { return left; }
        public U getRight() { return right; }
    }

    private Pair<Integer, List<ScoreContribution>> calculateScoreWithDetails(RiskAssessmentRequest request) {
        int score = 100;
        List<ScoreContribution> contributions = new ArrayList<>();
        contributions.add(new ScoreContribution("基础分", "基础分", 100.0, 100.0, "信用评估基础分"));

        ScoringConfig config = loadScoringConfig();
        Map<String, Object> featureScores = config.featureScores;

        int birthYear = Integer.parseInt(request.getBirthday().substring(0, 4));
        int age = java.time.LocalDate.now().getYear() - birthYear;

        int ageScore = calculateAgeScore(age, featureScores);
        score += ageScore;
        contributions.add(new ScoreContribution("年龄", String.valueOf(age), ageScore, ageScore,
            getAgeReason(age, featureScores)));

        int incomeScore = calculateIncomeScore(request.getMonthlyIncome(), featureScores);
        score += incomeScore;
        contributions.add(new ScoreContribution("月收入", request.getMonthlyIncome(), incomeScore, incomeScore,
            getIncomeReason(request.getMonthlyIncome(), featureScores)));

        int jobScore = calculateJobScore(request.getJobType(), featureScores);
        score += jobScore;
        contributions.add(new ScoreContribution("工作类型", request.getJobType(), jobScore, jobScore,
            getJobReason(request.getJobType(), featureScores)));

        int houseScore = calculateHouseScore(request.getHasHouse(), featureScores);
        score += houseScore;
        contributions.add(new ScoreContribution("房产", Boolean.TRUE.equals(request.getHasHouse()) ? "有" : "无", 
            houseScore, houseScore, Boolean.TRUE.equals(request.getHasHouse()) ? "有房产，资产稳定" : "无房产"));

        int carScore = calculateCarScore(request.getHasCar(), featureScores);
        score += carScore;
        contributions.add(new ScoreContribution("车产", Boolean.TRUE.equals(request.getHasCar()) ? "有" : "无", 
            carScore, carScore, Boolean.TRUE.equals(request.getHasCar()) ? "有车产，资产状况良好" : "无车产"));

        int educationScore = calculateEducationScore(request.getEducation(), featureScores);
        score += educationScore;
        contributions.add(new ScoreContribution("学历", request.getEducation(), educationScore, educationScore,
            getEducationReason(request.getEducation(), featureScores)));

        int marriageScore = calculateMarriageScore(request.getMarriage(), featureScores);
        score += marriageScore;
        contributions.add(new ScoreContribution("婚姻状况", request.getMarriage(), marriageScore, marriageScore,
            "已婚".equals(request.getMarriage()) ? "已婚，生活更稳定" : "未婚"));

        UserExternalFeatures externalFeatures = userExternalFeaturesMapper.selectByIdCard(request.getIdCard());
        if (externalFeatures != null) {
            int multiHeadScore = calculateMultiHeadScore(externalFeatures.getMultiHeadLoanCount(), featureScores);
            score += multiHeadScore;
            contributions.add(new ScoreContribution("多头借贷平台数", 
                String.valueOf(externalFeatures.getMultiHeadLoanCount()), 
                multiHeadScore, multiHeadScore,
                getMultiHeadReason(externalFeatures.getMultiHeadLoanCount(), featureScores)));

            int queryScore = calculateQueryScore(externalFeatures.getCreditQueryCount3m(), featureScores);
            score += queryScore;
            contributions.add(new ScoreContribution("近3月征信查询", 
                String.valueOf(externalFeatures.getCreditQueryCount3m()), 
                queryScore, queryScore,
                getQueryReason(externalFeatures.getCreditQueryCount3m(), featureScores)));

            int overdueScore = calculateOverdueScore(externalFeatures.getOverdueCount12m(), featureScores);
            score += overdueScore;
            contributions.add(new ScoreContribution("近12月逾期", 
                String.valueOf(externalFeatures.getOverdueCount12m()), 
                overdueScore, overdueScore,
                getOverdueReason(externalFeatures.getOverdueCount12m(), featureScores)));

            int dtiScore = calculateDtiScore(externalFeatures.getDti(), featureScores);
            score += dtiScore;
            contributions.add(new ScoreContribution("负债收入比", 
                String.valueOf(externalFeatures.getDti()), 
                dtiScore, dtiScore,
                getDtiReason(externalFeatures.getDti(), featureScores)));

            int extCreditScore = calculateExternalCreditScore(externalFeatures.getCreditScore());
            score += extCreditScore;
            contributions.add(new ScoreContribution("外部信用分", 
                String.valueOf(externalFeatures.getCreditScore()), 
                extCreditScore, extCreditScore,
                getExternalCreditReason(externalFeatures.getCreditScore())));

            if (externalFeatures.getDeviceIsVirtual() != null && externalFeatures.getDeviceIsVirtual() == 1) {
                score -= 20;
                contributions.add(new ScoreContribution("虚拟设备", "是", -20.0, -20.0, "检测到虚拟设备，风险较高"));
            }

            if (externalFeatures.getIpIsProxy() != null && externalFeatures.getIpIsProxy() == 1) {
                score -= 10;
                contributions.add(new ScoreContribution("代理IP", "是", -10.0, -10.0, "检测到代理IP，风险中等"));
            }
        }

        score = Math.max(10, Math.min(90, score));
        return new Pair<>(score, contributions);
    }

    private int calculateAgeScore(int age, Map<String, Object> featureScores) {
        if (featureScores == null) {
            if (age < 22) return -10;
            if (age < 25) return -10;
            if (age < 30) return -5;
            if (age < 40) return 8;
            if (age < 50) return 5;
            return -3;
        }

        Map<String, Object> ageConfig = (Map<String, Object>) featureScores.get("age");
        if (ageConfig == null) return 0;

        List<Map<String, Object>> groups = (List<Map<String, Object>>) ageConfig.get("groups");
        return getScoreFromGroups(age, groups);
    }

    private String getAgeReason(int age, Map<String, Object> featureScores) {
        if (featureScores == null) {
            if (age < 25) return "年龄较小";
            if (age < 30) return "青年时期";
            if (age < 40) return "黄金年龄段";
            if (age < 50) return "稳定年龄段";
            return "年龄较大";
        }

        Map<String, Object> ageConfig = (Map<String, Object>) featureScores.get("age");
        if (ageConfig == null) return "";

        List<Map<String, Object>> groups = (List<Map<String, Object>>) ageConfig.get("groups");
        for (Map<String, Object> group : groups) {
            List<Number> range = (List<Number>) group.get("range");
            double min = range.get(0).doubleValue();
            double max = range.get(1).doubleValue();
            if (age >= min && age < max) {
                return (String) group.get("reason");
            }
        }
        return "";
    }

    private int calculateIncomeScore(String income, Map<String, Object> featureScores) {
        if (featureScores == null) {
            if ("3000以下".equals(income)) return -15;
            if ("3000-8000".equals(income)) return -5;
            if ("8000-15000".equals(income)) return 5;
            if ("15000以上".equals(income)) return 15;
            return 0;
        }

        Map<String, Object> incomeConfig = (Map<String, Object>) featureScores.get("income");
        if (incomeConfig == null) return 0;

        List<Map<String, Object>> groups = (List<Map<String, Object>>) incomeConfig.get("groups");
        int incomeValue = mapIncomeToValue(income);
        return getScoreFromGroups(incomeValue, groups);
    }

    private String getIncomeReason(String income, Map<String, Object> featureScores) {
        if (featureScores == null) {
            if ("3000以下".equals(income)) return "收入较低";
            if ("3000-8000".equals(income)) return "收入一般";
            if ("8000-15000".equals(income)) return "收入中等";
            if ("15000以上".equals(income)) return "收入较高";
            return "";
        }

        Map<String, Object> incomeConfig = (Map<String, Object>) featureScores.get("income");
        if (incomeConfig == null) return "";

        List<Map<String, Object>> groups = (List<Map<String, Object>>) incomeConfig.get("groups");
        int incomeValue = mapIncomeToValue(income);
        for (Map<String, Object> group : groups) {
            List<Number> range = (List<Number>) group.get("range");
            double min = range.get(0).doubleValue();
            double max = range.get(1).doubleValue();
            if (incomeValue >= min && incomeValue < max) {
                return (String) group.get("reason");
            }
        }
        return "";
    }

    private int mapIncomeToValue(String income) {
        if ("3000以下".equals(income)) return 2000;
        if ("3000-8000".equals(income)) return 5500;
        if ("8000-15000".equals(income)) return 11500;
        if ("15000以上".equals(income)) return 20000;
        return 5500;
    }

    private int calculateJobScore(String jobType, Map<String, Object> featureScores) {
        if (featureScores == null) {
            if ("公务员".equals(jobType)) return 15;
            if ("企事业单位".equals(jobType)) return 10;
            if ("私营企业".equals(jobType)) return 0;
            return -5;
        }

        Map<String, Object> jobConfig = (Map<String, Object>) featureScores.get("job_type");
        if (jobConfig == null) return 0;

        Map<String, Object> scores = (Map<String, Object>) jobConfig.get("scores");
        return getScoreFromDict(jobType, scores);
    }

    private String getJobReason(String jobType, Map<String, Object> featureScores) {
        if ("公务员".equals(jobType)) return "最稳定工作";
        if ("企事业单位".equals(jobType)) return "稳定工作";
        if ("私营企业".equals(jobType)) return "一般工作";
        return "工作不稳定";
    }

    private int calculateHouseScore(Boolean hasHouse, Map<String, Object> featureScores) {
        if (featureScores == null) {
            return Boolean.TRUE.equals(hasHouse) ? 10 : 0;
        }

        Map<String, Object> houseConfig = (Map<String, Object>) featureScores.get("has_house");
        if (houseConfig == null) return 0;

        if (Boolean.TRUE.equals(hasHouse)) {
            return ((Number) houseConfig.get("has")).intValue();
        }
        return ((Number) houseConfig.get("none")).intValue();
    }

    private int calculateCarScore(Boolean hasCar, Map<String, Object> featureScores) {
        if (featureScores == null) {
            return Boolean.TRUE.equals(hasCar) ? 5 : 0;
        }

        Map<String, Object> carConfig = (Map<String, Object>) featureScores.get("has_car");
        if (carConfig == null) return 0;

        if (Boolean.TRUE.equals(hasCar)) {
            return ((Number) carConfig.get("has")).intValue();
        }
        return ((Number) carConfig.get("none")).intValue();
    }

    private int calculateEducationScore(String education, Map<String, Object> featureScores) {
        if (featureScores == null) {
            if ("博士".equals(education)) return 15;
            if ("硕士".equals(education)) return 10;
            if ("本科".equals(education)) return 5;
            return -5;
        }

        Map<String, Object> eduConfig = (Map<String, Object>) featureScores.get("education");
        if (eduConfig == null) return 0;

        Map<String, Object> scores = (Map<String, Object>) eduConfig.get("scores");
        return getScoreFromDict(education, scores);
    }

    private String getEducationReason(String education, Map<String, Object> featureScores) {
        if ("博士".equals(education)) return "高学历";
        if ("硕士".equals(education)) return "较高学历";
        if ("本科".equals(education)) return "本科学历";
        return "学历较低";
    }

    private int calculateMarriageScore(String marriage, Map<String, Object> featureScores) {
        if (featureScores == null) {
            return "已婚".equals(marriage) ? 5 : 0;
        }

        Map<String, Object> marriageConfig = (Map<String, Object>) featureScores.get("marriage");
        if (marriageConfig == null) return 0;

        if ("已婚".equals(marriage)) {
            return ((Number) marriageConfig.get("married")).intValue();
        }
        return ((Number) marriageConfig.get("single")).intValue();
    }

    private int calculateMultiHeadScore(Integer count, Map<String, Object> featureScores) {
        if (count == null) return 0;

        if (featureScores == null) {
            if (count >= 10) return -30;
            if (count >= 6) return -20;
            if (count >= 3) return -10;
            return 0;
        }

        Map<String, Object> config = (Map<String, Object>) featureScores.get("multi_head_loan_count");
        if (config == null) return 0;

        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        return getScoreFromGroups(count, groups);
    }

    private String getMultiHeadReason(Integer count, Map<String, Object> featureScores) {
        if (count == null) return "";

        if (featureScores == null) {
            if (count >= 10) return "极高多头";
            if (count >= 6) return "高多头";
            if (count >= 3) return "中等多头";
            return "正常";
        }

        Map<String, Object> config = (Map<String, Object>) featureScores.get("multi_head_loan_count");
        if (config == null) return "";

        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        for (Map<String, Object> group : groups) {
            List<Number> range = (List<Number>) group.get("range");
            double min = range.get(0).doubleValue();
            double max = range.get(1).doubleValue();
            if (count >= min && count < max) {
                return (String) group.get("reason");
            }
        }
        return "";
    }

    private int calculateQueryScore(Integer count, Map<String, Object> featureScores) {
        if (count == null) return 0;

        if (featureScores == null) {
            if (count >= 10) return -15;
            if (count >= 6) return -10;
            if (count >= 3) return -5;
            return 0;
        }

        Map<String, Object> config = (Map<String, Object>) featureScores.get("credit_query_count_3m");
        if (config == null) return 0;

        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        return getScoreFromGroups(count, groups);
    }

    private String getQueryReason(Integer count, Map<String, Object> featureScores) {
        if (count == null) return "";

        if (featureScores == null) {
            if (count >= 10) return "异常频繁";
            if (count >= 6) return "查询频繁";
            if (count >= 3) return "查询较多";
            return "正常";
        }

        Map<String, Object> config = (Map<String, Object>) featureScores.get("credit_query_count_3m");
        if (config == null) return "";

        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        for (Map<String, Object> group : groups) {
            List<Number> range = (List<Number>) group.get("range");
            double min = range.get(0).doubleValue();
            double max = range.get(1).doubleValue();
            if (count >= min && count < max) {
                return (String) group.get("reason");
            }
        }
        return "";
    }

    private int calculateOverdueScore(Integer count, Map<String, Object> featureScores) {
        if (count == null) return 0;

        if (featureScores == null) {
            if (count >= 3) return -40;
            if (count >= 2) return -25;
            if (count >= 1) return -15;
            return 0;
        }

        Map<String, Object> config = (Map<String, Object>) featureScores.get("overdue_count_12m");
        if (config == null) return 0;

        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        return getScoreFromGroups(count, groups);
    }

    private String getOverdueReason(Integer count, Map<String, Object> featureScores) {
        if (count == null) return "";

        if (featureScores == null) {
            if (count >= 3) return "严重逾期";
            if (count >= 2) return "多次逾期";
            if (count >= 1) return "有逾期记录";
            return "无逾期";
        }

        Map<String, Object> config = (Map<String, Object>) featureScores.get("overdue_count_12m");
        if (config == null) return "";

        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        for (Map<String, Object> group : groups) {
            List<Number> range = (List<Number>) group.get("range");
            double min = range.get(0).doubleValue();
            double max = range.get(1).doubleValue();
            if (count >= min && count < max) {
                return (String) group.get("reason");
            }
        }
        return "";
    }

    private int calculateDtiScore(Integer dti, Map<String, Object> featureScores) {
        if (dti == null) return 0;

        if (featureScores == null) {
            if (dti >= 30) return -15;
            if (dti >= 15) return 0;
            return 5;
        }

        Map<String, Object> config = (Map<String, Object>) featureScores.get("dti");
        if (config == null) return 0;

        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        return getScoreFromGroups(dti, groups);
    }

    private String getDtiReason(Integer dti, Map<String, Object> featureScores) {
        if (dti == null) return "";

        if (featureScores == null) {
            if (dti >= 30) return "负债较高";
            if (dti >= 15) return "负债正常";
            return "负债低";
        }

        Map<String, Object> config = (Map<String, Object>) featureScores.get("dti");
        if (config == null) return "";

        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        for (Map<String, Object> group : groups) {
            List<Number> range = (List<Number>) group.get("range");
            double min = range.get(0).doubleValue();
            double max = range.get(1).doubleValue();
            if (dti >= min && dti < max) {
                return (String) group.get("reason");
            }
        }
        return "";
    }

    private int calculateExternalCreditScore(Integer score) {
        if (score == null) return 0;
        if (score >= 800) return 10;
        if (score >= 700) return 5;
        if (score < 600) return -10;
        return 0;
    }

    private String getExternalCreditReason(Integer score) {
        if (score == null) return "";
        if (score >= 800) return "外部信用非常优秀";
        if (score >= 700) return "外部信用良好";
        if (score < 600) return "外部信用较差";
        return "外部信用一般";
    }

    @Override
    public String getDecision(int score) {
        ScoringConfig config = loadScoringConfig();
        if (score >= config.autoApproveThreshold) {
            return "APPROVE";
        } else if (score >= config.manualReviewThreshold) {
            return "REVIEW";
        } else {
            return "REJECT";
        }
    }

    @Override
    public double calculateCreditLimit(int score, String monthlyIncome) {
        return calculateCreditLimitWithFeatures(score, monthlyIncome, null);
    }

    @Override
    public double calculateCreditLimitWithFeatures(int score, String monthlyIncome, String idCard) {
        int incomeValue = mapIncomeToValue(monthlyIncome);
        double limit = 0;

        ScoringConfig config = loadScoringConfig();
        if (score >= config.autoApproveThreshold) {
            limit = incomeValue * 12;
        } else if (score >= config.manualReviewThreshold) {
            limit = incomeValue * 10;
        } else {
            limit = incomeValue * 8;
        }

        if (idCard != null) {
            limit = adjustLimitByExternalFeatures(limit, idCard, incomeValue);
        }

        if (score >= config.autoApproveThreshold && limit > 500000) {
            limit = 500000;
        } else if (score >= config.manualReviewThreshold && score < config.autoApproveThreshold && limit > 300000) {
            limit = 300000;
        } else if (score < config.manualReviewThreshold && limit > 200000) {
            limit = 200000;
        }

        return Math.max(0, limit);
    }

    private double adjustLimitByExternalFeatures(double baseLimit, String idCard, int incomeValue) {
        UserExternalFeatures features = userExternalFeaturesMapper.selectByIdCard(idCard);
        if (features == null) {
            return baseLimit;
        }

        double adjustedLimit = baseLimit;
        double penaltyRate = 0.0;

        if (features.getMultiHeadLoanCount() != null && features.getMultiHeadLoanCount() > 3) {
            int excess = features.getMultiHeadLoanCount() - 3;
            penaltyRate += excess * 0.10;
        }

        if (features.getOverdueCount12m() != null && features.getOverdueCount12m() > 0) {
            penaltyRate += features.getOverdueCount12m() * 0.15;
        }

        if (features.getCreditQueryCount3m() != null && features.getCreditQueryCount3m() > 5) {
            penaltyRate += 0.05;
        }

        if (features.getDeviceIsVirtual() != null && features.getDeviceIsVirtual() == 1) {
            penaltyRate += 0.50;
        }

        if (features.getIpIsProxy() != null && features.getIpIsProxy() == 1) {
            penaltyRate += 0.20;
        }

        if (features.getMultiHeadLoanTotalAmount() != null
                && features.getMultiHeadLoanTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            int annualIncome = incomeValue * 12;
            double amountRatio = features.getMultiHeadLoanTotalAmount().doubleValue() / annualIncome;
            if (amountRatio > 0.5) {
                penaltyRate += 0.10;
            }
        }

        penaltyRate = Math.min(penaltyRate, 0.80);
        adjustedLimit = baseLimit * (1 - penaltyRate);

        return adjustedLimit;
    }

    @Override
    public ScoreDetailReport getScoreDetailReport(RiskAssessmentRequest request) {
        Pair<Integer, List<ScoreContribution>> result = calculateScoreWithDetails(request);
        int score = result.getLeft();
        List<ScoreContribution> contributions = result.getRight();

        String decision = getDecision(score);
        ExternalFeatures externalFeatures = getExternalFeatures(request.getIdCard());
        BlacklistCheck blacklistCheck = getBlacklistCheck(request.getIdCard());

        return new ScoreDetailReport(score, decision, contributions, externalFeatures, blacklistCheck);
    }

    private ExternalFeatures getExternalFeatures(String idCard) {
        UserExternalFeatures features = userExternalFeaturesMapper.selectByIdCard(idCard);
        ExternalFeatures result = new ExternalFeatures();

        if (features != null) {
            result.setCreditScore(features.getCreditScore());
            result.setOverdueCount12m(features.getOverdueCount12m());
            result.setCreditQueryCount3m(features.getCreditQueryCount3m());
            result.setMultiHeadLoanCount(features.getMultiHeadLoanCount());
            result.setMultiHeadLoanTotalAmount(features.getMultiHeadLoanTotalAmount());
            result.setDeviceIsVirtual(features.getDeviceIsVirtual());
            result.setIpIsProxy(features.getIpIsProxy());
            result.setDataSource(features.getDataSource());
            if (features.getUpdatedAt() != null) {
                result.setUpdatedAt(features.getUpdatedAt().toString());
            }
        }

        return result;
    }

    private BlacklistCheck getBlacklistCheck(String idCard) {
        Blacklist blacklist = blacklistMapper.selectByIdCard(idCard);
        if (blacklist != null) {
            return new BlacklistCheck(true, blacklist.getSource(), blacklist.getReason());
        }
        return new BlacklistCheck(false, null, null);
    }
}
