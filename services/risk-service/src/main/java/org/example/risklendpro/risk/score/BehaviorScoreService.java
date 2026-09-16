package org.example.risklendpro.risk.score;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.api.dto.CreditBehaviorUpsertCommand;
import org.example.risklendpro.api.dto.CreditLimitSnapshot;
import org.example.risklendpro.risk.client.LoanServiceClient;
import org.example.risklendpro.risk.entity.UserBCardLog;
import org.example.risklendpro.risk.credit.UserBehaviorFeatures;
import org.example.risklendpro.risk.mapper.UserBCardLogMapper;
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
    private UserBCardLogMapper userBCardLogMapper;

    @Autowired
    private LoanServiceClient loanServiceClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void activate(Long userId, String idCard) {
        if (userId == null) {
            return;
        }
        loanServiceClient.ensureCreditLimit(userId);
        recalculateInternal(userId, idCard, true);
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
        recalculateInternal(userId, null, null);
    }

    public boolean isBCardEnabled(Long userId) {
        CreditLimitSnapshot limit = loanServiceClient.getCreditLimit(userId);
        return limit != null && limit.bCardEnabled();
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

    private void recalculateInternal(Long userId, String idCard, Boolean bCardEnabled) {
        double base = computeBaseScore(idCard);
        BehaviorLiveFeatureService.LiveFeatures live = behaviorLiveFeatureService.aggregate(userId);
        double delta = live.computeDelta();
        double finalScore = clamp(base + delta);

        loanServiceClient.upsertBehaviorScore(
                new CreditBehaviorUpsertCommand(userId, bCardEnabled, BigDecimal.valueOf(finalScore)));

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
}
