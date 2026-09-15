package org.example.risklendpro.common;

/**
 * 用户重复提交风控评估时抛出，与 submit-eligibility 接口文案一致。
 */
public class DuplicateApplyException extends RuntimeException {

    private final String existingApplyId;
    private final String existingStatus;
    private final Long retryAfterDays;

    public DuplicateApplyException(String reason, String existingApplyId, String existingStatus, Long retryAfterDays) {
        super(reason);
        this.existingApplyId = existingApplyId;
        this.existingStatus = existingStatus;
        this.retryAfterDays = retryAfterDays;
    }

    public String getExistingApplyId() {
        return existingApplyId;
    }

    public String getExistingStatus() {
        return existingStatus;
    }

    public Long getRetryAfterDays() {
        return retryAfterDays;
    }
}
