package org.example.risklendpro.risk.credit;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class BehaviorScoringRules {
    private Long id;
    private String version;
    private String ruleContent;
    private String featureWeights;
    private String scorecard;
    private BigDecimal intercept;
    private BigDecimal thresholdWatch;
    private BigDecimal thresholdReduceLimit;
    private Integer isActive;
    private Date trainedAt;
    private Integer trainingDataCount;
    private BigDecimal accuracy;
    private Date createdAt;

    /** 规则内容 SHA-256（对 rule_content 原文计算；发布端与消费端口径一致）。 */
    private String ruleHash;
    /** 该版本被激活的时间，用于稳定、可解释的版本选择。 */
    private Date activatedAt;
    /** 模型类型，期望 b_card_woe。 */
    private String modelType;
    /** 卡类型，期望 B。 */
    private String cardType;
    /** 阈值来源：DB_COLUMN / RULE_CONTENT / DEFAULT。 */
    private String thresholdSource;
}