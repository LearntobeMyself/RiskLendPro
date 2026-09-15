package org.example.risklendpro.api.contract;

import org.example.risklendpro.api.dto.CreditBehaviorUpsertCommand;
import org.example.risklendpro.api.dto.CreditLimitGrantCommand;
import org.example.risklendpro.api.dto.CreditLimitSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 授信/额度域契约。由 loan-service 实现（额度归 loan 域所有），risk（评估放款、B卡回写）与 user 消费。
 */
public interface CreditLimitApi {

    @GetMapping("/internal/credit-limits/users/{userId}")
    CreditLimitSnapshot getCreditLimit(@PathVariable("userId") Long userId);

    @PostMapping("/internal/credit-limits/grants")
    CreditLimitSnapshot grantCreditLimit(@RequestBody CreditLimitGrantCommand command);

    @PostMapping("/internal/credit-limits/behavior-upsert")
    CreditLimitSnapshot upsertBehaviorScore(@RequestBody CreditBehaviorUpsertCommand command);
}