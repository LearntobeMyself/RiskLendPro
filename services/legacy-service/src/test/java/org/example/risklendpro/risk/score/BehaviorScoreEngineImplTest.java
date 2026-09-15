package org.example.risklendpro.risk.score;

import org.example.risklendpro.risk.credit.BehaviorScoringRules;
import org.example.risklendpro.risk.credit.mapper.BehaviorScoringRulesMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BehaviorScoreEngineImplTest {

    @InjectMocks
    private BehaviorScoreEngineImpl engine;

    @Mock
    private BehaviorScoringRulesMapper behaviorScoringRulesMapper;

    @BeforeEach
    void setUpRules() {
        BehaviorScoringRules rules = new BehaviorScoringRules();
        rules.setThresholdWatch(BigDecimal.valueOf(650));
        rules.setThresholdReduceLimit(BigDecimal.valueOf(550));
        rules.setRuleContent("""
                {
                  "intercept": -0.05,
                  "coefficients": {
                    "inst_dpd_max": -0.82,
                    "inst_cnt": -0.48
                  },
                  "features": {
                    "inst_dpd_max": {
                      "type": "numeric",
                      "cuts": [5,15,30,60,90],
                      "woe": [-0.45,-0.12,0.18,0.42,0.71]
                    },
                    "inst_cnt": {
                      "type": "numeric",
                      "cuts": [1,3,6,10,15],
                      "woe": [-0.15,-0.05,0.12,0.35,0.58]
                    }
                  },
                  "scorecard": {
                    "scale": "odds_pdo",
                    "min_score": 350,
                    "max_score": 950,
                    "pdo": 125,
                    "target_score": 650,
                    "target_odds": 0.75
                  },
                  "thresholds": { "watch": 650, "reduce_limit": 550 }
                }
                """);
        when(behaviorScoringRulesMapper.selectActiveRule()).thenReturn(rules);
    }

    @Test
    void calculateBaseScore_returnsScoreInRange() {
        Map<String, Double> features = new HashMap<>();
        features.put("inst_dpd_max", 12.0);
        features.put("inst_cnt", 4.0);
        double score = engine.calculateBaseScore(features);
        assertTrue(score >= 350 && score <= 950);
    }
}
