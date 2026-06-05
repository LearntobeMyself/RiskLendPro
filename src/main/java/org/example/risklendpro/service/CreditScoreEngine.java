package org.example.risklendpro.service;

import org.example.risklendpro.entity.credit.Blacklist;
import org.example.risklendpro.pojo.request.RiskAssessmentRequest;

import java.util.List;

/**
 * 风控评分引擎服务
 */
public interface CreditScoreEngine {

    boolean isInBlacklist(String idCard);

    BlacklistMatchResult checkBlacklist(String name, String idCard);

    boolean hasExternalFeaturesForScoring(String idCard);

    enum MatchLevel {
        FULL("姓名+地域+出生年份命中"),
        NAME_AREA("姓名+地域命中"),
        NAME_ONLY("仅姓名命中"),
        NONE("未命中");

        private final String description;

        MatchLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    class BlacklistMatchResult {
        private final MatchLevel matchLevel;
        private final Blacklist record;

        public BlacklistMatchResult(MatchLevel matchLevel, Blacklist record) {
            this.matchLevel = matchLevel;
            this.record = record;
        }

        public MatchLevel getMatchLevel() {
            return matchLevel;
        }

        public Blacklist getRecord() {
            return record;
        }

        public boolean isReject() {
            return matchLevel == MatchLevel.FULL;
        }

        public boolean isNeedManualReview() {
            return matchLevel == MatchLevel.NAME_AREA;
        }
    }

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
}
