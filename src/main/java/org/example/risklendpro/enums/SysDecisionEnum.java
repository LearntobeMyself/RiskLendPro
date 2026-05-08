package org.example.risklendpro.enums;

/**
 * 引擎建议枚举
 */
public enum SysDecisionEnum {
    APPROVE("APPROVE", "建议通过"),
    MANUAL_REVIEW("MANUAL_REVIEW", "建议人工复核"),
    REJECT("REJECT", "建议拒绝");
    
    private final String value;
    private final String description;
    
    SysDecisionEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public String getValue() {
        return value;
    }
    
    public String getDescription() {
        return description;
    }
}