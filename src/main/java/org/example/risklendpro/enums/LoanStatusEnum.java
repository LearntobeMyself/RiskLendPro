package org.example.risklendpro.enums;

import lombok.Getter;

@Getter
public enum LoanStatusEnum {
    PENDING_APPROVAL("PENDING_APPROVAL", "待审批（额度外借款需管理员审批）"),
    APPROVED("APPROVED", "已审批通过"),
    REJECTED("REJECTED", "审批拒绝"),
    DISBURRSED("DISBURRSED", "已发放"),
    REPAID("REPAID", "已还清"),
    OVERDUE("OVERDUE", "逾期中");

    private final String code;
    private final String desc;

    LoanStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static LoanStatusEnum fromCode(String code) {
        for (LoanStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
