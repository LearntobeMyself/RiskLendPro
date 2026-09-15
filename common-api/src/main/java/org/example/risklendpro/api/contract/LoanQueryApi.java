package org.example.risklendpro.api.contract;

import org.example.risklendpro.api.dto.LoanBehaviorSnapshot;
import org.example.risklendpro.api.dto.LoanUserSummaryItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 贷款域行为/汇总契约。由 loan-service 实现，risk（行为分）与 user（管理端）消费。
 */
public interface LoanQueryApi {

    @GetMapping("/internal/loans/users/{userId}/behavior")
    LoanBehaviorSnapshot getLoanBehavior(@PathVariable("userId") Long userId);

    @GetMapping("/internal/loans/users/{userId}/summary")
    LoanUserSummaryItem getUserLoanSummary(@PathVariable("userId") Long userId);
}