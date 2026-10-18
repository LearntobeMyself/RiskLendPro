package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanRecommendationSupportTest {

    private LoanProduct product(String categoryCode, Integer minAge, Integer maxAge,
                                Long minAmount, String targetGroups, String extraConditions) {
        LoanProduct product = new LoanProduct();
        product.setStatus("ON_SHELF");
        product.setCategoryCode(categoryCode);
        product.setMinAge(minAge);
        product.setMaxAge(maxAge);
        product.setMinAmount(minAmount);
        product.setTargetGroups(targetGroups);
        product.setExtraConditions(extraConditions);
        return product;
    }

    @Test
    void eligibleChecksStatusAgeAndCreditLimit() {
        LoanProduct product = product("PERSONAL_CREDIT", 22, 60, 5000L, null, null);
        // 年龄落在区间且额度下限不超过剩余额度
        assertTrue(LoanRecommendationSupport.eligible(product, 30, null, null, new BigDecimal("10000")));
        // 年龄低于下限
        assertFalse(LoanRecommendationSupport.eligible(product, 20, null, null, new BigDecimal("10000")));
        // 年龄高于上限
        assertFalse(LoanRecommendationSupport.eligible(product, 65, null, null, new BigDecimal("10000")));
        // 剩余额度低于产品额度下限
        assertFalse(LoanRecommendationSupport.eligible(product, 30, null, null, new BigDecimal("3000")));

        // 抵押分类不走信用额度判断
        product.setCategoryCode("MORTGAGE");
        assertTrue(LoanRecommendationSupport.eligible(product, 30, null, null, BigDecimal.ZERO));

        // 未上架直接淘汰
        product.setStatus("OFF_SHELF");
        assertFalse(LoanRecommendationSupport.eligible(product, 30, null, null, BigDecimal.ZERO));
    }

    @Test
    void eligibleRequiresCarOrHouseWhenDeclared() {
        LoanProduct carProduct = product("PERSONAL_CREDIT", null, null, null, "[\"CAR_OWNER\"]", null);
        assertFalse(LoanRecommendationSupport.eligible(carProduct, 30, false, null, new BigDecimal("10000")));
        assertTrue(LoanRecommendationSupport.eligible(carProduct, 30, true, null, new BigDecimal("10000")));

        LoanProduct houseProduct = product("MORTGAGE", null, null, null, "[\"PROPERTY_OWNER\"]",
                "{\"collateralTypes\":[\"HOUSE\"]}");
        assertFalse(LoanRecommendationSupport.eligible(houseProduct, 30, null, false, BigDecimal.ZERO));
        assertTrue(LoanRecommendationSupport.eligible(houseProduct, 30, null, true, BigDecimal.ZERO));

        // 无车房要求的产品不受影响
        LoanProduct plain = product("PERSONAL_CREDIT", null, null, null, "[\"SALARIED\"]", null);
        assertTrue(LoanRecommendationSupport.eligible(plain, 30, false, false, new BigDecimal("10000")));
    }
}
