package org.example.risklendpro.api.dto;

/**
 * risk-service 评估终审后回写 user-service 用户评估状态的命令。
 */
public record UserAssessmentStatusCommand(
        Long userId,
        String status
) {
}