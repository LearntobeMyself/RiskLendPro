package org.example.risklendpro.entity.credit;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 风控评分规则实体类 - 从credit_data_db数据库读取
 * 对应表: scoring_rules
 */
@Data
public class ScoringRules {
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 规则版本号
     */
    private String version;
    
    /**
     * 完整规则JSON备份
     */
    private String ruleContent;
    
    /**
     * LR特征权重 rules.feature_weights
     */
    private String featureWeights;
    
    /**
     * 评分卡规则 rules.scorecard
     */
    private String scorecard;
    
    /**
     * 申请表策略加成规则
     */
    private String applicationRuleBonus;
    
    /**
     * 旧版逐项评分规则
     */
    private String featureScores;
    
    /**
     * 特征推导说明
     */
    private String featureDerivation;
    
    /**
     * 逻辑回归截距项
     */
    private BigDecimal intercept;
    
    /**
     * 自动通过阈值（PDO量表）
     */
    private BigDecimal thresholdAutoApprove;
    
    /**
     * 人工审核阈值（PDO量表）
     */
    private BigDecimal thresholdManualReview;
    
    /**
     * 是否激活（0=否，1=是）
     */
    private Integer isActive;
    
    /**
     * 模型训练时间
     */
    private Date trainedAt;
    
    /**
     * 训练数据量
     */
    private Integer trainingDataCount;
    
    /**
     * 模型准确率
     */
    private BigDecimal accuracy;
    
    /**
     * 记录创建时间
     */
    private Date createdAt;
}