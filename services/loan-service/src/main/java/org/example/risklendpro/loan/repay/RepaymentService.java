package org.example.risklendpro.loan.repay;

import org.example.risklendpro.loan.repay.RepaymentExecuteRequest;
import org.example.risklendpro.loan.repay.RepaymentResponse;
import java.util.List;
import java.util.Map;

public interface RepaymentService {
    /**
     * 查看还款计划
     */
    List<Object> getRepaymentPlans(Long userId);

    /**
     * 执行还款
     *
     * @param userId  当前操作的用户 ID（调用方必须从登录上下文传入）
     * @param request 还款请求
     */
    RepaymentResponse executeRepayment(Long userId, RepaymentExecuteRequest request);

    /**
     * 获取还款记录详情
     */
    List<Object> getRepaymentRecords(Long userId, Long planId, String status);

    /**
     * 获取还款统计
     */
    Map<String, Long> getRepaymentStatistics();

    /**
     * 获取逾期统计
     */
    Map<String, Long> getOverdueStatistics();
}
