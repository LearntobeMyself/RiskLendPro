package org.example.risklendpro.enums;

/**
 * 婚姻状况枚举
 */
public enum MarriageEnum {
    SINGLE("单身"),
    MARRIED("已婚"),
    DIVORCED("离异"),
    WIDOWED("丧偶");
    
    private final String value;
    
    MarriageEnum(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}