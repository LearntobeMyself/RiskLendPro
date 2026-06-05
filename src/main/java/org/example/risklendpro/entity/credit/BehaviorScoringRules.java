package org.example.risklendpro.entity.credit;

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
}
