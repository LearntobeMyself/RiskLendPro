package org.example.risklendpro.risk.score;

import java.util.Map;

/**
 * B 卡（贷后行为）评分引擎，与 A 卡 CreditScoreEngine 完全分离。
 */
public interface BehaviorScoreEngine {

    double calculateBaseScore(Map<String, Double> featureValues);

    double getThresholdWatch();

    double getThresholdReduceLimit();
}
