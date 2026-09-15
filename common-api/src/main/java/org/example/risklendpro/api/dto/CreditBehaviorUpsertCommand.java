package org.example.risklendpro.api.dto;

import java.math.BigDecimal;

/**
 * 风控侧（risk）向 loan-service 回写 B 卡行为分到授信额度行的命令。
 */
public record CreditBehaviorUpsertCommand(
        Long userId,
        Boolean bCardEnabled,
        BigDecimal behaviorScore
) {
}