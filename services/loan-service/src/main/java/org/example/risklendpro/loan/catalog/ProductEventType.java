package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.common.CatalogBusinessException;

import java.util.Locale;

public enum ProductEventType {
    EXPOSURE(0.5),
    CLICK(2.0),
    FAVORITE(3.0),
    SEARCH(2.0),
    DETAIL(2.0),
    APPLY_START(4.0),
    APPLY_SUBMIT(5.0),
    APPLY_SUCCESS(6.0);

    private final double preferenceWeight;

    ProductEventType(double preferenceWeight) {
        this.preferenceWeight = preferenceWeight;
    }

    public double preferenceWeight() {
        return preferenceWeight;
    }

    public boolean countsAsApply() {
        return this == APPLY_START || this == APPLY_SUBMIT || this == APPLY_SUCCESS;
    }

    public static ProductEventType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "eventType 不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID,
                    "不支持的事件类型: " + value);
        }
    }
}
