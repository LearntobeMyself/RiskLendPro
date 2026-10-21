package org.example.risklendpro.risk.score;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.risk.credit.BehaviorScoringRules;
import org.example.risklendpro.risk.credit.mapper.BehaviorScoringRulesMapper;
import org.example.risklendpro.risk.score.BehaviorScoreEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * B 卡评分引擎。
 *
 * <p>阶段 A1 规则治理：规则加载带 TTL 与内容哈希校验，支持不重启切换版本、
 * 支持人工强制刷新；非法规则一律拒绝进入评分链路。</p>
 *
 * <p>本类不改变任何 WOE、系数、截距、阈值或 PDO 公式。</p>
 */
@Service
public class BehaviorScoreEngineImpl implements BehaviorScoreEngine {

    private static final Logger log = LoggerFactory.getLogger(BehaviorScoreEngineImpl.class);
    private static final double DEFAULT_WATCH = 650.0;
    private static final double DEFAULT_REDUCE = 550.0;
    private static final String MODEL_TYPE_EXPECTED = "b_card_woe";
    private static final String CARD_TYPE_EXPECTED = "B";

    /** 规则缺失（无 active 版本或无规则内容）。 */
    public static final String STATUS_RULE_NOT_FOUND = "RULE_NOT_FOUND";
    /** 规则内容无法解析为 JSON。 */
    public static final String STATUS_RULE_PARSE_ERROR = "RULE_PARSE_ERROR";
    /** 规则结构校验失败（模型类型、特征列表、系数长度、WOE 长度、阈值或 PDO 参数非法）。 */
    public static final String STATUS_RULE_VALIDATION_ERROR = "RULE_VALIDATION_ERROR";

    public static final String THRESHOLD_SOURCE_DB_COLUMN = "DB_COLUMN";
    public static final String THRESHOLD_SOURCE_RULE_CONTENT = "RULE_CONTENT";
    public static final String THRESHOLD_SOURCE_DEFAULT = "DEFAULT";

    @Autowired
    private BehaviorScoringRulesMapper behaviorScoringRulesMapper;

    /**
     * 规则缓存 TTL（秒）。到期后重新读取 active 版本，因此发布新规则后无需重启服务；
     * 设为 0 表示每次调用都重新读取；需要立即生效时可调用 {@link #refreshRules()}。
     */
    @Value("${risk.b-card.rule-cache-ttl-seconds:60}")
    private long ruleCacheTtlSeconds = 60L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object lock = new Object();

    private volatile CachedRule cached;
    private volatile String cachedFailureStatus;
    private volatile String cachedFailureDetail;
    private volatile long cachedFailureAtMillis;

    @Override
    public double calculateBaseScore(Map<String, Double> featureValues) {
        return calculateBaseScore(requireRuleRoot(), featureValues);
    }

    /**
     * 用指定规则内容计算基础分（不读写缓存）。供回放校验与测试使用。
     */
    public static double calculateBaseScore(JsonNode ruleRoot, Map<String, Double> featureValues) {
        JsonNode features = ruleRoot.get("features");
        JsonNode coefs = ruleRoot.has("coefficients") ? ruleRoot.get("coefficients")
                : ruleRoot.get("feature_weights");
        double intercept = ruleRoot.has("intercept") ? ruleRoot.get("intercept").asDouble() : 0.0;

        double linear = intercept;
        if (coefs != null && coefs.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = coefs.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                double w = e.getValue().asDouble();
                Double raw = featureValues != null ? featureValues.get(key) : null;
                JsonNode featDef = features != null ? features.get(key) : null;
                linear += w * woeLookup(raw, featDef);
            }
        }
        double prob = sigmoid(linear);
        return scorecardFromProb(prob, ruleRoot.get("scorecard"));
    }

    @Override
    public double getThresholdWatch() {
        loadRuleRoot();
        CachedRule current = cached;
        return current != null ? current.watch : DEFAULT_WATCH;
    }

    @Override
    public double getThresholdReduceLimit() {
        loadRuleRoot();
        CachedRule current = cached;
        return current != null ? current.reduce : DEFAULT_REDUCE;
    }

    /** 最近一次规则加载失败的状态码；加载成功时为 null。 */
    public String getRuleFailureStatus() {
        loadRuleRoot();
        return cachedFailureStatus;
    }

    /**
     * 人工刷新入口：忽略 TTL 强制重新读取 active 规则，并返回当前运行时规则信息。
     */
    public RuleRuntimeInfo refreshRules() {
        synchronized (lock) {
            reloadLocked();
        }
        return describeActiveRule();
    }

    /** 当前运行时规则信息（版本、哈希、加载时间、阈值来源、active 版本数）。 */
    public RuleRuntimeInfo describeActiveRule() {
        loadRuleRoot();
        CachedRule current = cached;
        int activeCount;
        try {
            activeCount = behaviorScoringRulesMapper.countActiveRules();
        } catch (Exception e) {
            log.warn("统计 active B 卡规则版本失败", e);
            activeCount = -1;
        }
        if (current == null) {
            return new RuleRuntimeInfo(null, null, null, null, null, null,
                    cachedFailureStatus, null, activeCount, null, null);
        }
        return new RuleRuntimeInfo(current.ruleId, current.version, current.contentHash,
                current.modelType, current.cardType, current.thresholdSource, null,
                new Date(current.loadedAtMillis), activeCount, current.watch, current.reduce);
    }

    private JsonNode requireRuleRoot() {
        JsonNode root = loadRuleRoot();
        if (root == null) {
            String status = cachedFailureStatus != null ? cachedFailureStatus : STATUS_RULE_NOT_FOUND;
            String detail = cachedFailureDetail;
            throw new RuleUnavailableException(status, detail == null || detail.isBlank()
                    ? "B 卡规则不可用：" + status
                    : "B 卡规则不可用：" + status + "：" + detail);
        }
        return root;
    }

    private JsonNode loadRuleRoot() {
        CachedRule current = cached;
        if (current != null) {
            if (!isExpired(current.loadedAtMillis)) {
                return current.root;
            }
            return reload();
        }
        // 规则暂时不可用时，失败状态同样受 TTL 保护，避免每次评分都打数据库
        if (cachedFailureStatus != null && !isExpired(cachedFailureAtMillis)) {
            return null;
        }
        return reload();
    }

    private boolean isExpired(long loadedAtMillis) {
        if (ruleCacheTtlSeconds <= 0) {
            return true;
        }
        return System.currentTimeMillis() - loadedAtMillis >= ruleCacheTtlSeconds * 1000L;
    }

    private JsonNode reload() {
        synchronized (lock) {
            CachedRule current = cached;
            if (current != null && !isExpired(current.loadedAtMillis)) {
                return current.root;
            }
            reloadLocked();
            CachedRule after = cached;
            return after != null ? after.root : null;
        }
    }

    private void reloadLocked() {
        CachedRule previous = cached;
        BehaviorScoringRules rules;
        try {
            rules = behaviorScoringRulesMapper.selectActiveRule();
        } catch (Exception e) {
            log.error("读取 B 卡规则失败", e);
            markFailure(STATUS_RULE_PARSE_ERROR, e.getMessage());
            return;
        }
        if (rules == null || rules.getRuleContent() == null || rules.getRuleContent().isBlank()) {
            log.warn("未找到激活的 B 卡规则 behavior_scoring_rules");
            markFailure(STATUS_RULE_NOT_FOUND, "未找到 is_active = 1 的规则行");
            return;
        }

        String contentHash = sha256Hex(rules.getRuleContent());

        // 内容哈希与规则行都没变：只续期，不重新解析，避免评分树抖动
        if (previous != null && Objects.equals(previous.ruleId, rules.getId())
                && contentHash != null && contentHash.equals(previous.contentHash)) {
            cached = previous.renew(System.currentTimeMillis());
            cachedFailureStatus = null;
            cachedFailureDetail = null;
            return;
        }

        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(rules.getRuleContent());
        } catch (Exception e) {
            log.error("解析 B 卡规则失败", e);
            markFailure(STATUS_RULE_PARSE_ERROR, e.getMessage());
            return;
        }
        try {
            validateRuleContent(parsed);
        } catch (RuleUnavailableException e) {
            log.error("B 卡规则校验失败：{}", e.getMessage());
            markFailure(STATUS_RULE_VALIDATION_ERROR, e.getMessage());
            return;
        }

        Thresholds thresholds = resolveThresholds(rules, parsed);
        CachedRule next = new CachedRule(parsed, rules.getId(), rules.getVersion(), contentHash,
                firstNonNull(rules.getModelType(), textOf(parsed, "model_type")),
                firstNonNull(rules.getCardType(), textOf(parsed, "card_type")),
                thresholds.source, thresholds.watch, thresholds.reduce,
                System.currentTimeMillis());
        cached = next;
        cachedFailureStatus = null;
        cachedFailureDetail = null;
        log.info("B 卡规则已加载 version={} id={} hash={} thresholdSource={} watch={} reduce={}",
                next.version, next.ruleId, next.contentHash, next.thresholdSource, next.watch, next.reduce);
    }

    private void markFailure(String status, String detail) {
        cachedFailureStatus = status;
        cachedFailureDetail = detail;
        cachedFailureAtMillis = System.currentTimeMillis();
    }

    private Thresholds resolveThresholds(BehaviorScoringRules rules, JsonNode root) {
        Double watch = rules.getThresholdWatch() != null ? rules.getThresholdWatch().doubleValue() : null;
        Double reduce = rules.getThresholdReduceLimit() != null ? rules.getThresholdReduceLimit().doubleValue() : null;
        JsonNode thresholds = root.path("thresholds");

        if (watch != null && reduce != null) {
            warnIfThresholdMismatch("watch", watch, thresholds.path("watch"));
            warnIfThresholdMismatch("reduce_limit", reduce, thresholds.path("reduce_limit"));
            return new Thresholds(watch, reduce, THRESHOLD_SOURCE_DB_COLUMN);
        }

        boolean fromContent = thresholds.path("watch").isNumber() && thresholds.path("reduce_limit").isNumber();
        double resolvedWatch = watch != null ? watch
                : (fromContent ? thresholds.path("watch").asDouble() : DEFAULT_WATCH);
        double resolvedReduce = reduce != null ? reduce
                : (fromContent ? thresholds.path("reduce_limit").asDouble() : DEFAULT_REDUCE);
        return new Thresholds(resolvedWatch, resolvedReduce,
                fromContent ? THRESHOLD_SOURCE_RULE_CONTENT : THRESHOLD_SOURCE_DEFAULT);
    }

    private static void warnIfThresholdMismatch(String field, double dbValue, JsonNode contentValue) {
        if (contentValue.isNumber() && Math.abs(contentValue.asDouble() - dbValue) > 1e-6) {
            log.warn("B 卡阈值不一致 field={} DB={} 规则内容={}，以数据库列为准", field, dbValue, contentValue.asDouble());
        }
    }

    /**
     * 结构校验：确保评分所需的模型元数据、特征列表、系数、分箱、阈值与 scorecard 齐备。
     * 只做完整性校验，不改变任何 WOE、系数、阈值或公式。
     */
    public static void validateRuleContent(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw validationError("规则根节点必须是 JSON 对象");
        }

        String modelType = textOf(root, "model_type");
        if (modelType == null || modelType.isBlank()) {
            throw validationError("缺少 model_type");
        }
        if (!MODEL_TYPE_EXPECTED.equals(modelType)) {
            throw validationError("model_type 期望 " + MODEL_TYPE_EXPECTED + "，实际 " + modelType);
        }

        String cardType = textOf(root, "card_type");
        if (cardType == null || cardType.isBlank()) {
            throw validationError("缺少 card_type");
        }
        if (!CARD_TYPE_EXPECTED.equalsIgnoreCase(cardType)) {
            throw validationError("card_type 期望 " + CARD_TYPE_EXPECTED + "，实际 " + cardType);
        }

        JsonNode coefs = root.has("coefficients") ? root.get("coefficients")
                : (root.has("feature_weights") ? root.get("feature_weights") : null);
        if (coefs == null || !coefs.isObject() || coefs.isEmpty()) {
            throw validationError("缺少非空的 coefficients/feature_weights");
        }

        JsonNode features = root.get("features");
        if (features == null || !features.isObject()) {
            throw validationError("缺少 features 特征定义");
        }

        JsonNode selected = root.get("selected_features");
        if (selected == null || !selected.isArray() || selected.isEmpty()) {
            throw validationError("缺少 selected_features 特征列表");
        }
        Set<String> selectedNames = new LinkedHashSet<>();
        for (JsonNode node : selected) {
            selectedNames.add(node.asText());
        }
        Set<String> coefficientNames = new LinkedHashSet<>();
        coefs.fieldNames().forEachRemaining(coefficientNames::add);
        if (!selectedNames.equals(coefficientNames)) {
            throw validationError("selected_features 与 coefficients 不一致：selected="
                    + selectedNames + "，coefficients=" + coefficientNames);
        }

        Iterator<Map.Entry<String, JsonNode>> it = coefs.fields();
        while (it.hasNext()) {
            String key = it.next().getKey();
            JsonNode def = features.get(key);
            if (def == null || !def.isObject()) {
                throw validationError("系数 " + key + " 缺少特征定义");
            }
            String type = def.path("type").asText("numeric");
            if ("categorical".equals(type)) {
                JsonNode cats = def.get("categories");
                if (cats == null || !cats.isObject() || cats.isEmpty()) {
                    throw validationError("类别特征 " + key + " 缺少 categories");
                }
            } else {
                JsonNode cuts = def.get("cuts");
                JsonNode woe = def.get("woe");
                if (cuts == null || !cuts.isArray() || cuts.isEmpty()) {
                    throw validationError("数值特征 " + key + " 缺少 cuts");
                }
                if (woe == null || !woe.isArray() || woe.size() < cuts.size()) {
                    throw validationError("数值特征 " + key + " 的 woe 长度与 cuts 不一致");
                }
            }
        }

        JsonNode thresholds = root.get("thresholds");
        if (thresholds == null || !thresholds.isObject()
                || !thresholds.path("watch").isNumber() || !thresholds.path("reduce_limit").isNumber()) {
            throw validationError("缺少 thresholds.watch / thresholds.reduce_limit");
        }
        double watch = thresholds.get("watch").asDouble();
        double reduce = thresholds.get("reduce_limit").asDouble();
        if (!(reduce < watch)) {
            throw validationError("thresholds 需要满足 reduce_limit < watch，实际 watch=" + watch + " reduce_limit=" + reduce);
        }

        JsonNode scorecard = root.get("scorecard");
        if (scorecard == null || !scorecard.isObject()) {
            throw validationError("缺少 scorecard 评分配置");
        }
        if (!scorecard.path("pdo").isNumber() || scorecard.get("pdo").asDouble() <= 0) {
            throw validationError("scorecard.pdo 必须大于 0");
        }
        if (!scorecard.path("target_score").isNumber() || scorecard.get("target_score").asDouble() <= 0) {
            throw validationError("scorecard.target_score 必须大于 0");
        }
        if (!scorecard.path("target_odds").isNumber() || scorecard.get("target_odds").asDouble() <= 0) {
            throw validationError("scorecard.target_odds 必须大于 0");
        }
        if (!scorecard.path("min_score").isNumber() || !scorecard.path("max_score").isNumber()) {
            throw validationError("scorecard 缺少 min_score / max_score");
        }
        if (scorecard.get("min_score").asDouble() >= scorecard.get("max_score").asDouble()) {
            throw validationError("scorecard.min_score 必须小于 max_score");
        }
    }

    private static RuleUnavailableException validationError(String message) {
        return new RuleUnavailableException(STATUS_RULE_VALIDATION_ERROR, message);
    }

    private static String textOf(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String firstNonNull(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String sha256Hex(String content) {
        if (content == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * WOE 查表（公开给影子评分复用，保证在线/影子使用同一份分箱实现）。
     */
    public static double woeLookup(Double raw, JsonNode featDef) {
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

    /** 规则缓存条目。 */
    private static class CachedRule {
        private final JsonNode root;
        private final Long ruleId;
        private final String version;
        private final String contentHash;
        private final String modelType;
        private final String cardType;
        private final String thresholdSource;
        private final double watch;
        private final double reduce;
        private final long loadedAtMillis;

        private CachedRule(JsonNode root, Long ruleId, String version, String contentHash,
                           String modelType, String cardType, String thresholdSource,
                           double watch, double reduce, long loadedAtMillis) {
            this.root = root;
            this.ruleId = ruleId;
            this.version = version;
            this.contentHash = contentHash;
            this.modelType = modelType;
            this.cardType = cardType;
            this.thresholdSource = thresholdSource;
            this.watch = watch;
            this.reduce = reduce;
            this.loadedAtMillis = loadedAtMillis;
        }

        private CachedRule renew(long loadedAtMillis) {
            return new CachedRule(root, ruleId, version, contentHash, modelType, cardType,
                    thresholdSource, watch, reduce, loadedAtMillis);
        }
    }

    private static class Thresholds {
        private final double watch;
        private final double reduce;
        private final String source;

        private Thresholds(double watch, double reduce, String source) {
            this.watch = watch;
            this.reduce = reduce;
            this.source = source;
        }
    }

    /** 规则运行时信息，供人工刷新入口与监控输出。 */
    public record RuleRuntimeInfo(
            Long ruleId,
            String version,
            String contentHash,
            String modelType,
            String cardType,
            String thresholdSource,
            String failureStatus,
            Date loadedAt,
            int activeVersionCount,
            Double thresholdWatch,
            Double thresholdReduceLimit
    ) {
    }

    /**
     * 规则不可用（缺失 / 解析失败 / 校验失败）时抛出，携带失败状态码。
     * 调用方必须据此把本次评分标记为不可信，而不是当成中性分使用。
     */
    public static class RuleUnavailableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String dataStatus;

        public RuleUnavailableException(String dataStatus, String message) {
            super(message);
            this.dataStatus = dataStatus;
        }

        public String getDataStatus() {
            return dataStatus;
        }
    }
}