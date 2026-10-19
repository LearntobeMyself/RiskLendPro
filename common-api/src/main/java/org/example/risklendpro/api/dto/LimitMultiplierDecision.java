package org.example.risklendpro.api.dto;

/**
 * B 卡额度系数决策对象。属主服务：risk-service。
 *
 * <p>可信度判断与系数生成在同一决策点完成。约定：
 * {@code decisionAllowed=false} 时 {@code multiplier} 必须为 {@code null}，
 * 调用方（例如 loan-service 的自动调额任务）不得据此调整任何额度。</p>
 */
public record LimitMultiplierDecision(
        Long userId,
        Double multiplier,
        boolean scoreReliable,
        boolean decisionAllowed,
        String dataStatus,
        String modelVersion,
        String featureVersion,
        String policyVersion,
        String ruleHash,
        String reason
) {
}