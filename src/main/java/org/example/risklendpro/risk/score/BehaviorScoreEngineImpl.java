package org.example.risklendpro.risk.score;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.risk.credit.BehaviorScoringRules;
import org.example.risklendpro.risk.credit.mapper.BehaviorScoringRulesMapper;
import org.example.risklendpro.risk.score.BehaviorScoreEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;

@Service
public class BehaviorScoreEngineImpl implements BehaviorScoreEngine {

    private static final Logger log = LoggerFactory.getLogger(BehaviorScoreEngineImpl.class);
    private static final double DEFAULT_WATCH = 650.0;
    private static final double DEFAULT_REDUCE = 550.0;

    @Autowired
    private BehaviorScoringRulesMapper behaviorScoringRulesMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile JsonNode cachedRuleRoot;
    private volatile double cachedWatch = DEFAULT_WATCH;
    private volatile double cachedReduce = DEFAULT_REDUCE;

    @Override
    public double calculateBaseScore(Map<String, Double> featureValues) {
        JsonNode root = loadRuleRoot();
        if (root == null) {
            return 700.0;
        }
        JsonNode features = root.get("features");
        JsonNode coefs = root.has("coefficients") ? root.get("coefficients") : root.get("feature_weights");
        double intercept = root.has("intercept") ? root.get("intercept").asDouble() : 0.0;

        double linear = intercept;
        if (coefs != null && coefs.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = coefs.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                double w = e.getValue().asDouble();
                Double raw = featureValues != null ? featureValues.get(key) : null;
                JsonNode featDef = features != null ? features.get(key) : null;
                double woe = woeLookup(raw, featDef);
                linear += w * woe;
            }
        }
        double prob = sigmoid(linear);
        return scorecardFromProb(prob, root.get("scorecard"));
    }

    @Override
    public double getThresholdWatch() {
        loadRuleRoot();
        return cachedWatch;
    }

    @Override
    public double getThresholdReduceLimit() {
        loadRuleRoot();
        return cachedReduce;
    }

    private JsonNode loadRuleRoot() {
        if (cachedRuleRoot != null) {
            return cachedRuleRoot;
        }
        synchronized (this) {
            if (cachedRuleRoot != null) {
                return cachedRuleRoot;
            }
            try {
                BehaviorScoringRules rules = behaviorScoringRulesMapper.selectActiveRule();
                if (rules == null || rules.getRuleContent() == null) {
                    log.warn("未找到激活的 B 卡规则 behavior_scoring_rules");
                    return null;
                }
                cachedRuleRoot = objectMapper.readTree(rules.getRuleContent());
                if (rules.getThresholdWatch() != null) {
                    cachedWatch = rules.getThresholdWatch().doubleValue();
                }
                if (rules.getThresholdReduceLimit() != null) {
                    cachedReduce = rules.getThresholdReduceLimit().doubleValue();
                }
                return cachedRuleRoot;
            } catch (Exception e) {
                log.error("加载 B 卡规则失败", e);
                return null;
            }
        }
    }

    private static double woeLookup(Double raw, JsonNode featDef) {
        if (featDef == null || !featDef.isObject()) {
            return 0.0;
        }
        if (raw == null) {
            return featDef.path("missing_bin").path("woe").asDouble(0.0);
        }
        String type = featDef.path("type").asText("numeric");
        if ("categorical".equals(type)) {
            int key = raw >= 0.5 ? 1 : 0;
            JsonNode cat = featDef.path("categories").path(String.valueOf(key));
            if (cat.isObject() && cat.has("woe")) {
                return cat.get("woe").asDouble();
            }
            return featDef.path("missing_bin").path("woe").asDouble(0.0);
        }
        JsonNode cuts = featDef.get("cuts");
        JsonNode woeArr = featDef.get("woe");
        if (cuts == null || !cuts.isArray() || woeArr == null || !woeArr.isArray()) {
            return 0.0;
        }
        double x = raw;
        for (int i = 0; i < cuts.size(); i++) {
            if (x <= cuts.get(i).asDouble()) {
                return woeArr.get(Math.min(i, woeArr.size() - 1)).asDouble();
            }
        }
        return woeArr.get(woeArr.size() - 1).asDouble();
    }

    private static double sigmoid(double x) {
        if (x >= 30) {
            return 1.0;
        }
        if (x <= -30) {
            return 0.0;
        }
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private static double scorecardFromProb(double probDefault, JsonNode scorecardNode) {
        final double eps = 1e-9;
        double p = probDefault;
        if (p <= 0.0) {
            p = eps;
        }
        if (p >= 1.0) {
            p = 1.0 - eps;
        }
        double pdo = 80;
        double targetScore = 650;
        double targetOdds = 1;
        int minS = 350;
        int maxS = 950;
        if (scorecardNode != null && scorecardNode.isObject()) {
            if (scorecardNode.has("pdo")) {
                pdo = scorecardNode.get("pdo").asDouble();
            }
            if (scorecardNode.has("target_score")) {
                targetScore = scorecardNode.get("target_score").asDouble();
            }
            if (scorecardNode.has("target_odds")) {
                targetOdds = scorecardNode.get("target_odds").asDouble();
            }
            if (scorecardNode.has("min_score")) {
                minS = scorecardNode.get("min_score").asInt();
            }
            if (scorecardNode.has("max_score")) {
                maxS = scorecardNode.get("max_score").asInt();
            }
        }
        double odds = p / (1.0 - p);
        double factor = -pdo / Math.log(2);
        double offset = targetScore - factor * Math.log(targetOdds);
        double score = offset + factor * Math.log(odds);
        score = Math.max(minS, Math.min(maxS, score));
        return Math.round(score * 10.0) / 10.0;
    }
}
