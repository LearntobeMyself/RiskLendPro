package org.example.risklendpro.service;

import org.example.risklendpro.entity.credit.Blacklist;
import org.example.risklendpro.pojo.request.RiskAssessmentRequest;

import java.util.List;

/**
 * 风控评分引擎服务
 */
public interface CreditScoreEngine {

    boolean isInBlacklist(String idCard);

    /**
     * 三级黑名单匹配检查
     * @param name 用户姓名
     * @param idCard 身份证号（用于提取地区编码和出生年份）
     * @return 匹配结果
     */
    BlacklistMatchResult checkBlacklist(String name, String idCard);

    /**
     * 风控总分：优先为 Python 逻辑回归 + 评分卡映射结果（连续分值，保留小数）
     */
    double calculateScore(RiskAssessmentRequest request);

    String getDecision(double score);

    double calculateCreditLimit(double score, String monthlyIncome);

    double calculateCreditLimitWithFeatures(double score, String monthlyIncome, String idCard);

    ScoreDetailReport getScoreDetailReport(RiskAssessmentRequest request);

    class ScoreDetailReport {
        private double totalScore;
        private String decision;
        private List<ScoreContribution> scoreDetails;
        private ExternalFeatures externalFeatures;
        private BlacklistCheck blacklistCheck;

        public ScoreDetailReport(double totalScore, String decision, List<ScoreContribution> scoreDetails,
                                  ExternalFeatures externalFeatures, BlacklistCheck blacklistCheck) {
            this.totalScore = totalScore;
            this.decision = decision;
            this.scoreDetails = scoreDetails;
            this.externalFeatures = externalFeatures;
            this.blacklistCheck = blacklistCheck;
        }

        public double getTotalScore() { return totalScore; }
        public String getDecision() { return decision; }
        public List<ScoreContribution> getScoreDetails() { return scoreDetails; }
        public ExternalFeatures getExternalFeatures() { return externalFeatures; }
        public BlacklistCheck getBlacklistCheck() { return blacklistCheck; }
    }

    class ScoreContribution {
        private String feature;
        private String value;
        private double weight;
        private double contribution;
        private String description;

        public ScoreContribution(String feature, String value, double weight, double contribution, String description) {
            this.feature = feature;
            this.value = value;
            this.weight = weight;
            this.contribution = contribution;
            this.description = description;
        }

        public String getFeature() { return feature; }
        public String getValue() { return value; }
        public double getWeight() { return weight; }
        public double getContribution() { return contribution; }
        public String getDescription() { return description; }
    }

    class ExternalFeatures {
        private Integer creditScore;
        private Integer overdueCount12m;
        private Integer creditQueryCount3m;
        private Integer multiHeadLoanCount;
        private java.math.BigDecimal multiHeadLoanTotalAmount;
        private Integer deviceIsVirtual;
        private Integer ipIsProxy;
        private String dataSource;
        private String updatedAt;

        public Integer getCreditScore() { return creditScore; }
        public void setCreditScore(Integer creditScore) { this.creditScore = creditScore; }
        public Integer getOverdueCount12m() { return overdueCount12m; }
        public void setOverdueCount12m(Integer overdueCount12m) { this.overdueCount12m = overdueCount12m; }
        public Integer getCreditQueryCount3m() { return creditQueryCount3m; }
        public void setCreditQueryCount3m(Integer creditQueryCount3m) { this.creditQueryCount3m = creditQueryCount3m; }
        public Integer getMultiHeadLoanCount() { return multiHeadLoanCount; }
        public void setMultiHeadLoanCount(Integer multiHeadLoanCount) { this.multiHeadLoanCount = multiHeadLoanCount; }
        public java.math.BigDecimal getMultiHeadLoanTotalAmount() { return multiHeadLoanTotalAmount; }
        public void setMultiHeadLoanTotalAmount(java.math.BigDecimal multiHeadLoanTotalAmount) { this.multiHeadLoanTotalAmount = multiHeadLoanTotalAmount; }
        public Integer getDeviceIsVirtual() { return deviceIsVirtual; }
        public void setDeviceIsVirtual(Integer deviceIsVirtual) { this.deviceIsVirtual = deviceIsVirtual; }
        public Integer getIpIsProxy() { return ipIsProxy; }
        public void setIpIsProxy(Integer ipIsProxy) { this.ipIsProxy = ipIsProxy; }
        public String getDataSource() { return dataSource; }
        public void setDataSource(String dataSource) { this.dataSource = dataSource; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }

    class BlacklistCheck {
        private boolean hit;
        private String source;
        private String reason;

        public BlacklistCheck(boolean hit, String source, String reason) {
            this.hit = hit;
            this.source = source;
            this.reason = reason;
        }

        public boolean isHit() { return hit; }
        public String getSource() { return source; }
        public String getReason() { return reason; }
    }

    /**
     * 黑名单匹配结果枚举
     */
    enum MatchLevel {
        NONE("未命中", "安全", "自动通过"),
        NAME_ONLY("仅姓名命中", "低风险", "通过/标记"),
        NAME_AREA("姓名+地域命中", "中风险", "人工审批"),
        FULL("姓名+地域+出生年份命中", "高风险", "直接拒绝");

        private final String description;
        private final String riskLevel;
        private final String action;

        MatchLevel(String description, String riskLevel, String action) {
            this.description = description;
            this.riskLevel = riskLevel;
            this.action = action;
        }

        public String getDescription() { return description; }
        public String getRiskLevel() { return riskLevel; }
        public String getAction() { return action; }
    }

    /**
     * 黑名单匹配结果
     */
    class BlacklistMatchResult {
        private MatchLevel matchLevel;
        private Blacklist matchedRecord;

        public BlacklistMatchResult(MatchLevel matchLevel, Blacklist matchedRecord) {
            this.matchLevel = matchLevel;
            this.matchedRecord = matchedRecord;
        }

        public MatchLevel getMatchLevel() { return matchLevel; }
        public Blacklist getMatchedRecord() { return matchedRecord; }
        
        public boolean isNeedManualReview() {
            return matchLevel == MatchLevel.NAME_AREA;
        }
        
        public boolean isReject() {
            return matchLevel == MatchLevel.FULL;
        }
    }
}
