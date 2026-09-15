package org.example.risklendpro.loan.repay;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.loan.entity.LimitAdjustLog;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.LimitAdjustLogMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.loan.client.RiskServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Component
public class OverdueLimitAdjustTask {

    private static final double MIN_SINGLE_ADJUST_MULTIPLIER = 0.5;
    private static final String B_CARD_REASON_PREFIX = "B卡自动调额";
    private static final String RULE_REASON_PREFIX = "系统自动调整";

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private LimitAdjustLogMapper limitAdjustLogMapper;

    @Autowired
    private RiskServiceClient riskServiceClient;

    /**
     * P0 默认关闭 B 卡自动降额，避免当前模型继续影响生产额度。
     * 修复并完成影子验收后，再通过配置显式开启。
     */
    @Value("${risk.b-card.auto-adjust-enabled:false}")
    private boolean bCardAutoAdjustEnabled;

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void autoAdjustOverdueUserLimit() {
        QueryWrapper<UserCreditLimit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("has_overdue", true);
        List<UserCreditLimit> overdueUsers = userCreditLimitMapper.selectList(queryWrapper);

        for (UserCreditLimit creditLimit : overdueUsers) {
            if (Boolean.TRUE.equals(creditLimit.getBCardEnabled())) {
                riskServiceClient.recalculateBehaviorScore(creditLimit.getUserId());
                creditLimit = userCreditLimitMapper.selectById(creditLimit.getId());
            }
            adjustUserLimitByOverdue(creditLimit);
        }
    }

    private void adjustUserLimitByOverdue(UserCreditLimit creditLimit) {
        QueryWrapper<RepaymentPlan> planQuery = new QueryWrapper<>();
        planQuery.eq("user_id", creditLimit.getUserId());
        planQuery.eq("status", "OVERDUE");
        RepaymentPlan overduePlan = repaymentPlanMapper.selectOne(planQuery);

        if (overduePlan == null || creditLimit.getTotalLimit() == null) {
            return;
        }

        String overdueLevel = normalizeOverdueLevel(overduePlan.getOverdueLevel());
        BigDecimal oldLimit = creditLimit.getTotalLimit();

        if (bCardAutoAdjustEnabled
                && Boolean.TRUE.equals(creditLimit.getBCardEnabled())
                && creditLimit.getBScore() != null) {
            String decisionKey = B_CARD_REASON_PREFIX + "[level=" + overdueLevel + "]";
            if (hasExistingAdjustment(creditLimit.getUserId(), decisionKey)) {
                return;
            }

            double multiplier = riskServiceClient.getLimitMultiplier(creditLimit.getBScore().doubleValue());
            multiplier = Math.max(MIN_SINGLE_ADJUST_MULTIPLIER, Math.min(1.0, multiplier));
            BigDecimal newLimit = oldLimit.multiply(BigDecimal.valueOf(multiplier));
            applyLimitChange(creditLimit, oldLimit, newLimit,
                    decisionKey + "：B分=" + creditLimit.getBScore() + "，系数=" + multiplier);
            return;
        }

        if (hasExistingRuleAdjustment(creditLimit.getUserId(), overdueLevel)) {
            return;
        }

        BigDecimal newLimit;
        switch (overdueLevel) {
            case "M1":
                newLimit = oldLimit.multiply(new BigDecimal("0.8"));
                break;
            case "M2":
                newLimit = oldLimit.multiply(new BigDecimal("0.5"));
                break;
            case "M3":
                newLimit = oldLimit.multiply(new BigDecimal("0.2"));
                break;
            case "M4":
                newLimit = BigDecimal.ZERO;
                break;
            default:
                return;
        }

        applyLimitChange(creditLimit, oldLimit, newLimit,
                RULE_REASON_PREFIX + "[level=" + overdueLevel + "]：用户逾期等级为"
                        + overdueLevel + "，根据风控规则调整额度");
    }

    private boolean hasExistingAdjustment(Long userId, String reasonKey) {
        Long count = limitAdjustLogMapper.selectCount(
                new QueryWrapper<LimitAdjustLog>()
                        .eq("user_id", userId)
                        .likeRight("reason", reasonKey));
        return count != null && count > 0;
    }

    private boolean hasExistingRuleAdjustment(Long userId, String overdueLevel) {
        Long count = limitAdjustLogMapper.selectCount(
                new QueryWrapper<LimitAdjustLog>()
                        .eq("user_id", userId)
                        .like("reason", "用户逾期等级为" + overdueLevel));
        return count != null && count > 0;
    }

    private static String normalizeOverdueLevel(String overdueLevel) {
        return overdueLevel == null || overdueLevel.isBlank() ? "UNKNOWN" : overdueLevel;
    }

    private void applyLimitChange(UserCreditLimit creditLimit, BigDecimal oldLimit, BigDecimal newLimit, String reason) {
        if (newLimit.compareTo(oldLimit) == 0) {
            return;
        }

        creditLimit.setTotalLimit(newLimit);
        BigDecimal difference = newLimit.subtract(oldLimit);
        BigDecimal newRemainingLimit = creditLimit.getRemainingLimit().add(difference);

        if (newRemainingLimit.compareTo(BigDecimal.ZERO) < 0) {
            newRemainingLimit = BigDecimal.ZERO;
        }

        creditLimit.setRemainingLimit(newRemainingLimit);
        creditLimit.setLastUpdateTime(new Date());
        userCreditLimitMapper.updateById(creditLimit);

        LimitAdjustLog log = new LimitAdjustLog();
        log.setUserId(creditLimit.getUserId());
        log.setOldLimit(oldLimit);
        log.setNewLimit(newLimit);
        log.setReason(reason);
        log.setOperatorId(0L);
        log.setAdjustTime(new Date());
        limitAdjustLogMapper.insert(log);
    }
}