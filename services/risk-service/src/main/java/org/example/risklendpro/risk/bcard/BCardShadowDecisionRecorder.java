package org.example.risklendpro.risk.bcard;

import java.time.LocalDate;

/**
 * 影子决策记录器（阶段 A4）。
 *
 * <p>影子评分只允许写本表，严禁写 user_credit_limit / limit_adjust_log。</p>
 */
public interface BCardShadowDecisionRecorder {

    void record(ShadowDecision decision);

    /** 一次影子决策的完整快照。 */
    record ShadowDecision(
            Long userId,
            LocalDate asOfDate,
            String modelVersion,
            String featureVersion,
            Double modelProbability,
            Integer modelScore,
            Double modelMultiplier,
            Double currentMultiplier,
            boolean decisionDiffers,
            String reason
    ) {
    }
}