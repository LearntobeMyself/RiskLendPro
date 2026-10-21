package org.example.risklendpro.loan.repay;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 逾期状态幂等同步：以"用户所有 OVERDUE 计划下的 OVERDUE 还款记录"为事实源，
 * 重算并回写 user_credit_limit 的 hasOverdue / overdueAmount。
 * 供首逾落库、逾期自愈、还款后刷新三处调用，替代易漏的增量累加，
 * 保证任意环节中断后下一次调用都能自愈到正确状态。
 */
@Component
public class OverdueStateSync {

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    public void syncUserOverdueState(Long userId) {
        if (userId == null) {
            return;
        }

        List<RepaymentPlan> overduePlans = repaymentPlanMapper.selectList(
                new QueryWrapper<RepaymentPlan>()
                        .eq("user_id", userId)
                        .eq("status", "OVERDUE"));

        BigDecimal overdueAmount = BigDecimal.ZERO;
        for (RepaymentPlan plan : overduePlans) {
            List<RepaymentRecord> overdueRecords = repaymentRecordMapper.selectList(
                    new QueryWrapper<RepaymentRecord>()
                            .eq("plan_id", plan.getPlanId())
                            .eq("status", "OVERDUE"));
            for (RepaymentRecord record : overdueRecords) {
                if (record.getAmount() != null) {
                    overdueAmount = overdueAmount.add(record.getAmount());
                }
            }
        }

        UserCreditLimit limit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId).last("FOR UPDATE"));
        if (limit == null) {
            return;
        }
        limit.setHasOverdue(overdueAmount.compareTo(BigDecimal.ZERO) > 0);
        limit.setOverdueAmount(overdueAmount);
        limit.setLastUpdateTime(new Date());
        userCreditLimitMapper.updateById(limit);
    }
}
