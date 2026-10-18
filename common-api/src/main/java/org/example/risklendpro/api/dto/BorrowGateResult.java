package org.example.risklendpro.api.dto;

/**
 * 借款前风控门禁。规则只在 risk-service 实现，loan 只看 {@link #allowed()}。
 * hasHouse / hasCar 取自最近一次最终评估，供房抵、车抵产品判断准入。
 */
public record BorrowGateResult(
        boolean allowed,
        String reasonCode,
        String reasonMessage,
        String assessmentId,
        String idCard,
        Boolean hasHouse,
        Boolean hasCar
) {
    public static BorrowGateResult allow(String assessmentId, String idCard) {
        return allow(assessmentId, idCard, null, null);
    }

    public static BorrowGateResult allow(String assessmentId, String idCard, Boolean hasHouse, Boolean hasCar) {
        return new BorrowGateResult(true, null, null, assessmentId, idCard, hasHouse, hasCar);
    }

    public static BorrowGateResult deny(String reasonCode, String reasonMessage, String assessmentId, String idCard) {
        return new BorrowGateResult(false, reasonCode, reasonMessage, assessmentId, idCard, null, null);
    }
}
