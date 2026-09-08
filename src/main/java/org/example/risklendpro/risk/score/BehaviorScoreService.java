package org.example.risklendpro.risk.score;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.risk.entity.UserBCardLog;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.risk.credit.UserBehaviorFeatures;
import org.example.risklendpro.risk.mapper.UserBCardLogMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.risk.credit.mapper.UserBehaviorFeaturesMapper;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void activate(Long userId, String idCard) {
        if (userId == null) {
            return;
        }
        UserCreditLimit limit = getOrCreateLimit(userId);
        limit.setBCardEnabled(true);
        recalculateInternal(userId, idCard, limit);
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
        recalculateInternal(userId, null, limit);
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

    private void recalculateInternal(Long userId, String idCard, UserCreditLimit limit) {
        double base = computeBaseScore(idCard);
        BehaviorLiveFeatureService.LiveFeatures live = behaviorLiveFeatureService.aggregate(userId);
        double delta = live.computeDelta();
        double finalScore = clamp(base + delta);

        limit.setBScore(BigDecimal.valueOf(finalScore));
        limit.setBScoreUpdatedAt(new Date());

        UserBCardLog cardLog = new UserBCardLog();
        cardLog.setUserId(userId);
        cardLog.setBaseScore(BigDecimal.valueOf(base));
        cardLog.setDeltaScore(BigDecimal.valueOf(delta));
        cardLog.setFinalScore(BigDecimal.valueOf(finalScore));
        try {
            Map<String, Object> liveMap = new HashMap<>();
            liveMap.put("maxOverdueDays", live.getMaxOverdueDays());
            liveMap.put("overduePeriodCount", live.getOverduePeriodCount());
            liveMap.put("onTimeRate", live.getOnTimeRate());
            cardLog.setLiveFeatures(objectMapper.writeValueAsString(liveMap));
        } catch (Exception e) {
            cardLog.setLiveFeatures("{}");
        }
        cardLog.setCreatedAt(new Date());
        userBCardLogMapper.insert(cardLog);
    }

    private double computeBaseScore(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return NEUTRAL_BASE;
        }
        UserBehaviorFeatures snap = userBehaviorFeaturesMapper.selectByIdCard(idCard);
        if (snap == null || snap.getFeatureJson() == null || snap.getFeatureJson().isBlank()) {
            return NEUTRAL_BASE;
        }
        try {
            Map<String, Double> features = objectMapper.readValue(
                    snap.getFeatureJson(), new TypeReference<Map<String, Double>>() {});
            return behaviorScoreEngine.calculateBaseScore(features);
        } catch (Exception e) {
            log.warn("解析 B 卡特征失败 idCard={}", idCard, e);
            return NEUTRAL_BASE;
        }
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
}
