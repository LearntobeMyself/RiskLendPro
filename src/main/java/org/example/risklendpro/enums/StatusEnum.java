package org.example.risklendpro.enums;

/**
 * 业务状态枚举
 */
public enum StatusEnum {
    WAITING("WAITING", "待处理"),
    SYSTEM_REJECT("SYSTEM_REJECT", "系统拒绝"),
    MANUAL_REVIEW("MANUAL_REVIEW", "人工复核中"),
    FINAL_PASS("FINAL_PASS", "已通过"),
    FINAL_REJECT("FINAL_REJECT", "已拒绝");
    
    private final String value;
    private final String description;
    
    StatusEnum(String value, String description) {
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