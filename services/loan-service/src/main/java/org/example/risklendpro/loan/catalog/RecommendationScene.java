package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.common.CatalogBusinessException;

import java.util.Locale;

public enum RecommendationScene {
    HOME("首页推荐", 6),
    DETAIL("详情页相似推荐", 4),
    CREDIT_PASSED("授信通过推荐", 3),
    POST_LOAN("成功页再推荐", 3);

    private final String label;
    private final int defaultLimit;

    RecommendationScene(String label, int defaultLimit) {
        this.label = label;
        this.defaultLimit = defaultLimit;
    }

    public String label() {
        return label;
    }

    public int defaultLimit() {
        return defaultLimit;
    }

    public static RecommendationScene parse(String value) {
        if (value == null || value.isBlank()) {
            return CREDIT_PASSED;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID,
                    "不支持的推荐场景，可选 HOME/DETAIL/CREDIT_PASSED/POST_LOAN");
        }
    }
}
