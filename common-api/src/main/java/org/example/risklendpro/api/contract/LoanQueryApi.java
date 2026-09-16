package org.example.risklendpro.api.contract;

import org.example.risklendpro.api.dto.BCardRepaymentSnapshot;
import org.example.risklendpro.api.dto.LoanBehaviorSnapshot;
import org.example.risklendpro.api.dto.LoanUserSummaryItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 贷款域行为/汇总契约。由 loan-service 实现，risk（行为分、B卡监控）与 user（管理端）消费。
 */
public interface LoanQueryApi {

    @GetMapping("/internal/loans/users/{userId}/behavior")
    LoanBehaviorSnapshot getLoanBehavior(@PathVariable("userId") Long userId);

    @GetMapping("/internal/loans/users/{userId}/summary")
    LoanUserSummaryItem getUserLoanSummary(@PathVariable("userId") Long userId);

    /** 批量查询多用户贷款汇总（用户管理列表批量填充，避免 N+1）。 */
    @PostMapping("/internal/loans/users/summary/batch")
    Map<Long, LoanUserSummaryItem> listUserLoanSummaries(@RequestBody List<Long> userIds);

    /** B 卡监控所需的还款计划/期次聚焦数据，由 loan-service 基于自身还款表聚合。 */
    @GetMapping("/internal/loans/users/{userId}/b-card-monitor")
    BCardRepaymentSnapshot getBCardRepaymentMonitor(@PathVariable("userId") Long userId);
}