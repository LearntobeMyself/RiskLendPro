package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.loan.catalog.entity.LoanProduct;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Objects;

/**
 * 贷款产品推荐的候选筛选纯逻辑，无 Spring/数据库依赖，便于单元测试。
 * 候选须同时满足：已上架、年龄区间、车/房身份、信用额度下限。
 */
public final class LoanRecommendationSupport {

    public static final String TARGET_CAR_OWNER = "CAR_OWNER";
    public static final String TARGET_PROPERTY_OWNER = "PROPERTY_OWNER";
    public static final int DEFAULT_MIN_AGE = 18;

    private LoanRecommendationSupport() {
    }

    /** 从 18 位身份证号（第 7-14 位为 yyyyMMdd）解析年龄，解析失败返回 null。 */
    public static Integer ageFromIdCard(String idCard) {
        if (idCard == null || idCard.length() < 14) {
            return null;
        }
        try {
            LocalDate birth = LocalDate.of(
                    Integer.parseInt(idCard.substring(6, 10)),
                    Integer.parseInt(idCard.substring(10, 12)),
                    Integer.parseInt(idCard.substring(12, 14)));
            return Period.between(birth, LocalDate.now()).getYears();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean eligible(LoanProduct product, Integer age, Boolean hasCar,
                                   Boolean hasHouse, BigDecimal remainingLimit) {
        if (product == null || !"ON_SHELF".equals(product.getStatus())) {
            return false;
        }
        if (!ageAllowed(age, product.getMinAge(), product.getMaxAge())) {
            return false;
        }
        List<String> collateralTypes = collateralTypes(product);
        List<String> targetGroups = CatalogJson.readStringList(product.getTargetGroups());
        if ((collateralTypes.contains(ProductApplySupport.COLLATERAL_VEHICLE)
                || targetGroups.contains(TARGET_CAR_OWNER)) && !Boolean.TRUE.equals(hasCar)) {
            return false;
        }
        if ((collateralTypes.contains(ProductApplySupport.COLLATERAL_HOUSE)
                || targetGroups.contains(TARGET_PROPERTY_OWNER)) && !Boolean.TRUE.equals(hasHouse)) {
            return false;
        }
        return ProductApplySupport.isMortgageCategory(product.getCategoryCode())
                || creditLimitAllowed(product.getMinAmount(), remainingLimit);
    }

    /** 产品未写 minAge 时默认 18 岁；未写 maxAge 时不设上限；无法解析年龄时跳过校验。 */
    static boolean ageAllowed(Integer age, Integer minAge, Integer maxAge) {
        if (age == null) {
            return true;
        }
        int min = minAge != null ? minAge : DEFAULT_MIN_AGE;
        return age >= min && (maxAge == null || age <= maxAge);
    }

    /** 走信用额度的产品：额度下限 ≤ 用户剩余信用额度（下限缺省视为 0）。 */
    static boolean creditLimitAllowed(Long minAmount, BigDecimal remainingLimit) {
        BigDecimal min = minAmount == null ? BigDecimal.ZERO : BigDecimal.valueOf(minAmount);
        BigDecimal remaining = remainingLimit == null ? BigDecimal.ZERO : remainingLimit;
        return min.compareTo(remaining) <= 0;
    }

    static List<String> collateralTypes(LoanProduct product) {
        Object value = CatalogJson.readMap(product.getExtraConditions()).get("collateralTypes");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }
}
