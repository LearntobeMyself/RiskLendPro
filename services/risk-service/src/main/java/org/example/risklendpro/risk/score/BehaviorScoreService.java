package org.example.risklendpro.risk.score;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.api.dto.CreditBehaviorUpsertCommand;
import org.example.risklendpro.api.dto.CreditLimitSnapshot;
import org.example.risklendpro.api.dto.LimitMultiplierDecision;
import org.example.risklendpro.api.dto.UserSummary;
import org.example.risklendpro.risk.bcard.BCardDecisionAuditRecorder;
import org.example.risklendpro.risk.client.LoanServiceClient;
import org.example.risklendpro.risk.client.UserServiceClient;
import org.example.risklendpro.risk.credit.BehaviorScoringRules;
import org.example.risklendpro.risk.credit.UserBehaviorFeatures;
import org.example.risklendpro.risk.credit.mapper.BehaviorScoringRulesMapper;
import org.example.risklendpro.risk.credit.mapper.UserBehaviorFeaturesMapper;
import org.example.risklendpro.risk.entity.UserBCardLog;
import org.example.risklendpro.risk.mapper.UserBCardLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class BehaviorScoreService {

    private static final Logger log = LoggerFactory.getLogger(BehaviorScoreService.class);
    private static final double NEUTRAL_BASE = 700.0;
    private static final double MIN_SCORE = 350.0;
    private static final double MAX_SCORE = 950.0;

    private static final String STATUS_RELIABLE = "RELIABLE";
    private static final String STATUS_MISSING_ID_CARD = "MISSING_ID_CARD";
    private static final String STATUS_FEATURE_NOT_FOUND = "FEATURE_NOT_FOUND";
    private static final String STATUS_FEATURE_PARSE_ERROR = "FEATURE_PARSE_ERROR";
    private static final String STATUS_INSUFFICIENT_HISTORY = "INSUFFICIENT_HISTORY";
    private static final String STATUS_NO_SCORE_LOG = "NO_SCORE_LOG";
    private static final String STATUS_TRUST_PARSE_ERROR = "TRUST_PARSE_ERROR";

    /** 当前线上 B 卡入模的特征口径（HC 静态特征 + 贷后实时修正特征）。 */
    public static final String FEATURE_VERSION = "HC_STATIC_V1+LIVE_V1";
    /** 额度系数策略版本（阈值来源为激活规则）。 */
    public static final String POLICY_VERSION = "BCARD_LIMIT_POLICY_V1";

    @Autowired
    private BehaviorScoreEngine behaviorScoreEngine;

    @Autowired
    private BehaviorLiveFeatureService behaviorLiveFeatureService;

    @Autowired
    private UserBehaviorFeaturesMapper userBehaviorFeaturesMapper;

    @Autowired
    private UserBCardLogMapper userBCardLogMapper;

    @Autowired
    private BehaviorScoringRulesMapper behaviorScoringRulesMapper;

    @Autowired
    private LoanServiceClient loanServiceClient;

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private BCardDecisionAuditRecorder decisionAuditRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void activate(Long userId, String idCard) {
        if (userId == null) {
            return;
        }
        loanServiceClient.ensureCreditLimit(userId);
        recalculateInternal(userId, resolveIdCard(userId, idCard), true);
        log.info("B 卡已启动 userId={}", userId);
    }

    @Transactional
    public void recalculate(Long userId) {
        if (userId == null) {
            return;
        }
        CreditLimitSnapshot limit = loanServiceClient.getCreditLimit(userId);
        if (limit == null || !limit.bCardEnabled()) {
            return;
        }
        recalculateInternal(userId, resolveIdCard(userId, null), null);
    }

    public boolean isBCardEnabled(Long userId) {
        CreditLimitSnapshot limit = loanServiceClient.getCreditLimit(userId);
        return limit != null && limit.bCardEnabled();
    }

    /**
     * 仅按分数映射额度系数（纯函数）。调用方必须先确认分数可信；
     * 对外额度流程请使用 {@link #resolveLimitMultiplierForUser(Long, double)}。
     */
    public double resolveLimitMultiplier(double finalBScore) {
        double watch = behaviorScoreEngine.getThresholdWatch();
        double reduce = behaviorScoreEngine.getThresholdReduceLimit();
        if (finalBScore >= watch + 50) {
            return 1.0;
        }
        if (finalBScore >= watch) {
            return 0.9;
        }
        if (finalBScore >= reduce) {
            return 0.8;
        }
        if (finalBScore >= reduce - 100) {
            return 0.5;
        }
        if (finalBScore >= reduce - 200) {
            return 0.2;
        }
        return 0.0;
    }

    /**
     * 统一的额度系数决策入口：可信度判断与系数生成在同一决策点完成。
     *
     * <p>{@code scoreReliable=false} 时返回 {@code decisionAllowed=false} 且
     * {@code multiplier=null}，调用方不得据此调整额度。</p>
     */
    public LimitMultiplierDecision resolveLimitMultiplierForUser(Long userId, double finalBScore) {
        ScoreTrust trust = loadLatestScoreTrust(userId);
        boolean allowed = trust.scoreReliable;
        Double multiplier = allowed ? resolveLimitMultiplier(finalBScore) : null;
        String reason = allowed
                ? "B 卡分数可信，按激活规则阈值生成额度系数"
                : "B 卡分数不可信，拒绝生成额度系数 dataStatus=" + trust.dataStatus;
        if (!allowed) {
            log.warn("B 卡额度系数被拒绝 userId={} dataStatus={} score={}", userId, trust.dataStatus, finalBScore);
        }
        LimitMultiplierDecision decision = new LimitMultiplierDecision(
                userId,
                multiplier,
                trust.scoreReliable,
                allowed,
                trust.dataStatus,
                trust.modelVersion,
                trust.featureVersion,
                trust.policyVersion != null ? trust.policyVersion : POLICY_VERSION,
                trust.ruleHash,
                reason);
        recordAudit(decision, trust.logId, finalBScore);
        return decision;
    }

    /** 审计失败不得影响额度决策本身（只记录错误日志）。 */
    private void recordAudit(LimitMultiplierDecision decision, Long logId, double score) {
        try {
            decisionAuditRecorder.record(new BCardDecisionAuditRecorder.AuditEntry(
                    decision.userId(),
                    "QUOTA_MULTIPLIER",
                    decision.modelVersion(),
                    null,
                    decision.featureVersion(),
                    decision.policyVersion(),
                    null,
                    decision.ruleHash(),
                    logId == null ? null : "b_card_log:" + logId,
                    decision.dataStatus(),
                    score,
                    null,
                    decision.multiplier(),
                    decision.decisionAllowed()));
        } catch (Exception e) {
            log.error("写 B 卡决策审计失败 userId={}", decision.userId(), e);
        }
    }

    public boolean isLatestScoreReliable(Long userId) {
        return loadLatestScoreTrust(userId).scoreReliable;
    }

    private ScoreTrust loadLatestScoreTrust(Long userId) {
        if (userId == null) {
            return ScoreTrust.unavailable(STATUS_NO_SCORE_LOG);
        }
        UserBCardLog latestLog = userBCardLogMapper.selectOne(
                new QueryWrapper<UserBCardLog>()
                        .eq("user_id", userId)
                        .orderByDesc("created_at")
                        .last("LIMIT 1"));
        if (latestLog == null || latestLog.getLiveFeatures() == null) {
            return ScoreTrust.unavailable(STATUS_NO_SCORE_LOG);
        }
        try {
            Map<String, Object> liveFeatures = objectMapper.readValue(
                    latestLog.getLiveFeatures(), new TypeReference<Map<String, Object>>() {});
            return new ScoreTrust(
                    Boolean.TRUE.equals(liveFeatures.get("scoreReliable")),
                    asString(liveFeatures.get("dataStatus")),
                    asString(liveFeatures.get("modelVersion")),
                    asString(liveFeatures.get("featureVersion")),
                    asString(liveFeatures.get("policyVersion")),
                    asString(liveFeatures.get("ruleHash")),
                    latestLog.getId());
        } catch (Exception e) {
            log.warn("解析 B 卡可信状态失败 userId={}", userId, e);
            return ScoreTrust.unavailable(STATUS_TRUST_PARSE_ERROR);
        }
    }

    private String resolveIdCard(Long userId, String suppliedIdCard) {
        if (suppliedIdCard != null && !suppliedIdCard.isBlank()) {
            return suppliedIdCard;
        }
        UserSummary user = userServiceClient.getUser(userId);
        if (user == null) {
            log.warn("B 卡重算失败：用户不存在 userId={}", userId);
            return null;
        }
        if (user.idCard() == null || user.idCard().isBlank()) {
            log.warn("B 卡重算失败：用户缺少身份证 userId={}", userId);
            return null;
        }
        return user.idCard();
    }

    private void recalculateInternal(Long userId, String idCard, Boolean bCardEnabled) {
        BaseScoreResult baseResult = computeBaseScore(userId, idCard);
        BehaviorLiveFeatureService.LiveFeatures live = behaviorLiveFeatureService.aggregate(userId);
        double delta = live.computeDelta();
        double finalScore = clamp(baseResult.score + delta);

        boolean scoreReliable = baseResult.resolved && live.isHistoryAvailable();
        String dataStatus = resolveDataStatus(baseResult, live);
        RuleMeta ruleMeta = loadRuleMeta();

        // 不可信结果不得回写额度分（上游修复 b513450），避免脏分数影响额度
        if (scoreReliable) {
            loanServiceClient.upsertBehaviorScore(
                    new CreditBehaviorUpsertCommand(userId, bCardEnabled, BigDecimal.valueOf(finalScore)));
        }

        UserBCardLog cardLog = new UserBCardLog();
        cardLog.setUserId(userId);
        cardLog.setBaseScore(BigDecimal.valueOf(baseResult.score));
        cardLog.setDeltaScore(BigDecimal.valueOf(delta));
        cardLog.setFinalScore(BigDecimal.valueOf(finalScore));
        try {
            Map<String, Object> liveMap = new HashMap<>();
            liveMap.put("maxOverdueDays", live.getMaxOverdueDays());
            liveMap.put("overduePeriodCount", live.getOverduePeriodCount());
            liveMap.put("onTimeRate", live.getOnTimeRate());
            liveMap.put("historyAvailable", live.isHistoryAvailable());
            liveMap.put("baseScoreResolved", baseResult.resolved);
            liveMap.put("scoreReliable", scoreReliable);
            liveMap.put("dataStatus", dataStatus);
            liveMap.put("modelVersion", ruleMeta.version);
            liveMap.put("featureVersion", FEATURE_VERSION);
            liveMap.put("policyVersion", POLICY_VERSION);
            liveMap.put("ruleHash", ruleMeta.ruleHash);
            cardLog.setLiveFeatures(objectMapper.writeValueAsString(liveMap));
        } catch (Exception e) {
            cardLog.setLiveFeatures("{}");
        }
        cardLog.setCreatedAt(new Date());
        userBCardLogMapper.insert(cardLog);

        if (!scoreReliable) {
            log.warn("B 卡结果不可用于额度决策 userId={} dataStatus={} baseScore={} delta={}",
                    userId, dataStatus, baseResult.score, delta);
        }
    }

    private BaseScoreResult computeBaseScore(Long userId, String idCard) {
        if (idCard == null || idCard.isBlank()) {
            log.warn("B 卡基础分回退为中性分：缺少身份证 userId={}", userId);
            return BaseScoreResult.unresolved(NEUTRAL_BASE, STATUS_MISSING_ID_CARD);
        }
        UserBehaviorFeatures snap = userBehaviorFeaturesMapper.selectByIdCard(idCard);
        if (snap == null || snap.getFeatureJson() == null || snap.getFeatureJson().isBlank()) {
            log.warn("B 卡基础分回退为中性分：缺少特征快照 userId={}", userId);
            return BaseScoreResult.unresolved(NEUTRAL_BASE, STATUS_FEATURE_NOT_FOUND);
        }
        try {
            Map<String, Double> features = objectMapper.readValue(
                    snap.getFeatureJson(), new TypeReference<Map<String, Double>>() {});
            return BaseScoreResult.resolved(behaviorScoreEngine.calculateBaseScore(features));
        } catch (BehaviorScoreEngineImpl.RuleUnavailableException e) {
            log.warn("B 卡规则不可用，标记为不可信 userId={} dataStatus={}", userId, e.getDataStatus());
            return BaseScoreResult.unresolved(NEUTRAL_BASE, e.getDataStatus());
        } catch (Exception e) {
            log.warn("解析 B 卡特征失败 userId={}", userId, e);
            return BaseScoreResult.unresolved(NEUTRAL_BASE, STATUS_FEATURE_PARSE_ERROR);
        }
    }

    private RuleMeta loadRuleMeta() {
        try {
            BehaviorScoringRules rules = behaviorScoringRulesMapper.selectActiveRule();
            if (rules == null) {
                return RuleMeta.empty();
            }
            return new RuleMeta(rules.getVersion(), sha256Hex(rules.getRuleContent()));
        } catch (Exception e) {
            log.warn("读取 B 卡规则元数据失败", e);
            return RuleMeta.empty();
        }
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

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String resolveDataStatus(
            BaseScoreResult baseResult,
            BehaviorLiveFeatureService.LiveFeatures live) {
        if (!baseResult.resolved) {
            return baseResult.dataStatus;
        }
        if (!live.isHistoryAvailable()) {
            return STATUS_INSUFFICIENT_HISTORY;
        }
        return STATUS_RELIABLE;
    }

    private static double clamp(double score) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, Math.round(score * 10.0) / 10.0));
    }

    private static class BaseScoreResult {
        private final double score;
        private final boolean resolved;
        private final String dataStatus;

        private BaseScoreResult(double score, boolean resolved, String dataStatus) {
            this.score = score;
            this.resolved = resolved;
            this.dataStatus = dataStatus;
        }

        private static BaseScoreResult resolved(double score) {
            return new BaseScoreResult(score, true, STATUS_RELIABLE);
        }

        private static BaseScoreResult unresolved(double score, String dataStatus) {
            return new BaseScoreResult(score, false, dataStatus);
        }
    }

    /** 最新评分日志中的可信度与版本元数据。 */
    private static class ScoreTrust {
        private final boolean scoreReliable;
        private final String dataStatus;
        private final String modelVersion;
        private final String featureVersion;
        private final String policyVersion;
        private final String ruleHash;
        private final Long logId;

        private ScoreTrust(boolean scoreReliable, String dataStatus, String modelVersion,
                           String featureVersion, String policyVersion, String ruleHash, Long logId) {
            this.scoreReliable = scoreReliable;
            this.dataStatus = dataStatus;
            this.modelVersion = modelVersion;
            this.featureVersion = featureVersion;
            this.policyVersion = policyVersion;
            this.ruleHash = ruleHash;
            this.logId = logId;
        }

        private static ScoreTrust unavailable(String dataStatus) {
            return new ScoreTrust(false, dataStatus, null, null, null, null, null);
        }
    }

    /** 激活规则版本与内容哈希。 */
    private static class RuleMeta {
        private final String version;
        private final String ruleHash;

        private RuleMeta(String version, String ruleHash) {
            this.version = version;
            this.ruleHash = ruleHash;
        }

        private static RuleMeta empty() {
            return new RuleMeta(null, null);
        }
    }
}