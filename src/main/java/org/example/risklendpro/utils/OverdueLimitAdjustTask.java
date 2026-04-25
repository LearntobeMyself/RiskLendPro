package org.example.risklendpro.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.entity.LimitAdjustLog;
import org.example.risklendpro.entity.RepaymentPlan;
import org.example.risklendpro.entity.UserCreditLimit;
import org.example.risklendpro.mapper.LimitAdjustLogMapper;
import org.example.risklendpro.mapper.RepaymentPlanMapper;
import org.example.risklendpro.mapper.UserCreditLimitMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Component
public class OverdueLimitAdjustTask {

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private LimitAdjustLogMapper limitAdjustLogMapper;

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void autoAdjustOverdueUserLimit() {
        QueryWrapper<UserCreditLimit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("has_overdue", true);
        List<UserCreditLimit> overdueUsers = userCreditLimitMapper.selectList(queryWrapper);

        for (UserCreditLimit creditLimit : overdueUsers) {
            adjustUserLimitByOverdue(creditLimit);
        }
    }

    private void adjustUserLimitByOverdue(UserCreditLimit creditLimit) {
        QueryWrapper<RepaymentPlan> planQuery = new QueryWrapper<>();
        planQuery.eq("user_id", creditLimit.getUserId());
        planQuery.eq("status", "OVERDUE");
        RepaymentPlan overduePlan = repaymentPlanMapper.selectOne(planQuery);

        if (overduePlan == null) {
            return;
        }

        String overdueLevel = overduePlan.getOverdueLevel();
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
        log.setReason("系统自动调整：用户逾期等级为" + overdueLevel + "，根据风控规则调整额度");
        log.setOperatorId(0L);
        log.setAdjustTime(new Date());
        limitAdjustLogMapper.insert(log);
    }
}
