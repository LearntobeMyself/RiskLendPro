package org.example.risklendpro.risk.assessment;

/**
 * 学历枚举
 */
public enum EducationEnum {
    DOCTOR("博士"),
    MASTER("硕士"),
    BACHELOR("本科"),
    COLLEGE("大专"),
    HIGH_SCHOOL_AND_BELOW("高中及以下");
    
    private final String value;
    
    EducationEnum(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}