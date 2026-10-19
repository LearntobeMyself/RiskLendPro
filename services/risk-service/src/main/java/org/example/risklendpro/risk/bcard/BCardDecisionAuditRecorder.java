package org.example.risklendpro.risk.bcard;

/**
 * 线上决策审计（阶段 A5）：每次额度系数决策都落一条可追溯记录。
 */
public interface BCardDecisionAuditRecorder {

    void record(AuditEntry entry);

    record AuditEntry(
            Long userId,
            String decisionType,
            String modelVersion,
            String modelHash,
            String featureVersion,
            String policyVersion,
            Long ruleId,
            String ruleHash,
            String snapshotRef,
            String dataStatus,
            Double score,
            Double probability,
            Double multiplier,
            Boolean decisionAllowed
    ) {
    }
}