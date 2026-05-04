package org.example.risklendpro.service;

import org.example.risklendpro.pojo.request.RiskAssessmentRequest;

import java.util.List;
import java.util.Map;

/**
 * 风控评分引擎服务
 */
public interface CreditScoreEngine {

    /**
     * 检查用户是否在黑名单中
     */
    boolean isInBlacklist(String idCard);

    /**
     * 获取用户的风控评分
     */
    int calculateScore(RiskAssessmentRequest request);

    /**
     * 根据评分获取决策结果
     */
    String getDecision(int score);

    /**
     * 计算授信额度
     */
    double calculateCreditLimit(int score, String monthlyIncome);

    /**
     * 根据外部特征数据计算授信额度（参考Python分析数据）
     */
    double calculateCreditLimitWithFeatures(int score, String monthlyIncome, String idCard);

    /**
     * 获取详细的评分报告（用于管理员查看）
     */
    ScoreDetailReport getScoreDetailReport(RiskAssessmentRequest request);

    /**
     * 评分详情报告
     */
    class ScoreDetailReport {
        private int totalScore;
        private String decision;
        private List<ScoreContribution> scoreDetails;
        private ExternalFeatures externalFeatures;
        private BlacklistCheck blacklistCheck;

        public ScoreDetailReport(int totalScore, String decision, List<ScoreContribution> scoreDetails,
                                  ExternalFeatures externalFeatures, BlacklistCheck blacklistCheck) {
            this.totalScore = totalScore;
            this.decision = decision;
            this.scoreDetails = scoreDetails;
            this.externalFeatures = externalFeatures;
            this.blacklistCheck = blacklistCheck;
        }

        // Getters and Setters
        public int getTotalScore() { return totalScore; }
        public String getDecision() { return decision; }
        public List<ScoreContribution> getScoreDetails() { return scoreDetails; }
        public ExternalFeatures getExternalFeatures() { return externalFeatures; }
        public BlacklistCheck getBlacklistCheck() { return blacklistCheck; }
    }

    /**
     * 评分贡献明细
     */
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

    /**
     * 外部特征数据
     */
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

        // Getters and Setters
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

    /**
     * 黑名单检查结果
     */
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
