package org.example.risklendpro.risk.bcard;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.risk.score.BehaviorScoreEngineImpl;
import org.example.risklendpro.risk.score.BehaviorScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 影子评分（阶段 A4）。
 *
 * <p>只读用户额度与最新快照，把「模型决策」与「现有策略决策」的差异写入
 * b_card_shadow_decision。整个类**没有任何**写额度的代码路径，
 * 也不依赖 LimitAdjustLogMapper，从结构上保证影子运行不影响真实额度。</p>
 *
 * <p>开关：risk.b-card.shadow.enabled（默认关闭）。</p>
 */
@Service
public class BCardShadowScoreService {

    private static final Logger log = LoggerFactory.getLogger(BCardShadowScoreService.class);

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private BehaviorScoreService behaviorScoreService;

    @Autowired
    private BCardShadowSnapshotReader snapshotReader;

    @Autowired
    private BCardShadowDecisionRecorder recorder;

    @Value("${risk.b-card.shadow.model-path:}")
    private String modelPath;

    @Value("${risk.b-card.shadow.max-users:500}")
    private int maxUsers = 500;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile JsonNode cachedModel;
    private volatile long cachedModelModifiedAt;

    /**
     * 执行一轮影子评分。
     *
     * @return 成功记录的用户数
     */
    public int scoreShadowDecisions() {
        JsonNode model = loadModel();
        if (model == null) {
            log.warn("影子评分未执行：未配置或无法读取模型 {}", modelPath);
            return 0;
        }
        String featureVersion = model.path("feature_version").asText(null);
        String modelVersion = model.path("model_version").asText("A4-shadow");
        double intercept = model.path("intercept").asDouble(0.0);
        JsonNode coefficients = model.path("coefficients");
        JsonNode featureDefs = model.path("feature_defs");
        double a = model.path("calibration").path("a").asDouble(1.0);
        double b = model.path("calibration").path("b").asDouble(0.0);
        double approveBelow = model.path("policy").path("approve_below").asDouble(0.5);
        double rejectAbove = model.path("policy").path("reject_above").asDouble(0.8);
        double reduceMultiplier = model.path("policy").path("multipliers").path("reduce").asDouble(0.5);
        JsonNode pdo = model.path("pdo");

        QueryWrapper<UserCreditLimit> query = new QueryWrapper<UserCreditLimit>()
                .eq("b_card_enabled", true)
                .orderByAsc("user_id")
                .last("LIMIT " + Math.max(1, maxUsers));
        List<UserCreditLimit> users = userCreditLimitMapper.selectList(query);

        int recorded = 0;
        int skipped = 0;
        for (UserCreditLimit user : users) {
            Optional<BCardShadowSnapshotReader.ShadowFeature> snapshot =
                    snapshotReader.findLatest(user.getUserId(), featureVersion);
            if (snapshot.isEmpty()) {
                skipped++;
                continue;
            }
            try {
                double linear = linearScore(intercept, coefficients, featureDefs, snapshot.get().features());
                double probability = sigmoid(a * linear + b);
                double multiplier = resolveMultiplier(probability, approveBelow, rejectAbove, reduceMultiplier);
                int score = pdoScore(probability, pdo);
                Double current = user.getBScore() == null
                        ? null
                        : behaviorScoreService.resolveLimitMultiplier(user.getBScore().doubleValue());
                boolean differs = current == null || Math.abs(current - multiplier) > 0.001;
                String reason = bucketName(multiplier) + (differs ? "_DIFF" : "_SAME")
                        + (current == null ? "_NO_B_SCORE" : "");

                recorder.record(new BCardShadowDecisionRecorder.ShadowDecision(
                        user.getUserId(), snapshot.get().asOfDate(), modelVersion, featureVersion,
                        round(probability), score, multiplier, current, differs, reason));
                recorded++;
            } catch (Exception e) {
                log.warn("影子评分失败 userId={}", user.getUserId(), e);
            }
        }
        log.info("影子评分完成 recorded={} skipped(no snapshot)={} modelVersion={}",
                recorded, skipped, modelVersion);
        return recorded;
    }

    private JsonNode loadModel() {
        if (modelPath == null || modelPath.isBlank()) {
            return null;
        }
        File file = new File(modelPath);
        if (!file.isFile()) {
            return null;
        }
        long modified = file.lastModified();
        JsonNode cached = cachedModel;
        if (cached != null && modified == cachedModelModifiedAt) {
            return cached;
        }
        synchronized (this) {
            try {
                cachedModel = objectMapper.readTree(file);
                cachedModelModifiedAt = modified;
                return cachedModel;
            } catch (Exception e) {
                log.error("读取影子模型失败 {}", modelPath, e);
                return null;
            }
        }
    }

    private static double linearScore(double intercept, JsonNode coefficients, JsonNode featureDefs,
                                      java.util.Map<String, Double> features) {
        double linear = intercept;
        java.util.Iterator<java.util.Map.Entry<String, JsonNode>> it = coefficients.fields();
        while (it.hasNext()) {
            java.util.Map.Entry<String, JsonNode> entry = it.next();
            String name = entry.getKey();
            Double raw = features.get(name);
            double woe = BehaviorScoreEngineImpl.woeLookup(raw, featureDefs.get(name));
            linear += entry.getValue().asDouble() * woe;
        }
        return linear;
    }

    private static double resolveMultiplier(double probability, double approveBelow,
                                            double rejectAbove, double reduceMultiplier) {
        if (probability < approveBelow) {
            return 1.0;
        }
        if (probability < rejectAbove) {
            return reduceMultiplier;
        }
        return 0.0;
    }

    private static String bucketName(double multiplier) {
        if (multiplier >= 1.0) {
            return "APPROVE";
        }
        if (multiplier > 0.0) {
            return "REDUCE";
        }
        return "REJECT";
    }

    private static int pdoScore(double probability, JsonNode pdo) {
        double pdoValue = pdo.path("pdo").asDouble(80.0);
        double targetScore = pdo.path("target_score").asDouble(650.0);
        double targetOdds = pdo.path("target_odds").asDouble(1.0);
        int minScore = pdo.path("min_score").asInt(350);
        int maxScore = pdo.path("max_score").asInt(950);
        double p = Math.min(Math.max(probability, 1e-9), 1.0 - 1e-9);
        double factor = -pdoValue / Math.log(2.0);
        double offset = targetScore - factor * Math.log(targetOdds);
        double score = offset + factor * Math.log(p / (1.0 - p));
        return (int) Math.round(Math.max(minScore, Math.min(maxScore, score)));
    }

    private static double sigmoid(double value) {
        double clamped = Math.max(-30.0, Math.min(30.0, value));
        return 1.0 / (1.0 + Math.exp(-clamped));
    }

    private static Double round(double value) {
        return BigDecimal.valueOf(value).setScale(6, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    /** 供测试设置模型路径。 */
    void setModelPathForTest(String modelPath) {
        this.modelPath = modelPath;
    }
}