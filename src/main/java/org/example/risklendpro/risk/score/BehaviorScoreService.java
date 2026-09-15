package org.example.risklendpro.risk.score;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.risk.credit.UserBehaviorFeatures;
import org.example.risklendpro.risk.credit.mapper.UserBehaviorFeaturesMapper;
import org.example.risklendpro.risk.entity.UserBCardLog;
import org.example.risklendpro.risk.mapper.UserBCardLogMapper;
import org.example.risklendpro.user.entity.User;
import org.example.risklendpro.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Autowired
    private BehaviorScoreEngine behaviorScoreEngine;

    @Autowired
    private BehaviorLiveFeatureService behaviorLiveFeatureService;

    @Autowired
    private UserBehaviorFeaturesMapper userBehaviorFeaturesMapper;

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private UserBCardLogMapper userBCardLogMapper;

    @Autowired
    private UserMapper userMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void activate(Long userId, String idCard) {
        if (userId == null) {
            return;
        }
        UserCreditLimit limit = getOrCreateLimit(userId);
        limit.setBCardEnabled(true);
        recalculateInternal(userId, resolveIdCard(userId, idCard), limit);
        userCreditLimitMapper.updateById(limit);
        log.info("B 卡已启动 userId={} finalBScore={}", userId, limit.getBScore());
    }

    @Transactional
    public void recalculate(Long userId) {
        if (userId == null) {
            return;
        }
        UserCreditLimit limit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId));
        if (limit == null || !Boolean.TRUE.equals(limit.getBCardEnabled())) {
            return;
        }
        recalculateInternal(userId, resolveIdCard(userId, null), limit);
        userCreditLimitMapper.updateById(limit);
    }

    public boolean isBCardEnabled(Long userId) {
        UserCreditLimit limit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId));
        return limit != null && Boolean.TRUE.equals(limit.getBCardEnabled());
    }

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

    public Double resolveLimitMultiplierForUser(Long userId, double finalBScore) {
        if (!isLatestScoreReliable(userId)) {
            return null;
        }
        return resolveLimitMultiplier(finalBScore);
    }

    public boolean isLatestScoreReliable(Long userId) {
        if (userId == null) {
            return false;
        }
        UserBCardLog latestLog = userBCardLogMapper.selectOne(
                new QueryWrapper<UserBCardLog>()
                        .eq("user_id", userId)
                        .orderByDesc("created_at")
                        .last("LIMIT 1"));
        if (latestLog == null || latestLog.getLiveFeatures() == null) {
            return false;
        }
        try {
            Map<String, Object> liveFeatures = objectMapper.readValue(
                    latestLog.getLiveFeatures(), new TypeReference<Map<String, Object>>() {});
            return Boolean.TRUE.equals(liveFeatures.get("scoreReliable"));
        } catch (Exception e) {
            log.warn("解析 B 卡可信状态失败 userId={}", userId, e);
            return false;
        }
    }

    private String resolveIdCard(Long userId, String suppliedIdCard) {
        if (suppliedIdCard != null && !suppliedIdCard.isBlank()) {
            return suppliedIdCard;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("B 卡重算失败：用户不存在 userId={}", userId);
            return null;
        }
        if (user.getIdCard() == null || user.getIdCard().isBlank()) {
            log.warn("B 卡重算失败：用户缺少身份证 userId={}", userId);
            return null;
        }
        return user.getIdCard();
    }

    private void recalculateInternal(Long userId, String idCard, UserCreditLimit limit) {
        BaseScoreResult baseResult = computeBaseScore(userId, idCard);
        BehaviorLiveFeatureService.LiveFeatures live = behaviorLiveFeatureService.aggregate(userId);
        double delta = live.computeDelta();
        double finalScore = clamp(baseResult.score + delta);

        boolean scoreReliable = baseResult.resolved && live.isHistoryAvailable();
        String dataStatus = resolveDataStatus(baseResult, live);

        limit.setBScore(BigDecimal.valueOf(finalScore));
        limit.setBScoreUpdatedAt(new Date());

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
        } catch (Exception e) {
            log.warn("解析 B 卡特征失败 userId={}", userId, e);
            return BaseScoreResult.unresolved(NEUTRAL_BASE, STATUS_FEATURE_PARSE_ERROR);
        }
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

    private UserCreditLimit getOrCreateLimit(Long userId) {
        UserCreditLimit limit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId));
        if (limit != null) {
            return limit;
        }
        limit = new UserCreditLimit();
        limit.setUserId(userId);
        limit.setTotalLimit(BigDecimal.ZERO);
        limit.setUsedLimit(BigDecimal.ZERO);
        limit.setRemainingLimit(BigDecimal.ZERO);
        limit.setOverdueAmount(BigDecimal.ZERO);
        limit.setHasOverdue(false);
        limit.setBCardEnabled(false);
        limit.setLastUpdateTime(new Date());
        userCreditLimitMapper.insert(limit);
        return limit;
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
}
