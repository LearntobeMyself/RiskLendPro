package org.example.risklendpro.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.risklendpro.entity.credit.Blacklist;
import org.example.risklendpro.entity.credit.ScoringRules;
import org.example.risklendpro.entity.credit.UserExternalFeatures;
import org.example.risklendpro.mapper.credit.BlacklistMapper;
import org.example.risklendpro.mapper.credit.ScoringRulesMapper;
import org.example.risklendpro.mapper.credit.UserExternalFeaturesMapper;
import org.example.risklendpro.pojo.request.RiskAssessmentRequest;
import org.example.risklendpro.service.CreditScoreEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CreditScoreEngineImpl implements CreditScoreEngine {

    private static final Logger log = LoggerFactory.getLogger(CreditScoreEngineImpl.class);

    @Autowired
    private BlacklistMapper blacklistMapper;

    @Autowired
    private UserExternalFeaturesMapper userExternalFeaturesMapper;

    @Autowired
    private ScoringRulesMapper scoringRulesMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final class Pair<T, U> {
        private final T left;
        private final U right;

        Pair(T left, U right) {
            this.left = left;
            this.right = right;
        }

        T getLeft() { return left; }
        U getRight() { return right; }
    }

    /** 与 Python rule JSON + scoring_rules 表列对齐 */
    private static final class FullRuleConfig {
        JsonNode ruleRoot;
        ScoringRules rulesEntity;
        Map<String, Object> featureScores;
        /** v5 规则缺省：350–950 PDO 量表下的占位阈值（以 DB/JSON 为准） */
        double autoApproveThreshold = 720;
        double manualReviewThreshold = 580;
    }

    @Override
    public boolean isInBlacklist(String idCard) {
        String areaCode = extractAreaCodeFromIdCard(idCard);
        Integer birthYear = extractBirthYearFromIdCard(idCard);
        List<Blacklist> allBlacklist = blacklistMapper.selectAll();
        for (Blacklist record : allBlacklist) {
            if (areaCode != null && areaCode.equals(record.getAreaCode())) {
                if (birthYear != null && birthYear.equals(record.getBirthYear())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public CreditScoreEngine.BlacklistMatchResult checkBlacklist(String name, String idCard) {
        if (name == null || name.trim().isEmpty()) {
            return new CreditScoreEngine.BlacklistMatchResult(CreditScoreEngine.MatchLevel.NONE, null);
        }

        String areaCode = extractAreaCodeFromIdCard(idCard);
        Integer birthYear = extractBirthYearFromIdCard(idCard);

        List<Blacklist> allBlacklist = blacklistMapper.selectAll();

        Blacklist bestNameArea = null;
        Blacklist bestNameOnly = null;

        for (Blacklist record : allBlacklist) {
            String blacklistName = record.getName();
            if (blacklistName == null) {
                continue;
            }

            if (!matchWildcardName(name, blacklistName)) {
                continue;
            }

            String recordAreaCode = record.getAreaCode();
            Integer recordBirthYear = record.getBirthYear();

            boolean areaMatch = (recordAreaCode != null && !recordAreaCode.isEmpty()) &&
                               (areaCode != null && areaCode.equals(recordAreaCode));
            boolean birthYearMatch = (recordBirthYear != null) && (birthYear != null) &&
                                    recordBirthYear.equals(birthYear);

            if (areaMatch && birthYearMatch) {
                return new CreditScoreEngine.BlacklistMatchResult(CreditScoreEngine.MatchLevel.FULL, record);
            }

            if (areaMatch) {
                bestNameArea = record;
                continue;
            }

            if (bestNameOnly == null) {
                bestNameOnly = record;
            }
        }

        if (bestNameArea != null) {
            return new CreditScoreEngine.BlacklistMatchResult(CreditScoreEngine.MatchLevel.NAME_AREA, bestNameArea);
        }
        if (bestNameOnly != null) {
            return new CreditScoreEngine.BlacklistMatchResult(CreditScoreEngine.MatchLevel.NAME_ONLY, bestNameOnly);
        }

        return new CreditScoreEngine.BlacklistMatchResult(CreditScoreEngine.MatchLevel.NONE, null);
    }

    private boolean matchWildcardName(String realName, String patternName) {
        String regex = patternName.replace("*", ".*");
        return realName.matches(regex);
    }

    private String extractAreaCodeFromIdCard(String idCard) {
        if (idCard == null || idCard.length() < 6) {
            return "";
        }
        return idCard.substring(0, 6);
    }

    private Integer extractBirthYearFromIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return null;
        }
        try {
            String yearStr = idCard.substring(6, 10);
            return Integer.parseInt(yearStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private FullRuleConfig loadFullRuleConfig() {
        FullRuleConfig cfg = new FullRuleConfig();
        ScoringRules rules = scoringRulesMapper.selectActiveRule();
        cfg.rulesEntity = rules;
        if (rules == null) {
            return cfg;
        }
        if ((rules.getFeatureWeights() == null || rules.getFeatureWeights().isBlank())
                && rules.getRuleContent() == null) {
            return cfg;
        }
        try {
            JsonNode root = null;
            if (rules.getRuleContent() != null && !rules.getRuleContent().isBlank()) {
                JsonNode full = objectMapper.readTree(rules.getRuleContent());
                if (full.has("model_type")) {
                    String mt = full.get("model_type").asText();
                    if (mt.startsWith("hc_")) {
                        root = full;
                    }
                }
            }
            if (root == null) {
                root = buildRuleRootFromScoringColumns(rules);
            }
            if (root == null && rules.getRuleContent() != null) {
                root = objectMapper.readTree(rules.getRuleContent());
            }
            normalizeRuleRoot(root, rules);
            cfg.ruleRoot = root;
            JsonNode fs = cfg.ruleRoot != null ? cfg.ruleRoot.get("feature_scores") : null;
            if (fs != null && fs.isObject()) {
                cfg.featureScores = objectMapper.convertValue(fs, new TypeReference<Map<String, Object>>() {});
            }
            JsonNode th = cfg.ruleRoot != null ? cfg.ruleRoot.get("thresholds") : null;
            if (th != null && th.isObject()) {
                if (th.has("auto_approve")) {
                    cfg.autoApproveThreshold = th.get("auto_approve").asDouble();
                }
                if (th.has("manual_review")) {
                    cfg.manualReviewThreshold = th.get("manual_review").asDouble();
                }
            }
            if (rules.getThresholdAutoApprove() != null) {
                cfg.autoApproveThreshold = rules.getThresholdAutoApprove().doubleValue();
            }
            if (rules.getThresholdManualReview() != null) {
                cfg.manualReviewThreshold = rules.getThresholdManualReview().doubleValue();
            }
        } catch (Exception e) {
            cfg.ruleRoot = null;
            cfg.featureScores = null;
        }
        return cfg;
    }

    /**
     * 使用 scoring_rules 表中解析列拼装与 rule_content 等价的 JSON 树（优先于解析整包 rule_content）。
     */
    private JsonNode buildRuleRootFromScoringColumns(ScoringRules rules) throws Exception {
        if (rules.getFeatureWeights() == null || rules.getFeatureWeights().isBlank()) {
            return null;
        }
        if (rules.getScorecard() == null || rules.getScorecard().isBlank()) {
            return null;
        }
        ObjectNode root = objectMapper.createObjectNode();
        if (rules.getVersion() != null) {
            root.put("version", rules.getVersion());
        }
        if (rules.getIntercept() != null) {
            root.put("intercept", rules.getIntercept().doubleValue());
        }
        root.set("feature_weights", objectMapper.readTree(rules.getFeatureWeights()));
        root.set("scorecard", objectMapper.readTree(rules.getScorecard()));
        ObjectNode th = objectMapper.createObjectNode();
        if (rules.getThresholdAutoApprove() != null) {
            th.put("auto_approve", rules.getThresholdAutoApprove().doubleValue());
        }
        if (rules.getThresholdManualReview() != null) {
            th.put("manual_review", rules.getThresholdManualReview().doubleValue());
        }
        root.set("thresholds", th);
        if (rules.getApplicationRuleBonus() != null && !rules.getApplicationRuleBonus().isBlank()) {
            root.set("application_rule_bonus", objectMapper.readTree(rules.getApplicationRuleBonus()));
        }
        if (rules.getFeatureScores() != null && !rules.getFeatureScores().isBlank()) {
            root.set("feature_scores", objectMapper.readTree(rules.getFeatureScores()));
        }
        if (rules.getFeatureDerivation() != null && !rules.getFeatureDerivation().isBlank()) {
            root.set("feature_derivation", objectMapper.readTree(rules.getFeatureDerivation()));
        }
        return root;
    }

    /**
     * v7 WOE 规则 JSON 使用 coefficients；列拼装或旧规则使用 feature_weights。统一别名并补全 WOE 所需字段。
     */
    private void normalizeRuleRoot(JsonNode root, ScoringRules rules) {
        if (root == null || !root.isObject()) {
            return;
        }
        ObjectNode obj = (ObjectNode) root;
        JsonNode coef = obj.get("coefficients");
        JsonNode fw = obj.get("feature_weights");
        boolean hasCoef = coef != null && coef.isObject() && !coef.isEmpty();
        boolean hasFw = fw != null && fw.isObject() && !fw.isEmpty();
        if (!hasFw && hasCoef) {
            obj.set("feature_weights", coef);
        } else if (!hasCoef && hasFw) {
            obj.set("coefficients", fw);
        }

        if (rules == null || rules.getRuleContent() == null || rules.getRuleContent().isBlank()) {
            return;
        }
        try {
            JsonNode full = objectMapper.readTree(rules.getRuleContent());
            if (!obj.has("model_type") && full.has("model_type")) {
                obj.set("model_type", full.get("model_type"));
            }
            if (!obj.has("features") && full.has("features")) {
                obj.set("features", full.get("features"));
            }
            if ((!obj.has("coefficients") || obj.get("coefficients").isEmpty())
                    && full.has("coefficients") && full.get("coefficients").isObject()) {
                obj.set("coefficients", full.get("coefficients"));
                if (!obj.has("feature_weights") || obj.get("feature_weights").isEmpty()) {
                    obj.set("feature_weights", full.get("coefficients"));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to supplement rule root from rule_content: {}", e.getMessage());
        }
    }

    private JsonNode getLrCoefficientsNode(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode weights = root.get("feature_weights");
        if (weights != null && weights.isObject() && !weights.isEmpty()) {
            return weights;
        }
        JsonNode coef = root.get("coefficients");
        if (coef != null && coef.isObject() && !coef.isEmpty()) {
            return coef;
        }
        return null;
    }

    private boolean hasFeatureWeights(FullRuleConfig cfg) {
        return getLrCoefficientsNode(cfg.ruleRoot) != null;
    }

    private double readIntercept(FullRuleConfig cfg) {
        if (cfg.rulesEntity != null && cfg.rulesEntity.getIntercept() != null) {
            return cfg.rulesEntity.getIntercept().doubleValue();
        }
        if (cfg.ruleRoot != null && cfg.ruleRoot.has("intercept")) {
            return cfg.ruleRoot.get("intercept").asDouble();
        }
        return 0;
    }

    /**
     * 与 train_scoring_model.calculate_scorecard_score 一致。
     * scale=inverse_prob_0_100：round(100*(1-p)) 再 clamp（旧版）。
     * 否则 odds_pdo：PDO+log-odds，再 clamp 到 min_score/max_score（默认 350–950）。
     */
    private double scorecardFromProb(double probDefault, JsonNode scorecardNode) {
        final double eps = 1e-9;
        double p = probDefault;
        if (p <= 0.0) {
            p = eps;
        }
        if (p >= 1.0) {
            p = 1.0 - eps;
        }

        boolean inverse0100 = scorecardNode != null && scorecardNode.isObject()
                && scorecardNode.has("scale")
                && "inverse_prob_0_100".equals(scorecardNode.get("scale").asText());
        if (inverse0100) {
            int minS = scorecardNode.has("min_score") ? scorecardNode.get("min_score").asInt() : 0;
            int maxS = scorecardNode.has("max_score") ? scorecardNode.get("max_score").asInt() : 100;
            double score = Math.round(100.0 * (1.0 - p));
            return Math.max(minS, Math.min(maxS, score));
        }

        double pdo = 80;
        double targetScore = 650;
        double targetOdds = 1;
        int minS = 350;
        int maxS = 950;
        if (scorecardNode != null && scorecardNode.isObject()) {
            if (scorecardNode.has("pdo")) {
                pdo = scorecardNode.get("pdo").asDouble();
            }
            if (scorecardNode.has("target_score")) {
                targetScore = scorecardNode.get("target_score").asDouble();
            }
            if (scorecardNode.has("target_odds")) {
                targetOdds = scorecardNode.get("target_odds").asDouble();
            }
            if (scorecardNode.has("min_score")) {
                minS = scorecardNode.get("min_score").asInt();
            }
            if (scorecardNode.has("max_score")) {
                maxS = scorecardNode.get("max_score").asInt();
            }
        }
        double odds = p / (1.0 - p);
        double factor = -pdo / Math.log(2);
        double offset = targetScore - factor * Math.log(targetOdds);
        double score = offset + factor * Math.log(odds);
        score = Math.max(minS, Math.min(maxS, score));
        return Math.round(score * 100.0) / 100.0;
    }

    private static double sigmoid(double x) {
        if (x >= 30) {
            return 1.0;
        }
        if (x <= -30) {
            return 0.0;
        }
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /** pandas pd.cut(..., right=True) 与 train_scoring_model.bin_features 一致 */
    private static String ageBinLabel(int age) {
        if (age <= 25) {
            return "age_0_25";
        }
        if (age <= 35) {
            return "age_26_35";
        }
        if (age <= 50) {
            return "age_36_50";
        }
        return "age_51_plus";
    }

    private static String incomeBinLabel(double income) {
        if (income <= 5000) {
            return "income_below_5000";
        }
        if (income <= 15000) {
            return "income_5000_15000";
        }
        return "income_15000_plus";
    }

    private static String multiHeadBinLabel(int c) {
        if (c <= 3) {
            return "multi_head_0_3";
        }
        if (c <= 6) {
            return "multi_head_4_6";
        }
        return "multi_head_7_plus";
    }

    private static String creditQueryBinLabel(int c) {
        if (c <= 3) {
            return "credit_query_0_3";
        }
        if (c <= 8) {
            return "credit_query_4_8";
        }
        return "credit_query_9_plus";
    }

    private static String overdueBinLabel(int c) {
        if (c <= 0) {
            return "overdue_12m_0";
        }
        if (c <= 2) {
            return "overdue_12m_1_2";
        }
        return "overdue_12m_3_plus";
    }

    private static String dtiBinLabel(int dti) {
        if (dti <= 15) {
            return "dti_low";
        }
        if (dti <= 30) {
            return "dti_medium";
        }
        return "dti_high";
    }

    private int mapIncomeToValue(String income) {
        if ("3000以下".equals(income)) {
            return 2000;
        }
        if ("3000-8000".equals(income)) {
            return 5500;
        }
        if ("8000-15000".equals(income)) {
            return 11500;
        }
        if ("15000以上".equals(income)) {
            return 20000;
        }
        return 5500;
    }

    private static void putAgeBinDummies(Map<String, Double> m, int age) {
        String bin = ageBinLabel(age);
        m.put("age_bin_age_26_35", "age_26_35".equals(bin) ? 1.0 : 0.0);
        m.put("age_bin_age_36_50", "age_36_50".equals(bin) ? 1.0 : 0.0);
        m.put("age_bin_age_51_plus", "age_51_plus".equals(bin) ? 1.0 : 0.0);
    }

    private static void putIncomeBinDummies(Map<String, Double> m, double income) {
        String bin = incomeBinLabel(income);
        m.put("income_bin_income_5000_15000", "income_5000_15000".equals(bin) ? 1.0 : 0.0);
        m.put("income_bin_income_15000_plus", "income_15000_plus".equals(bin) ? 1.0 : 0.0);
    }

    private static void putMultiHeadBinDummies(Map<String, Double> m, int c) {
        String bin = multiHeadBinLabel(c);
        m.put("multi_head_bin_multi_head_4_6", "multi_head_4_6".equals(bin) ? 1.0 : 0.0);
        m.put("multi_head_bin_multi_head_7_plus", "multi_head_7_plus".equals(bin) ? 1.0 : 0.0);
    }

    private static void putCreditQueryBinDummies(Map<String, Double> m, int c) {
        String bin = creditQueryBinLabel(c);
        m.put("credit_query_bin_credit_query_4_8", "credit_query_4_8".equals(bin) ? 1.0 : 0.0);
        m.put("credit_query_bin_credit_query_9_plus", "credit_query_9_plus".equals(bin) ? 1.0 : 0.0);
    }

    private static void putOverdueBinDummies(Map<String, Double> m, int c) {
        String bin = overdueBinLabel(c);
        m.put("overdue_bin_overdue_12m_1_2", "overdue_12m_1_2".equals(bin) ? 1.0 : 0.0);
        m.put("overdue_bin_overdue_12m_3_plus", "overdue_12m_3_plus".equals(bin) ? 1.0 : 0.0);
    }

    private static void putDtiBinDummies(Map<String, Double> m, int dti) {
        String bin = dtiBinLabel(dti);
        m.put("dti_bin_dti_medium", "dti_medium".equals(bin) ? 1.0 : 0.0);
        m.put("dti_bin_dti_high", "dti_high".equals(bin) ? 1.0 : 0.0);
    }

    /**
     * 与 train_scoring_model 中 edu_tier 三档对齐：系数来自 LC grade 分档训练，
     * 此处将中文申请表枚举映射到同一 low/mid/high（见 scoring_rules.feature_derivation）。
     */
    private static String mapEducationToTier(String education) {
        if (education == null || education.isEmpty()) {
            return "low";
        }
        if ("博士".equals(education) || "硕士".equals(education)) {
            return "high";
        }
        if ("本科".equals(education)) {
            return "mid";
        }
        return "low";
    }

    private static void putEduTierDummies(Map<String, Double> m, String education) {
        String tier = mapEducationToTier(education);
        m.put("edu_tier_mid", "mid".equals(tier) ? 1.0 : 0.0);
        m.put("edu_tier_high", "high".equals(tier) ? 1.0 : 0.0);
    }

    /** 与 LC emp_length 代理一致：公务员 / 企事业单位视为稳定就业 */
    private static double jobStableFromJobType(String jobType) {
        if (jobType == null) {
            return 0.0;
        }
        if ("公务员".equals(jobType) || "企事业单位".equals(jobType)) {
            return 1.0;
        }
        return 0.0;
    }

    private boolean isHcWoeModel(FullRuleConfig cfg) {
        if (cfg.ruleRoot == null || !cfg.ruleRoot.has("model_type")) {
            return false;
        }
        return "hc_woe_lr".equals(cfg.ruleRoot.get("model_type").asText());
    }

    private boolean isHcStandardizedModel(FullRuleConfig cfg) {
        if (isHcWoeModel(cfg)) {
            return false;
        }
        if (cfg.ruleRoot == null || !cfg.ruleRoot.has("model_type")) {
            return false;
        }
        return cfg.ruleRoot.get("model_type").asText().startsWith("hc_");
    }

    @Override
    public boolean hasExternalFeaturesForScoring(String idCard) {
        return hasUsableThirdPartyProfile(resolveExternalFeatures(idCard));
    }

    /**
     * HC 评分要求第三方表有有效行：ext_source 或征信查询/活跃贷款等非全空。
     */
    private boolean hasUsableThirdPartyProfile(UserExternalFeatures ext) {
        if (ext == null) {
            return false;
        }
        if (ext.getExtSource2() != null && ext.getExtSource2().doubleValue() > 1e-6) {
            return true;
        }
        if (ext.getExtSource3() != null && ext.getExtSource3().doubleValue() > 1e-6) {
            return true;
        }
        if (ext.getCreditBureauMon() != null && ext.getCreditBureauMon() > 0) {
            return true;
        }
        if (ext.getCreditBureauWeek() != null && ext.getCreditBureauWeek() > 0) {
            return true;
        }
        if (ext.getActiveLoansCount() != null && ext.getActiveLoansCount() > 0) {
            return true;
        }
        if (ext.getDaysEmployed() != null && ext.getDaysEmployed() != 0) {
            return true;
        }
        return false;
    }

    /** 按身份证号关联外部特征；兼容 sk_id_curr 纯数字联调 */
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

    /** 申请表 + 第三方征信原始特征（未标准化），键名与 train_scoring_model.HC_FEATURE_COLS 一致 */
    private Map<String, Double> buildHcRawFeatureMap(RiskAssessmentRequest request, UserExternalFeatures ext) {
        int birthYear = Integer.parseInt(request.getBirthday().substring(0, 4));
        int age = java.time.LocalDate.now().getYear() - birthYear;
        // 申请侧特征进入 LR；外部表 days_* / amt_income 仅用于验真（见 RiskAssessmentServiceImpl）
        double daysBirth = -age * 365.0;
        double daysEmployed = 0.0;
        double amtIncome = mapIncomeToValue(request.getMonthlyIncome()) * 12.0;

        Map<String, Double> m = new LinkedHashMap<>();
        m.put("days_birth", daysBirth);
        m.put("days_employed", daysEmployed);
        m.put("amt_income_total", amtIncome);
        m.put("ext_source_2", ext != null && ext.getExtSource2() != null ? ext.getExtSource2().doubleValue() : 0.0);
        m.put("ext_source_3", ext != null && ext.getExtSource3() != null ? ext.getExtSource3().doubleValue() : 0.0);
        m.put("amt_req_credit_bureau_mon",
                ext != null && ext.getCreditBureauMon() != null ? ext.getCreditBureauMon().doubleValue() : 0.0);
        m.put("amt_req_credit_bureau_week",
                ext != null && ext.getCreditBureauWeek() != null ? ext.getCreditBureauWeek().doubleValue() : 0.0);
        m.put("active_loans_count",
                ext != null && ext.getActiveLoansCount() != null ? ext.getActiveLoansCount().doubleValue() : 0.0);
        return m;
    }

    /** WOE 模型：申请表 + 第三方原始值（键与 scoring_rules.features 一致） */
    private Map<String, Double> buildHcWoeRawFeatureMap(RiskAssessmentRequest request, UserExternalFeatures ext) {
        Map<String, Double> m = new LinkedHashMap<>();
        int birthYear = Integer.parseInt(request.getBirthday().substring(0, 4));
        int age = java.time.LocalDate.now().getYear() - birthYear;

        m.put("gender_male", request.getGender() != null && request.getGender() == 1 ? 1.0 : 0.0);
        m.put("married", "已婚".equals(request.getMarriage()) ? 1.0 : 0.0);
        if (Boolean.TRUE.equals(request.getHasCar())) {
            m.put("own_car", 1.0);
        } else if (ext != null && ext.getFlagOwnCar() != null) {
            m.put("own_car", ext.getFlagOwnCar().doubleValue());
        } else {
            m.put("own_car", 0.0);
        }
        if (Boolean.TRUE.equals(request.getHasHouse())) {
            m.put("own_realty", 1.0);
        } else if (ext != null && ext.getOwnRealty() != null) {
            m.put("own_realty", ext.getOwnRealty().doubleValue());
        } else {
            m.put("own_realty", 0.0);
        }
        String tier = mapEducationToTier(request.getEducation());
        m.put("edu_high", "high".equals(tier) ? 1.0 : 0.0);
        m.put("edu_mid", "mid".equals(tier) ? 1.0 : 0.0);
        if (jobStableFromJobType(request.getJobType()) > 0.5) {
            m.put("employment_stable", 1.0);
        } else if (ext != null && ext.getEmploymentStable() != null) {
            m.put("employment_stable", ext.getEmploymentStable().doubleValue());
        } else if (ext != null && ext.getDaysEmployed() != null && ext.getDaysEmployed() < -365) {
            m.put("employment_stable", 1.0);
        } else {
            m.put("employment_stable", 0.0);
        }
        m.put("age_years", (double) age);
        m.put("amt_income_total", mapIncomeToValue(request.getMonthlyIncome()) * 12.0);
        m.put("credit_inquiry_1m",
                ext != null && ext.getCreditBureauMon() != null ? ext.getCreditBureauMon().doubleValue() : null);
        m.put("credit_inquiry_week",
                ext != null && ext.getCreditBureauWeek() != null ? ext.getCreditBureauWeek().doubleValue() : null);
        m.put("ext_source_2", ext != null && ext.getExtSource2() != null ? ext.getExtSource2().doubleValue() : null);
        m.put("ext_source_3", ext != null && ext.getExtSource3() != null ? ext.getExtSource3().doubleValue() : null);
        m.put("active_loans_count",
                ext != null && ext.getActiveLoansCount() != null ? ext.getActiveLoansCount().doubleValue() : null);
        m.put("credit_income_ratio",
                ext != null && ext.getCreditIncomeRatio() != null ? ext.getCreditIncomeRatio().doubleValue() : null);
        m.put("cc_utilization",
                ext != null && ext.getCcUtilization() != null ? ext.getCcUtilization().doubleValue() : null);
        m.put("loan_overdue_max_6m",
                ext != null && ext.getLoanOverdueMax6m() != null ? ext.getLoanOverdueMax6m().doubleValue() : null);
        m.put("phone_change_days",
                ext != null && ext.getDaysLastPhoneChange() != null
                        ? Math.abs(ext.getDaysLastPhoneChange().doubleValue())
                        : null);
        m.put("prev_refused_count",
                ext != null && ext.getPrevRefusedCount() != null ? ext.getPrevRefusedCount().doubleValue() : 0.0);
        return m;
    }

    private double woeLookup(Double raw, JsonNode featDef) {
        if (featDef == null || !featDef.isObject()) {
            return 0.0;
        }
        if (raw == null || raw.isNaN()) {
            return featDef.path("missing_bin").path("woe").asDouble(0.0);
        }
        String type = featDef.path("type").asText("numeric");
        if ("categorical".equals(type)) {
            int key = raw >= 0.5 ? 1 : 0;
            JsonNode cat = featDef.path("categories").path(String.valueOf(key));
            if (cat.isObject() && cat.has("woe")) {
                return cat.get("woe").asDouble();
            }
            return featDef.path("missing_bin").path("woe").asDouble(0.0);
        }
        JsonNode cuts = featDef.get("cuts");
        JsonNode woeArr = featDef.get("woe");
        if (cuts == null || !cuts.isArray() || woeArr == null || !woeArr.isArray()) {
            return 0.0;
        }
        double x = raw;
        for (int i = 0; i < cuts.size(); i++) {
            if (x <= cuts.get(i).asDouble()) {
                return woeArr.get(Math.min(i, woeArr.size() - 1)).asDouble();
            }
        }
        return woeArr.get(woeArr.size() - 1).asDouble();
    }

    private Pair<Double, List<ScoreContribution>> calculateHcWoeScorecard(
            RiskAssessmentRequest request, FullRuleConfig cfg) {
        UserExternalFeatures ext = resolveExternalFeatures(request.getIdCard());
        Map<String, Double> rawValues = buildHcWoeRawFeatureMap(request, ext);
        JsonNode features = cfg.ruleRoot.get("features");
        JsonNode coefs = cfg.ruleRoot.has("coefficients")
                ? cfg.ruleRoot.get("coefficients")
                : cfg.ruleRoot.get("feature_weights");
        double intercept = readIntercept(cfg);

        List<ScoreContribution> contributions = new ArrayList<>();
        contributions.add(new ScoreContribution(
                "intercept", "1.0", intercept, intercept, "基础评分"));

        double linear = intercept;
        if (coefs != null && coefs.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = coefs.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                double w = e.getValue().asDouble();
                Double raw = rawValues.get(key);
                JsonNode featDef = features != null ? features.get(key) : null;
                double woeVal = woeLookup(raw, featDef);
                double contrib = w * woeVal;
                linear += contrib;

                String description = describeLogisticFeature(key, woeVal);
                if (description == null) {
                    continue;
                }
                String valStr = raw == null ? "缺失" : String.format("%.6g", raw);
                contributions.add(new ScoreContribution(
                        key, valStr + "→WOE=" + String.format("%.4f", woeVal), w, contrib, description));
            }
        }

        double prob = sigmoid(linear);
        JsonNode scNode = cfg.ruleRoot.get("scorecard");
        double lrScore = scorecardFromProb(prob, scNode);
        double total = applyApplicationRuleBonus(request, cfg.ruleRoot, scNode, lrScore, contributions);
        return new Pair<>(total, contributions);
    }

    private double scaleHcFeature(String key, double raw, JsonNode scalerRoot) {
        if (scalerRoot == null || !scalerRoot.has(key)) {
            return raw;
        }
        JsonNode s = scalerRoot.get(key);
        double mean = s.path("mean").asDouble(0.0);
        double scale = s.path("scale").asDouble(1.0);
        if (Math.abs(scale) < 1e-9) {
            scale = 1.0;
        }
        return (raw - mean) / scale;
    }

    private Map<String, Double> buildHcScaledFeatureMap(
            Map<String, Double> raw, JsonNode scalerRoot) {
        Map<String, Double> scaled = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            scaled.put(e.getKey(), scaleHcFeature(e.getKey(), e.getValue(), scalerRoot));
        }
        return scaled;
    }

    /** 旧版 LC 分箱特征（仅当规则非 HC 时使用） */
    private Map<String, Double> buildLegacyLcFeatureMap(RiskAssessmentRequest request, UserExternalFeatures ext) {
        int birthYear = Integer.parseInt(request.getBirthday().substring(0, 4));
        int age = java.time.LocalDate.now().getYear() - birthYear;
        double income = mapIncomeToValue(request.getMonthlyIncome());
        int multiHead = ext != null && ext.getActiveLoansCount() != null ? ext.getActiveLoansCount() : 0;
        int creditQuery = ext != null && ext.getCreditBureauMon() != null ? ext.getCreditBureauMon() : 0;
        int overdue = 0;
        int dti = 20;

        Map<String, Double> m = new LinkedHashMap<>();
        m.put("age", (double) age);
        m.put("income", income);
        m.put("multi_head_loan_count", (double) multiHead);
        m.put("credit_query_count_3m", (double) creditQuery);
        m.put("overdue_count_12m", (double) overdue);
        m.put("dti", (double) dti);
        m.put("device_is_virtual", 0.0);
        m.put("ip_is_proxy", 0.0);
        putAgeBinDummies(m, age);
        putIncomeBinDummies(m, income);
        putMultiHeadBinDummies(m, multiHead);
        putCreditQueryBinDummies(m, creditQuery);
        putOverdueBinDummies(m, overdue);
        putDtiBinDummies(m, dti);
        putEduTierDummies(m, request.getEducation());
        m.put("house_owner", Boolean.TRUE.equals(request.getHasHouse()) ? 1.0 : 0.0);
        m.put("job_stable", jobStableFromJobType(request.getJobType()));
        m.put("has_car_stated", Boolean.TRUE.equals(request.getHasCar()) ? 1.0 : 0.0);
        m.put("marriage_married", "已婚".equals(request.getMarriage()) ? 1.0 : 0.0);
        return m;
    }

    private Map<String, Double> buildLogisticFeatureMap(
            RiskAssessmentRequest request, UserExternalFeatures ext, FullRuleConfig cfg) {
        if (isHcStandardizedModel(cfg)) {
            return buildHcScaledFeatureMap(
                    buildHcRawFeatureMap(request, ext),
                    cfg.ruleRoot.get("feature_scaler"));
        }
        return buildLegacyLcFeatureMap(request, ext);
    }

    private String describeLogisticFeature(String key, double value) {
        if ("intercept".equals(key)) {
            return "基础评分";
        }
        if ("age".equals(key)) {
            return "年龄";
        }
        if ("income".equals(key)) {
            return "月收入";
        }
        if ("multi_head_loan_count".equals(key)) {
            return "多头借贷平台数";
        }
        if ("credit_query_count_3m".equals(key)) {
            return "近3月征信查询次数";
        }
        if ("overdue_count_12m".equals(key)) {
            return "近12月逾期次数";
        }
        if ("dti".equals(key)) {
            return "负债收入比";
        }
        if ("device_is_virtual".equals(key)) {
            return value > 0.5 ? "使用虚拟设备，风险较高" : "设备正常";
        }
        if ("ip_is_proxy".equals(key)) {
            return value > 0.5 ? "使用代理IP，风险较高" : "IP正常";
        }
        if (key.startsWith("age_bin_")) {
            if (!key.contains("age_0_25") && value > 0.5) {
                if (key.contains("age_26_35")) return "年龄处于黄金期(26-35岁)";
                if (key.contains("age_36_50")) return "年龄处于稳定期(36-50岁)";
                if (key.contains("age_51_plus")) return "年龄较大(51岁以上)";
            }
            return null;
        }
        if (key.startsWith("income_bin_")) {
            if (value > 0.5) {
                if (key.contains("income_below_5000")) return "收入较低(5000以下)";
                if (key.contains("income_5000_15000")) return "收入中等(5000-15000)";
                if (key.contains("income_15000_plus")) return "收入较高(15000以上)";
            }
            return null;
        }
        if (key.startsWith("multi_head_bin_")) {
            if (value > 0.5) {
                if (key.contains("multi_head_4_6")) return "多头借贷4-6家，风险偏高";
                if (key.contains("multi_head_7_plus")) return "多头借贷7家以上，风险较高";
            }
            return null;
        }
        if (key.startsWith("credit_query_bin_")) {
            if (value > 0.5) {
                if (key.contains("credit_query_4_8")) return "近3月征信查询4-8次，较为频繁";
                if (key.contains("credit_query_9_plus")) return "近3月征信查询9次以上，异常频繁";
            }
            return null;
        }
        if (key.startsWith("overdue_bin_")) {
            if (value > 0.5) {
                if (key.contains("overdue_12m_1_2")) return "近12月有1-2次逾期记录";
                if (key.contains("overdue_12m_3_plus")) return "近12月逾期3次以上，风险较高";
            }
            return null;
        }
        if (key.startsWith("dti_bin_")) {
            if (value > 0.5) {
                if (key.contains("dti_medium")) return "负债收入比适中";
                if (key.contains("dti_high")) return "负债收入比较高，风险偏高";
            }
            return null;
        }
        if ("edu_tier_mid".equals(key)) {
            return value > 0.5 ? "本科学历" : null;
        }
        if ("edu_tier_high".equals(key)) {
            return value > 0.5 ? "硕士及以上学历" : null;
        }
        if ("house_owner".equals(key)) {
            return value > 0.5 ? "有房产" : null;
        }
        if ("job_stable".equals(key)) {
            return value > 0.5 ? "稳定职业(公务员/企事业单位)" : null;
        }
        if ("has_car_stated".equals(key)) {
            return value > 0.5 ? "有车产" : null;
        }
        if ("marriage_married".equals(key)) {
            return value > 0.5 ? "已婚" : null;
        }
        if ("days_birth".equals(key)) {
            return "出生天数(第三方/申请对齐)";
        }
        if ("days_employed".equals(key)) {
            return "入职天数(第三方)";
        }
        if ("amt_income_total".equals(key)) {
            return "年总收入";
        }
        if ("ext_source_2".equals(key)) {
            return "第三方权威评分A";
        }
        if ("ext_source_3".equals(key)) {
            return "第三方权威评分B";
        }
        if ("amt_req_credit_bureau_mon".equals(key)) {
            return "近1月征信查询(第三方)";
        }
        if ("amt_req_credit_bureau_week".equals(key)) {
            return "近1周征信查询(第三方)";
        }
        if ("active_loans_count".equals(key)) {
            return "活跃贷款数(第三方)";
        }
        if ("gender_male".equals(key)) {
            return "性别(男)";
        }
        if ("married".equals(key)) {
            return "婚姻(已婚)";
        }
        if ("own_car".equals(key)) {
            return "有车";
        }
        if ("own_realty".equals(key)) {
            return "有房";
        }
        if ("edu_high".equals(key)) {
            return "高学历";
        }
        if ("edu_mid".equals(key)) {
            return "本科学历";
        }
        if ("employment_stable".equals(key)) {
            return "稳定就业";
        }
        if ("age_years".equals(key)) {
            return "年龄";
        }
        if ("credit_inquiry_1m".equals(key)) {
            return "近1月征信查询";
        }
        if ("credit_income_ratio".equals(key)) {
            return "授信收入比";
        }
        if ("cc_utilization".equals(key)) {
            return "信用卡使用(代理)";
        }
        if ("loan_overdue_max_6m".equals(key)) {
            return "历史逾期(代理)";
        }
        if ("phone_change_days".equals(key)) {
            return "手机稳定性";
        }
        if ("prev_refused_count".equals(key)) {
            return "历史被拒次数";
        }
        if (key.startsWith("rule_bonus_")) {
            return null;
        }
        return key;
    }

    private Pair<Double, List<ScoreContribution>> calculateLogisticScorecard(
            RiskAssessmentRequest request, FullRuleConfig cfg) {
        if (isHcWoeModel(cfg)) {
            return calculateHcWoeScorecard(request, cfg);
        }
        UserExternalFeatures ext = resolveExternalFeatures(request.getIdCard());
        Map<String, Double> rawValues = isHcStandardizedModel(cfg)
                ? buildHcRawFeatureMap(request, ext)
                : null;
        Map<String, Double> values = buildLogisticFeatureMap(request, ext, cfg);
        double intercept = readIntercept(cfg);
        JsonNode fw = getLrCoefficientsNode(cfg.ruleRoot);
        if (fw == null) {
            return calculateLegacyScoreWithDetails(request, cfg);
        }

        List<ScoreContribution> contributions = new ArrayList<>();
        contributions.add(new ScoreContribution(
                "intercept", "1.0", intercept, intercept, "基础评分"));

        double linear = intercept;
        Iterator<Map.Entry<String, JsonNode>> it = fw.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            double w = e.getValue().asDouble();
            double v = values.getOrDefault(key, 0.0);
            double contrib = w * v;
            linear += contrib;

            String description = describeLogisticFeature(key, v);
            if (description == null) {
                continue;
            }

            double displayVal = rawValues != null && rawValues.containsKey(key)
                    ? rawValues.get(key)
                    : v;
            String valStr = (displayVal == (long) displayVal)
                    ? String.valueOf((long) displayVal)
                    : String.format("%.6g", displayVal);
            contributions.add(new ScoreContribution(key, valStr, w, contrib, description));
        }

        double prob = sigmoid(linear);
        JsonNode scNode = cfg.ruleRoot.get("scorecard");
        double lrScore = scorecardFromProb(prob, scNode);
        double total = applyApplicationRuleBonus(request, cfg.ruleRoot, scNode, lrScore, contributions);
        return new Pair<>(total, contributions);
    }

    private static Integer tryComputeAgeFromBirthday(String birthday) {
        if (birthday == null || birthday.length() < 4) {
            return null;
        }
        try {
            int birthYear = Integer.parseInt(birthday.substring(0, 4));
            return java.time.LocalDate.now().getYear() - birthYear;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * LR 映射分之上叠加 application_rule_bonus（婚姻/车/年龄）；加成受 cap_absolute_sum 限制后再夹紧到 scorecard 区间。
     */
    private double applyApplicationRuleBonus(
            RiskAssessmentRequest request,
            JsonNode ruleRoot,
            JsonNode scorecardNode,
            double lrScore,
            List<ScoreContribution> contributions) {
        if (ruleRoot == null || !ruleRoot.has("application_rule_bonus")) {
            return lrScore;
        }
        JsonNode arb = ruleRoot.get("application_rule_bonus");
        if (!arb.has("enabled") || !arb.get("enabled").asBoolean()) {
            return lrScore;
        }

        double bonusSum = 0.0;
        int cap = arb.has("cap_absolute_sum") ? arb.get("cap_absolute_sum").asInt(55) : 55;

        if (arb.has("married_equals_bonus") && arb.get("married_equals_bonus").isObject()) {
            JsonNode m = arb.get("married_equals_bonus");
            String match = m.path("match").asText("已婚");
            double b = m.path("bonus").asDouble(0.0);
            if (match.equals(request.getMarriage())) {
                bonusSum += b;
                contributions.add(new ScoreContribution(
                        "rule_bonus_marriage",
                        request.getMarriage(),
                        b,
                        b,
                        "申请表规则加成：婚姻匹配「" + match + "」（弥补 LR 侧婚姻权重常为 0）"));
            }
        }

        if (arb.has("has_car_bonus") && Boolean.TRUE.equals(request.getHasCar())) {
            double b = arb.get("has_car_bonus").asDouble(0.0);
            bonusSum += b;
            contributions.add(new ScoreContribution(
                    "rule_bonus_car",
                    "true",
                    b,
                    b,
                    "申请表规则加成：有车（弥补 LR 侧车字段在 LC 常为常数）"));
        }

        if (arb.has("age_rules") && arb.get("age_rules").isArray()) {
            Integer age = tryComputeAgeFromBirthday(request.getBirthday());
            if (age != null) {
                for (JsonNode rule : arb.get("age_rules")) {
                    if (!ageMatchesAgeRule(age, rule)) {
                        continue;
                    }
                    double b = rule.path("bonus").asDouble(0.0);
                    String reason = rule.path("reason").asText("年龄规则");
                    bonusSum += b;
                    contributions.add(new ScoreContribution(
                            "rule_bonus_age",
                            String.valueOf(age),
                            b,
                            b,
                            "申请表规则加成：" + reason + "（低龄违约率偏高、随年龄缓和）"));
                    break;
                }
            }
        }

        bonusSum = Math.max(-cap, Math.min(cap, bonusSum));

        int minS = 350;
        int maxS = 950;
        if (scorecardNode != null && scorecardNode.isObject()) {
            if (scorecardNode.has("min_score")) {
                minS = scorecardNode.get("min_score").asInt();
            }
            if (scorecardNode.has("max_score")) {
                maxS = scorecardNode.get("max_score").asInt();
            }
        }
        double combined = lrScore + bonusSum;
        combined = Math.max(minS, Math.min(maxS, combined));
        contributions.add(new ScoreContribution(
                "rule_bonus_total",
                String.format("%.2f", bonusSum),
                1.0,
                bonusSum,
                "规则加成合计（已 cap），LR 基础分=" + String.format("%.2f", lrScore)));
        return combined;
    }

    private static boolean ageMatchesAgeRule(int age, JsonNode rule) {
        if (rule.has("lte") && age > rule.get("lte").asInt()) {
            return false;
        }
        if (rule.has("gte") && age < rule.get("gte").asInt()) {
            return false;
        }
        return true;
    }

    private Pair<Double, List<ScoreContribution>> calculateScoreWithDetails(RiskAssessmentRequest request) {
        FullRuleConfig cfg = loadFullRuleConfig();
        if (hasFeatureWeights(cfg)) {
            if ((isHcStandardizedModel(cfg) || isHcWoeModel(cfg))
                    && !hasExternalFeaturesForScoring(request.getIdCard())) {
                throw new IllegalStateException(
                        "THIRD_PARTY_MISSING: 未找到第三方征信快照，请确认 id_card 已写入 credit_data_db.user_external_features");
            }
            return calculateLogisticScorecard(request, cfg);
        }
        String version = cfg.rulesEntity != null && cfg.rulesEntity.getVersion() != null
                ? cfg.rulesEntity.getVersion()
                : "unknown";
        log.warn(
                "Active rule version={} but no LR coefficients found; falling back to legacy scorecard (100-base, max 90)",
                version);
        return calculateLegacyScoreWithDetails(request, cfg);
    }

    @Override
    public double calculateScore(RiskAssessmentRequest request) {
        return calculateScoreWithDetails(request).getLeft();
    }

    @Override
    public String getDecision(double score) {
        FullRuleConfig cfg = loadFullRuleConfig();
        if (score >= cfg.autoApproveThreshold) {
            return "APPROVE";
        }
        if (score >= cfg.manualReviewThreshold) {
            return "MANUAL_REVIEW";
        }
        return "REJECT";
    }

    private int getScoreFromGroups(int value, List<Map<String, Object>> groups) {
        for (Map<String, Object> group : groups) {
            @SuppressWarnings("unchecked")
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

    /** 无 feature_weights 时的旧版规则（基础分 100 + 分项 + clamp） */
    private Pair<Double, List<ScoreContribution>> calculateLegacyScoreWithDetails(
            RiskAssessmentRequest request, FullRuleConfig cfg) {
        int score = 100;
        List<ScoreContribution> contributions = new ArrayList<>();
        contributions.add(new ScoreContribution("基础分", "基础分", 100.0, 100.0, "信用评估基础分"));

        Map<String, Object> featureScores = cfg.featureScores;

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

        UserExternalFeatures externalFeatures = resolveExternalFeatures(request.getIdCard());
        if (externalFeatures != null) {
            Integer activeLoans = externalFeatures.getActiveLoansCount();
            int multiHeadScore = calculateMultiHeadScore(activeLoans, featureScores);
            score += multiHeadScore;
            contributions.add(new ScoreContribution("活跃贷款数",
                    String.valueOf(activeLoans),
                    multiHeadScore, multiHeadScore,
                    getMultiHeadReason(activeLoans, featureScores)));

            Integer creditBureauMon = externalFeatures.getCreditBureauMon();
            int queryScore = calculateQueryScore(creditBureauMon, featureScores);
            score += queryScore;
            contributions.add(new ScoreContribution("近1月征信查询",
                    String.valueOf(creditBureauMon),
                    queryScore, queryScore,
                    getQueryReason(creditBureauMon, featureScores)));

            Integer target = externalFeatures.getTarget();
            int overdueScore = calculateOverdueScore(target, featureScores);
            score += overdueScore;
            contributions.add(new ScoreContribution("历史标签",
                    String.valueOf(target),
                    overdueScore, overdueScore,
                    getOverdueReason(target, featureScores)));

            int prevRefused = externalFeatures.getPrevRefusedCount() != null ? externalFeatures.getPrevRefusedCount() : 0;
            if (prevRefused > 0) {
                score -= prevRefused * 5;
                contributions.add(new ScoreContribution("历史被拒次数",
                        String.valueOf(prevRefused),
                        (double)(-prevRefused * 5), (double)(-prevRefused * 5),
                        "历史被拒" + prevRefused + "次"));
            }
        }

        score = Math.max(10, Math.min(90, score));
        return new Pair<>((double) score, contributions);
    }

    private int calculateAgeScore(int age, Map<String, Object> featureScores) {
        if (featureScores == null) {
            if (age < 22) {
                return -10;
            }
            if (age < 25) {
                return -10;
            }
            if (age < 30) {
                return -5;
            }
            if (age < 40) {
                return 8;
            }
            if (age < 50) {
                return 5;
            }
            return -3;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> ageConfig = (Map<String, Object>) featureScores.get("age");
        if (ageConfig == null) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) ageConfig.get("groups");
        return getScoreFromGroups(age, groups);
    }

    private String getAgeReason(int age, Map<String, Object> featureScores) {
        if (featureScores == null) {
            if (age < 25) {
                return "年龄较小";
            }
            if (age < 30) {
                return "青年时期";
            }
            if (age < 40) {
                return "黄金年龄段";
            }
            if (age < 50) {
                return "稳定年龄段";
            }
            return "年龄较大";
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> ageConfig = (Map<String, Object>) featureScores.get("age");
        if (ageConfig == null) {
            return "";
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) ageConfig.get("groups");
        for (Map<String, Object> group : groups) {
            @SuppressWarnings("unchecked")
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
            if ("3000以下".equals(income)) {
                return -15;
            }
            if ("3000-8000".equals(income)) {
                return -5;
            }
            if ("8000-15000".equals(income)) {
                return 5;
            }
            if ("15000以上".equals(income)) {
                return 15;
            }
            return 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> incomeConfig = (Map<String, Object>) featureScores.get("income");
        if (incomeConfig == null) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) incomeConfig.get("groups");
        int incomeValue = mapIncomeToValue(income);
        return getScoreFromGroups(incomeValue, groups);
    }

    private String getIncomeReason(String income, Map<String, Object> featureScores) {
        if (featureScores == null) {
            if ("3000以下".equals(income)) {
                return "收入较低";
            }
            if ("3000-8000".equals(income)) {
                return "收入一般";
            }
            if ("8000-15000".equals(income)) {
                return "收入中等";
            }
            if ("15000以上".equals(income)) {
                return "收入较高";
            }
            return "";
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> incomeConfig = (Map<String, Object>) featureScores.get("income");
        if (incomeConfig == null) {
            return "";
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) incomeConfig.get("groups");
        int incomeValue = mapIncomeToValue(income);
        for (Map<String, Object> group : groups) {
            @SuppressWarnings("unchecked")
            List<Number> range = (List<Number>) group.get("range");
            double min = range.get(0).doubleValue();
            double max = range.get(1).doubleValue();
            if (incomeValue >= min && incomeValue < max) {
                return (String) group.get("reason");
            }
        }
        return "";
    }

    private int calculateJobScore(String jobType, Map<String, Object> featureScores) {
        if (featureScores == null) {
            if ("公务员".equals(jobType)) {
                return 15;
            }
            if ("企事业单位".equals(jobType)) {
                return 10;
            }
            if ("私营企业".equals(jobType)) {
                return 0;
            }
            return -5;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> jobConfig = (Map<String, Object>) featureScores.get("job_type");
        if (jobConfig == null) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> scores = (Map<String, Object>) jobConfig.get("scores");
        return getScoreFromDict(jobType, scores);
    }

    private String getJobReason(String jobType, Map<String, Object> featureScores) {
        if ("公务员".equals(jobType)) {
            return "最稳定工作";
        }
        if ("企事业单位".equals(jobType)) {
            return "稳定工作";
        }
        if ("私营企业".equals(jobType)) {
            return "一般工作";
        }
        return "工作不稳定";
    }

    private int calculateHouseScore(Boolean hasHouse, Map<String, Object> featureScores) {
        if (featureScores == null) {
            return Boolean.TRUE.equals(hasHouse) ? 10 : 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> houseConfig = (Map<String, Object>) featureScores.get("has_house");
        if (houseConfig == null) {
            return 0;
        }

        if (Boolean.TRUE.equals(hasHouse)) {
            return ((Number) houseConfig.get("has")).intValue();
        }
        return ((Number) houseConfig.get("none")).intValue();
    }

    private int calculateCarScore(Boolean hasCar, Map<String, Object> featureScores) {
        if (featureScores == null) {
            return Boolean.TRUE.equals(hasCar) ? 5 : 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> carConfig = (Map<String, Object>) featureScores.get("has_car");
        if (carConfig == null) {
            return 0;
        }

        if (Boolean.TRUE.equals(hasCar)) {
            return ((Number) carConfig.get("has")).intValue();
        }
        return ((Number) carConfig.get("none")).intValue();
    }

    private int calculateEducationScore(String education, Map<String, Object> featureScores) {
        if (featureScores == null) {
            if ("博士".equals(education)) {
                return 15;
            }
            if ("硕士".equals(education)) {
                return 10;
            }
            if ("本科".equals(education)) {
                return 5;
            }
            return -5;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> eduConfig = (Map<String, Object>) featureScores.get("education");
        if (eduConfig == null) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> scores = (Map<String, Object>) eduConfig.get("scores");
        return getScoreFromDict(education, scores);
    }

    private String getEducationReason(String education, Map<String, Object> featureScores) {
        if ("博士".equals(education)) {
            return "高学历";
        }
        if ("硕士".equals(education)) {
            return "较高学历";
        }
        if ("本科".equals(education)) {
            return "本科学历";
        }
        return "学历较低";
    }

    private int calculateMarriageScore(String marriage, Map<String, Object> featureScores) {
        if (featureScores == null) {
            return "已婚".equals(marriage) ? 5 : 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> marriageConfig = (Map<String, Object>) featureScores.get("marriage");
        if (marriageConfig == null) {
            return 0;
        }

        if ("已婚".equals(marriage)) {
            return ((Number) marriageConfig.get("married")).intValue();
        }
        return ((Number) marriageConfig.get("single")).intValue();
    }

    private int calculateMultiHeadScore(Integer count, Map<String, Object> featureScores) {
        if (count == null) {
            return 0;
        }

        if (featureScores == null) {
            if (count >= 10) {
                return -30;
            }
            if (count >= 6) {
                return -20;
            }
            if (count >= 3) {
                return -10;
            }
            return 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) featureScores.get("multi_head_loan_count");
        if (config == null) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        return getScoreFromGroups(count, groups);
    }

    private String getMultiHeadReason(Integer count, Map<String, Object> featureScores) {
        if (count == null) {
            return "";
        }

        if (featureScores == null) {
            if (count >= 10) {
                return "极高多头";
            }
            if (count >= 6) {
                return "高多头";
            }
            if (count >= 3) {
                return "中等多头";
            }
            return "正常";
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) featureScores.get("multi_head_loan_count");
        if (config == null) {
            return "";
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        for (Map<String, Object> group : groups) {
            @SuppressWarnings("unchecked")
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
        if (count == null) {
            return 0;
        }

        if (featureScores == null) {
            if (count >= 10) {
                return -15;
            }
            if (count >= 6) {
                return -10;
            }
            if (count >= 3) {
                return -5;
            }
            return 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) featureScores.get("credit_query_count_3m");
        if (config == null) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        return getScoreFromGroups(count, groups);
    }

    private String getQueryReason(Integer count, Map<String, Object> featureScores) {
        if (count == null) {
            return "";
        }

        if (featureScores == null) {
            if (count >= 10) {
                return "异常频繁";
            }
            if (count >= 6) {
                return "查询频繁";
            }
            if (count >= 3) {
                return "查询较多";
            }
            return "正常";
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) featureScores.get("credit_query_count_3m");
        if (config == null) {
            return "";
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        for (Map<String, Object> group : groups) {
            @SuppressWarnings("unchecked")
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
        if (count == null) {
            return 0;
        }

        if (featureScores == null) {
            if (count >= 3) {
                return -40;
            }
            if (count >= 2) {
                return -25;
            }
            if (count >= 1) {
                return -15;
            }
            return 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) featureScores.get("overdue_count_12m");
        if (config == null) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        return getScoreFromGroups(count, groups);
    }

    private String getOverdueReason(Integer count, Map<String, Object> featureScores) {
        if (count == null) {
            return "";
        }

        if (featureScores == null) {
            if (count >= 3) {
                return "严重逾期";
            }
            if (count >= 2) {
                return "多次逾期";
            }
            if (count >= 1) {
                return "有逾期记录";
            }
            return "无逾期";
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) featureScores.get("overdue_count_12m");
        if (config == null) {
            return "";
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        for (Map<String, Object> group : groups) {
            @SuppressWarnings("unchecked")
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
        if (dti == null) {
            return 0;
        }

        if (featureScores == null) {
            if (dti >= 30) {
                return -15;
            }
            if (dti >= 15) {
                return 0;
            }
            return 5;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) featureScores.get("dti");
        if (config == null) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        return getScoreFromGroups(dti, groups);
    }

    private String getDtiReason(Integer dti, Map<String, Object> featureScores) {
        if (dti == null) {
            return "";
        }

        if (featureScores == null) {
            if (dti >= 30) {
                return "负债较高";
            }
            if (dti >= 15) {
                return "负债正常";
            }
            return "负债低";
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) featureScores.get("dti");
        if (config == null) {
            return "";
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
        for (Map<String, Object> group : groups) {
            @SuppressWarnings("unchecked")
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
        if (score == null) {
            return 0;
        }
        if (score >= 800) {
            return 10;
        }
        if (score >= 700) {
            return 5;
        }
        if (score < 600) {
            return -10;
        }
        return 0;
    }

    private String getExternalCreditReason(Integer score) {
        if (score == null) {
            return "";
        }
        if (score >= 800) {
            return "外部信用非常优秀";
        }
        if (score >= 700) {
            return "外部信用良好";
        }
        if (score < 600) {
            return "外部信用较差";
        }
        return "外部信用一般";
    }

    @Override
    public double calculateCreditLimit(double score, String monthlyIncome) {
        return calculateCreditLimitWithFeatures(score, monthlyIncome, null);
    }

    @Override
    public double calculateCreditLimitWithFeatures(double score, String monthlyIncome, String idCard) {
        int incomeValue = mapIncomeToValue(monthlyIncome);
        double limit;

        FullRuleConfig cfg = loadFullRuleConfig();
        if (score >= cfg.autoApproveThreshold) {
            limit = incomeValue * 12;
        } else if (score >= cfg.manualReviewThreshold) {
            limit = incomeValue * 10;
        } else {
            limit = incomeValue * 8;
        }

        if (idCard != null) {
            limit = adjustLimitByExternalFeatures(limit, idCard, incomeValue);
        }

        if (score >= cfg.autoApproveThreshold && limit > 500000) {
            limit = 500000;
        } else if (score >= cfg.manualReviewThreshold && score < cfg.autoApproveThreshold && limit > 300000) {
            limit = 300000;
        } else if (score < cfg.manualReviewThreshold && limit > 200000) {
            limit = 200000;
        }

        return Math.max(0, limit);
    }

    /**
     * 额度策略层：多头/第三方评分/查询次数已纳入 HC LR，此处仅保留规则类惩罚，避免双重计数。
     */
    private double adjustLimitByExternalFeatures(double baseLimit, String idCard, int incomeValue) {
        UserExternalFeatures features = resolveExternalFeatures(idCard);
        if (features == null) {
            return baseLimit;
        }

        double penaltyRate = 0.0;
        if (features.getPrevRefusedCount() != null && features.getPrevRefusedCount() > 0) {
            penaltyRate += features.getPrevRefusedCount() * 0.10;
        }
        penaltyRate = Math.min(penaltyRate, 0.50);
        return baseLimit * (1 - penaltyRate);
    }

    @Override
    public ScoreDetailReport getScoreDetailReport(RiskAssessmentRequest request) {
        Pair<Double, List<ScoreContribution>> result = calculateScoreWithDetails(request);
        double score = result.getLeft();
        List<ScoreContribution> contributions = result.getRight();

        String decision = getDecision(score);
        ExternalFeatures externalFeatures = getExternalFeatures(request.getIdCard());
        BlacklistCheck blacklistCheck = getBlacklistCheck(request.getIdCard());

        return new ScoreDetailReport(score, decision, contributions, externalFeatures, blacklistCheck);
    }

    private ExternalFeatures getExternalFeatures(String idCard) {
        UserExternalFeatures features = resolveExternalFeatures(idCard);
        ExternalFeatures result = new ExternalFeatures();

        if (features != null) {
            result.setDataSource(features.getDataSource());
            if (features.getUpdatedAt() != null) {
                result.setUpdatedAt(features.getUpdatedAt().toString());
            }
            if (features.getCreditBureauMon() != null) {
                result.setCreditQueryCount3m(features.getCreditBureauMon());
            }
            if (features.getActiveLoansCount() != null) {
                result.setMultiHeadLoanCount(features.getActiveLoansCount());
            }
            if (features.getExtSource2() != null) {
                result.setCreditScore(features.getExtSource2().multiply(BigDecimal.valueOf(1000)).intValue());
            }
            if (features.getTarget() != null) {
                result.setOverdueCount12m(features.getTarget());
            }
        }

        return result;
    }

    private BlacklistCheck getBlacklistCheck(String idCard) {
        String areaCode = extractAreaCodeFromIdCard(idCard);
        Integer birthYear = extractBirthYearFromIdCard(idCard);
        List<Blacklist> allBlacklist = blacklistMapper.selectAll();
        for (Blacklist record : allBlacklist) {
            if (areaCode != null && areaCode.equals(record.getAreaCode())) {
                if (birthYear != null && birthYear.equals(record.getBirthYear())) {
                    return new BlacklistCheck(true, "credit_data_db", "命中黑名单");
                }
            }
        }
        return new BlacklistCheck(false, null, null);
    }
}
