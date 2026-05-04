package org.example.risklendpro.entity.credit;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 评分规则实体类 - 从credit_data_db数据库读取（Python训练产出）
 */
@Data
public class ScoringRules {
    private Long id;
    private String version;
    private String ruleContent;
    private BigDecimal intercept;
    private BigDecimal thresholdAutoApprove;
    private BigDecimal thresholdManualReview;
    private Integer isActive;
    private Date trainedAt;
    private Integer trainingDataCount;
    private BigDecimal accuracy;
    private Date createdAt;
}
