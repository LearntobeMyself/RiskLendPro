package org.example.risklendpro.risk.client;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.api.contract.RiskDecisionApi;
import org.example.risklendpro.api.dto.BehaviorScoreSnapshot;
import org.example.risklendpro.api.dto.RiskAssessmentSummary;
import org.example.risklendpro.api.dto.RiskOverviewCounts;
import org.example.risklendpro.risk.entity.RiskAssessment;
import org.example.risklendpro.risk.entity.UserBCardLog;
import org.example.risklendpro.risk.mapper.RiskAssessmentMapper;
import org.example.risklendpro.risk.mapper.UserBCardLogMapper;
import org.example.risklendpro.risk.score.BehaviorScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * risk-service 对外契约实现，供 loan/user 通过 Feign 消费。
 */
@RestController
public class InternalRiskController implements RiskDecisionApi {

    @Autowired
    private RiskAssessmentMapper riskAssessmentMapper;

    @Autowired
    private UserBCardLogMapper userBCardLogMapper;

    @Autowired
    private BehaviorScoreService behaviorScoreService;

    @Override
    public RiskAssessmentSummary getLatestFinalAssessment(Long userId) {
        RiskAssessment assessment = riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>()
                        .eq("user_id", userId)
                        .eq("is_final", true)
                        .orderByDesc("approval_time")
                        .last("LIMIT 1")
        );
        if (assessment == null) {
            return null;
        }
        return new RiskAssessmentSummary(
                assessment.getApplyId(),
                assessment.getUserId(),
                assessment.getStatus(),
                assessment.getSysDecision(),
                assessment.getTotalScore(),
                assessment.getCreditLimit(),
                Boolean.TRUE.equals(assessment.getIsFinal()),
                assessment.getIdCard()
        );
    }

    @Override
    public BehaviorScoreSnapshot getBehaviorScore(Long userId) {
        return loadLatestSnapshot(userId);
    }

    @Override
    public BehaviorScoreSnapshot recalculateBehaviorScore(Long userId) {
        behaviorScoreService.recalculate(userId);
        return loadLatestSnapshot(userId);
    }

    @Override
    public BehaviorScoreSnapshot activateBehaviorScore(Long userId, String idCard) {
        behaviorScoreService.activate(userId, idCard);
        return loadLatestSnapshot(userId);
    }

    @Override
    public RiskOverviewCounts getOverviewCounts() {
        long total = riskAssessmentMapper.selectCount(null);
        long pending = riskAssessmentMapper.selectCount(
                new QueryWrapper<RiskAssessment>().eq("status", "MANUAL_REVIEW"));
        return new RiskOverviewCounts(total, pending);
    }

    @Override
    public double getLimitMultiplier(double score) {
        return behaviorScoreService.resolveLimitMultiplier(score);
    }

    private BehaviorScoreSnapshot loadLatestSnapshot(Long userId) {
        UserBCardLog latest = userBCardLogMapper.selectOne(
                new QueryWrapper<UserBCardLog>()
                        .eq("user_id", userId)
                        .orderByDesc("created_at")
                        .last("LIMIT 1")
        );
        if (latest == null) {
            return new BehaviorScoreSnapshot(userId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "NONE");
        }
        BigDecimal finalScore = latest.getFinalScore() == null ? BigDecimal.ZERO : latest.getFinalScore();
        return new BehaviorScoreSnapshot(
                userId,
                nvl(latest.getBaseScore()),
                nvl(latest.getDeltaScore()),
                finalScore,
                resolveLevel(finalScore.doubleValue())
        );
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String resolveLevel(double score) {
        if (score >= 700) {
            return "HIGH";
        }
        if (score >= 600) {
            return "MID";
        }
        return "LOW";
    }
}
