package org.example.risklendpro.enums;

/**
 * 月收入枚举
 */
public enum MonthlyIncomeEnum {
    BELOW_3000("3000以下"),
    BETWEEN_3000_8000("3000-8000"),
    BETWEEN_8000_15000("8000-15000"),
    ABOVE_15000("15000以上");
    
    private final String value;
    
    MonthlyIncomeEnum(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}