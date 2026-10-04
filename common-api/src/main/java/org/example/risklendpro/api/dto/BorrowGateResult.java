package org.example.risklendpro.api.dto;

/**
 * 借款前风控门禁。规则只在 risk-service 实现，loan 只看 {@link #allowed()}。
 */
public record BorrowGateResult(
        boolean allowed,
        String reasonCode,
        String reasonMessage,
        String assessmentId,
        String idCard
) {
    public static BorrowGateResult allow(String assessmentId, String idCard) {
        return new BorrowGateResult(true, null, null, assessmentId, idCard);
    }

    public static BorrowGateResult deny(String reasonCode, String reasonMessage, String assessmentId, String idCard) {
        return new BorrowGateResult(false, reasonCode, reasonMessage, assessmentId, idCard);
    }
}
