package org.example.risklendpro.loan.repay;

import org.example.risklendpro.api.dto.LimitMultiplierDecision;
import org.example.risklendpro.loan.client.RiskServiceClient;
import org.example.risklendpro.loan.entity.LimitAdjustLog;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.LimitAdjustLogMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 A0：B 卡自动调额必须在服务端决策放行后才执行；
 * decisionAllowed=false 或 scoreReliable=false 时不得发生任何额度变化。
 */
@ExtendWith(MockitoExtension.class)
class OverdueLimitAdjustTaskTest {

    @InjectMocks
    private OverdueLimitAdjustTask task;

    @Mock
    private UserCreditLimitMapper userCreditLimitMapper;

    @Mock
    private RepaymentPlanMapper repaymentPlanMapper;

    @Mock
    private LimitAdjustLogMapper limitAdjustLogMapper;

    @Mock
    private RiskServiceClient riskServiceClient;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void skipsBcardAdjustmentWhenDecisionNotAllowed() {
        setSwitches(true, false);
        UserCreditLimit limit = creditLimit(1L, "10000", "0", true, "400");
        prepareSingleOverdueUser(limit, "M1");
        when(riskServiceClient.getLimitMultiplierForUser(1L, 400.0)).thenReturn(
                new LimitMultiplierDecision(1L, null, false, false, "FEATURE_NOT_FOUND",
                        null, null, "BCARD_LIMIT_POLICY_V1", null, "B 卡分数不可信"));

        task.autoAdjustOverdueUserLimit();

        verify(riskServiceClient).getLimitMultiplierForUser(1L, 400.0);
        verify(userCreditLimitMapper, never()).updateById(any(UserCreditLimit.class));
        verify(limitAdjustLogMapper, never()).insert(any(LimitAdjustLog.class));
    }

    @Test
    void skipsBcardAdjustmentWhenNoDecisionObject() {
        setSwitches(true, false);
        UserCreditLimit limit = creditLimit(11L, "10000", "0", true, "400");
        prepareSingleOverdueUser(limit, "M1");
        when(riskServiceClient.getLimitMultiplierForUser(11L, 400.0)).thenReturn(null);

        task.autoAdjustOverdueUserLimit();

        verify(userCreditLimitMapper, never()).updateById(any(UserCreditLimit.class));
        verify(limitAdjustLogMapper, never()).insert(any(LimitAdjustLog.class));
    }

    @Test
    void appliesBcardAdjustmentWhenDecisionAllowedWithAuditFields() {
        setSwitches(true, false);
        UserCreditLimit limit = creditLimit(2L, "10000", "0", true, "400");
        prepareSingleOverdueUser(limit, "M1");
        when(riskServiceClient.getLimitMultiplierForUser(2L, 400.0)).thenReturn(
                new LimitMultiplierDecision(2L, 0.8, true, true, "RELIABLE", "v1.0-b-woe",
                        "HC_STATIC_V1+LIVE_V1", "BCARD_LIMIT_POLICY_V1", "abcdef123456", "ok"));

        task.autoAdjustOverdueUserLimit();

        assertEquals(0, new BigDecimal("8000").compareTo(limit.getTotalLimit()));
        verify(userCreditLimitMapper).updateById(limit);

        ArgumentCaptor<LimitAdjustLog> captor = ArgumentCaptor.forClass(LimitAdjustLog.class);
        verify(limitAdjustLogMapper).insert(captor.capture());
        String reason = captor.getValue().getReason();
        assertTrue(reason.startsWith("B卡自动调额"), "reason=" + reason);
        assertTrue(reason.contains("audit="), "reason=" + reason);
        assertTrue(reason.contains("modelVersion"), "reason=" + reason);
        assertTrue(reason.contains("featureVersion"), "reason=" + reason);
        assertTrue(reason.contains("dataStatus"), "reason=" + reason);
        assertTrue(reason.contains("decisionAllowed"), "reason=" + reason);
        assertTrue(reason.contains("ruleHash"), "reason=" + reason);
        assertTrue(reason.length() <= 500, "reason too long: " + reason.length());
    }

    @Test
    void doesNotAdjustWhenBcardSwitchOffAndRuleSwitchOff() {
        setSwitches(false, false);
        UserCreditLimit limit = creditLimit(3L, "10000", "0", true, "300");
        prepareSingleOverdueUser(limit, "M3");

        task.autoAdjustOverdueUserLimit();

        verify(riskServiceClient, never()).getLimitMultiplierForUser(anyLong(), anyDouble());
        verify(userCreditLimitMapper, never()).updateById(any(UserCreditLimit.class));
        verify(limitAdjustLogMapper, never()).insert(any(LimitAdjustLog.class));
    }

    @Test
    void ruleReductionAppliesIndependentlyOfBcardSwitch() {
        setSwitches(false, true);
        UserCreditLimit limit = creditLimit(4L, "10000", "0", true, "300");
        prepareSingleOverdueUser(limit, "M2");
        when(limitAdjustLogMapper.selectCount(any())).thenReturn(0L);

        task.autoAdjustOverdueUserLimit();

        assertEquals(0, new BigDecimal("5000").compareTo(limit.getTotalLimit()));
        verify(riskServiceClient, never()).getLimitMultiplierForUser(anyLong(), anyDouble());
        verify(limitAdjustLogMapper).insert(any(LimitAdjustLog.class));
    }

    private void setSwitches(boolean bCardAutoAdjustEnabled, boolean overdueLevelReduceEnabled) {
        ReflectionTestUtils.setField(task, "bCardAutoAdjustEnabled", bCardAutoAdjustEnabled);
        ReflectionTestUtils.setField(task, "overdueLevelReduceEnabled", overdueLevelReduceEnabled);
    }

    private void prepareSingleOverdueUser(UserCreditLimit limit, String overdueLevel) {
        when(userCreditLimitMapper.selectList(any())).thenReturn(List.of(limit));
        when(riskServiceClient.recalculateBehaviorScore(limit.getUserId())).thenReturn(null);
        // 合并上游后：额度行用 selectOne(... FOR UPDATE) 锁定，逾期计划用 selectList 取最严重等级
        when(userCreditLimitMapper.selectOne(any())).thenReturn(limit);
        when(repaymentPlanMapper.selectList(any())).thenReturn(List.of(overduePlan(limit.getUserId(), overdueLevel)));
    }

    private UserCreditLimit creditLimit(Long userId, String total, String used,
                                        boolean bCardEnabled, String bScore) {
        UserCreditLimit limit = new UserCreditLimit();
        limit.setId(userId);
        limit.setUserId(userId);
        limit.setTotalLimit(new BigDecimal(total));
        limit.setUsedLimit(new BigDecimal(used));
        limit.setRemainingLimit(new BigDecimal(total).subtract(new BigDecimal(used)));
        limit.setHasOverdue(true);
        limit.setBCardEnabled(bCardEnabled);
        limit.setBScore(new BigDecimal(bScore));
        return limit;
    }

    private RepaymentPlan overduePlan(Long userId, String overdueLevel) {
        RepaymentPlan plan = new RepaymentPlan();
        plan.setUserId(userId);
        plan.setStatus("OVERDUE");
        plan.setOverdueLevel(overdueLevel);
        return plan;
    }
}