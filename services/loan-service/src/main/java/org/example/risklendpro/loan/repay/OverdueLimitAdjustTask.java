package org.example.risklendpro.loan.repay;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.api.dto.LimitMultiplierDecision;
import org.example.risklendpro.loan.entity.LimitAdjustLog;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.LimitAdjustLogMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.loan.client.RiskServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OverdueLimitAdjustTask {

    private static final Logger log = LoggerFactory.getLogger(OverdueLimitAdjustTask.class);

    private static final double MIN_SINGLE_ADJUST_MULTIPLIER = 0.5;
    private static final int REASON_MAX_LENGTH = 500;
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

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 开关一：B 卡自动调额。P0 默认关闭，影子验收通过并经人工批准后才可显式开启。
     */
    @Value("${risk.b-card.auto-adjust-enabled:false}")
    private boolean bCardAutoAdjustEnabled;

    /**
     * 开关二：逾期等级规则降额（M1/M2/M3/M4）。与 B 卡自动调额相互独立。
     */
    @Value("${risk.overdue-level.reduce-enabled:true}")
    private boolean overdueLevelReduceEnabled;

    @Scheduled(cron = "0 0 2 * * ?")
    @SchedulerLock(name = "overdueLimitAdjust", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10S")
    public void autoAdjustOverdueUserLimit() {
        QueryWrapper<UserCreditLimit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("has_overdue", true);
        List<UserCreditLimit> overdueUsers = userCreditLimitMapper.selectList(queryWrapper);

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        for (UserCreditLimit creditLimit : overdueUsers) {
            try {
                // Feign 调用放在事务外，避免长事务占用数据库连接与行锁
                if (Boolean.TRUE.equals(creditLimit.getBCardEnabled())) {
                    riskServiceClient.recalculateBehaviorScore(creditLimit.getUserId());
                }
                // 每用户独立事务：单个用户失败仅跳过本人，不再整批回滚
                txTemplate.executeWithoutResult(status -> adjustUserLimitByOverdue(creditLimit));
            } catch (Exception e) {
                log.warn("逾期调额处理失败，跳过该用户 userId={}: {}", creditLimit.getUserId(), e.getMessage());
            }
        }
    }

    private void adjustUserLimitByOverdue(UserCreditLimit creditLimit) {
        if (creditLimit == null || creditLimit.getUserId() == null) {
            return;
        }
        // 锁定额度行，保证并发下调额判断与写入一致
        creditLimit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>()
                        .eq("user_id", creditLimit.getUserId())
                        .last("FOR UPDATE"));
        if (creditLimit == null || creditLimit.getTotalLimit() == null) {
            return;
        }

        // 用户可能有多笔逾期计划：取最严重等级（上游 ad0a430）
        List<RepaymentPlan> overduePlans = repaymentPlanMapper.selectList(
                new QueryWrapper<RepaymentPlan>()
                        .eq("user_id", creditLimit.getUserId())
                        .eq("status", "OVERDUE"));
        RepaymentPlan overduePlan = pickWorstOverduePlan(overduePlans);
        if (overduePlan == null) {
            return;
        }

        String overdueLevel = normalizeOverdueLevel(overduePlan.getOverdueLevel());

        if (adjustByBCardIfAllowed(creditLimit, overdueLevel)) {
            return;
        }

        if (!overdueLevelReduceEnabled) {
            return;
        }

        adjustByOverdueLevelRule(creditLimit, overdueLevel);
    }

    /**
     * B 卡自动调额分支：开关开启 + 用户启用 B 卡 + 有 B 分 + 服务端决策放行，才会调额。
     *
     * @return true 表示 B 卡分支已经处理（已调额或已明确跳过）
     */
    private boolean adjustByBCardIfAllowed(UserCreditLimit creditLimit, String overdueLevel) {
        if (!bCardAutoAdjustEnabled) {
            return false;
        }
        if (!Boolean.TRUE.equals(creditLimit.getBCardEnabled()) || creditLimit.getBScore() == null) {
            return false;
        }

        String decisionKey = B_CARD_REASON_PREFIX + "[level=" + overdueLevel + "]";
        if (hasExistingAdjustment(creditLimit.getUserId(), decisionKey)) {
            return true;
        }

        LimitMultiplierDecision decision = riskServiceClient.getLimitMultiplierForUser(
                creditLimit.getUserId(), creditLimit.getBScore().doubleValue());

        if (decision == null || !decision.decisionAllowed() || decision.multiplier() == null) {
            log.warn("B 卡自动调额已跳过（保持原额度）userId={} decisionKey={} decisionAllowed={} dataStatus={} reason={}",
                    creditLimit.getUserId(),
                    decisionKey,
                    decision == null ? null : decision.decisionAllowed(),
                    decision == null ? "NO_DECISION" : decision.dataStatus(),
                    decision == null ? "远程未返回决策对象" : decision.reason());
            return true;
        }

        BigDecimal oldLimit = creditLimit.getTotalLimit();
        double multiplier = Math.max(MIN_SINGLE_ADJUST_MULTIPLIER, Math.min(1.0, decision.multiplier()));
        BigDecimal newLimit = oldLimit.multiply(BigDecimal.valueOf(multiplier));
        String reason = decisionKey + "：B分=" + creditLimit.getBScore() + "，系数=" + multiplier
                + " | audit=" + buildAuditJson(decision, multiplier);
        applyLimitChange(creditLimit, oldLimit, newLimit, reason);
        return true;
    }

    private void adjustByOverdueLevelRule(UserCreditLimit creditLimit, String overdueLevel) {
        if (hasExistingRuleAdjustment(creditLimit.getUserId(), overdueLevel)) {
            return;
        }

        BigDecimal oldLimit = creditLimit.getTotalLimit();
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

    /** 审计字段（等价 JSON）：模型/特征/策略版本、数据状态、是否放行、规则哈希与生效系数。 */
    private String buildAuditJson(LimitMultiplierDecision decision, double appliedMultiplier) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("modelVersion", decision.modelVersion());
        audit.put("featureVersion", decision.featureVersion());
        audit.put("policyVersion", decision.policyVersion());
        audit.put("dataStatus", decision.dataStatus());
        audit.put("decisionAllowed", decision.decisionAllowed());
        audit.put("scoreReliable", decision.scoreReliable());
        audit.put("ruleHash", decision.ruleHash());
        audit.put("multiplier", appliedMultiplier);
        try {
            return objectMapper.writeValueAsString(audit);
        } catch (Exception e) {
            log.warn("序列化额度调整审计字段失败 userId={}", decision.userId(), e);
            return "{\"modelVersion\":\"" + decision.modelVersion() + "\",\"dataStatus\":\""
                    + decision.dataStatus() + "\"}";
        }
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

    private static RepaymentPlan pickWorstOverduePlan(List<RepaymentPlan> overduePlans) {
        if (overduePlans == null || overduePlans.isEmpty()) {
            return null;
        }
        RepaymentPlan worst = overduePlans.get(0);
        for (RepaymentPlan plan : overduePlans) {
            if (overdueRank(plan.getOverdueLevel()) > overdueRank(worst.getOverdueLevel())) {
                worst = plan;
            }
        }
        return worst;
    }

    private static int overdueRank(String level) {
        String normalized = normalizeOverdueLevel(level);
        return switch (normalized) {
            case "M4" -> 4;
            case "M3" -> 3;
            case "M2" -> 2;
            case "M1" -> 1;
            default -> 0;
        };
    }

    private static String normalizeOverdueLevel(String overdueLevel) {
        return overdueLevel == null || overdueLevel.isBlank() ? "UNKNOWN" : overdueLevel;
    }

    private void applyLimitChange(UserCreditLimit creditLimit, BigDecimal oldLimit, BigDecimal newLimit, String reason) {
        if (newLimit.compareTo(oldLimit) == 0) {
            return;
        }

        // 逾期降额不能使总额度低于已用额度（在贷余额）
        BigDecimal used = creditLimit.getUsedLimit() != null ? creditLimit.getUsedLimit() : BigDecimal.ZERO;
        if (newLimit.compareTo(used) < 0) {
            newLimit = used;
        }

        creditLimit.setTotalLimit(newLimit);
        BigDecimal newRemainingLimit = newLimit.subtract(used);
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
        log.setReason(truncateReason(reason));
        log.setOperatorId(0L);
        log.setAdjustTime(new Date());
        limitAdjustLogMapper.insert(log);
    }

    private static String truncateReason(String reason) {
        if (reason == null || reason.length() <= REASON_MAX_LENGTH) {
            return reason;
        }
        return reason.substring(0, REASON_MAX_LENGTH);
    }
}