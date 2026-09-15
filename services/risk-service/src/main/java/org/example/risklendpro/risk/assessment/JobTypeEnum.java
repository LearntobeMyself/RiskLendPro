package org.example.risklendpro.risk.assessment;

/**
 * 职业类型枚举
 */
public enum JobTypeEnum {
    GOVERNMENT_ENTERPRISE("企事业单位"),
    PRIVATE_ENTERPRISE("私营企业"),
    FOREIGN_JOINT_VENTURE("外资/合资"),
    INDIVIDUAL_BUSINESS("个体经营"),
    FREELANCE("自由职业"),
    STUDENT("学生");
    
    private final String value;
    
    JobTypeEnum(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}