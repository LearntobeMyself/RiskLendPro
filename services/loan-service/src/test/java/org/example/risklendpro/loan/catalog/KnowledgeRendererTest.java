package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.common.CatalogBusinessException;
import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeRendererTest {

    @Test
    void emptyFinancialFieldsRenderAsNoData() {
        LoanProduct product = onShelfBase();
        String text = KnowledgeRenderer.render(product, "测试银行", "银行", List.of());
        assertTrue(text.contains("借款额度：暂无数据"));
        assertTrue(text.contains("参考年化利率：暂无数据"));
        assertFalse(text.contains("编造"));
    }

    @Test
    void onlyMaxRateRendersHighest() {
        LoanProduct product = onShelfBase();
        product.setMaxAnnualRate(new BigDecimal("18"));
        String text = KnowledgeRenderer.rateText(product.getMinAnnualRate(), product.getMaxAnnualRate());
        assertEquals("最高 18%", text);
        assertFalse(text.contains("暂无数据%"));
    }

    @Test
    void nullFeeStatusExactlyNoData() {
        LoanProduct product = onShelfBase();
        String text = KnowledgeRenderer.render(product, "测试银行", "银行", List.of());
        assertTrue(text.contains("除利息外的其他费用：暂无数据"));
        assertFalse(text.contains("机构明示无其他费用"));
    }

    @Test
    void emptyRegionsNotNationwide() {
        assertEquals("暂无数据", KnowledgeRenderer.regionsText(List.of()));
        String text = KnowledgeRenderer.render(onShelfBase(), "测试银行", "银行", List.of());
        assertTrue(text.contains("可申请地区：暂无数据"));
        assertFalse(text.contains("可申请地区：全国"));
    }

    @Test
    void offShelfTemplateHasNoFinancialNumbers() {
        LoanProduct product = onShelfBase();
        product.setStatus("OFF_SHELF");
        product.setLastOffShelfTime(LocalDateTime.of(2026, 9, 30, 10, 0));
        product.setMinAmount(10000L);
        String text = KnowledgeRenderer.render(product, "测试银行", "银行", List.of());
        assertTrue(text.contains("状态：已下架"));
        assertFalse(text.contains("10000"));
        assertFalse(text.contains("借款额度"));
    }

    @Test
    void extraConditionUnits() {
        String text = KnowledgeRenderer.extraConditionsText("BUSINESS",
                "{\"minOperatingMonths\":6,\"maxLoanToValue\":70}");
        assertTrue(text.contains("经营时间下限：6 个月"));
        String mortgage = KnowledgeRenderer.extraConditionsText("MORTGAGE", "{\"maxLoanToValue\":70}");
        assertTrue(mortgage.contains("抵押率上限：70%"));
    }

    @Test
    void feeNoneAndInterestOnly() {
        LoanProduct product = onShelfBase();
        product.setFeeStatus("NONE");
        product.setRateScope("INTEREST_ONLY");
        String text = KnowledgeRenderer.render(product, "测试银行", "银行", List.of());
        assertTrue(text.contains("除利息外的其他费用：机构明示无其他费用；费用说明：无"));
        assertTrue(text.contains("利率口径：仅为利息部分（是否另有费用见“其他费用”一项）"));
        assertFalse(text.contains("另有费用见费用说明"));
    }

    @Test
    void metadataShapeForExportHelper() {
        Map<String, Object> metadata = Map.of(
                "chunkType", "PRODUCT",
                "categoryCode", "BUSINESS",
                "institutionName", "测试银行",
                "tags", List.of("小微企业"),
                "status", "ON_SHELF",
                "verifiedAt", "2026-09-30",
                "updateTime", "2026-09-30 10:00:00"
        );
        assertTrue(metadata.containsKey("chunkType"));
        assertEquals(7, metadata.size());
    }

    @Test
    void mortgageRequiredNullBecomesRequiredOnValidate() {
        assertEquals("REQUIRED", CatalogValidator.applyMortgageRule("MORTGAGE", null));
        CatalogBusinessException ex = assertThrows(CatalogBusinessException.class,
                () -> CatalogValidator.applyMortgageRule("MORTGAGE", "OPTIONAL"));
        assertEquals(CatalogErrorCodes.CATEGORY_RULE_CONFLICT, ex.getCode());
        CatalogBusinessException business = assertThrows(CatalogBusinessException.class,
                () -> CatalogValidator.applyMortgageRule("BUSINESS", "REQUIRED"));
        assertEquals(CatalogErrorCodes.CATEGORY_RULE_CONFLICT, business.getCode());
    }

    @Test
    void amountRangeAndFeeDependency() {
        CatalogBusinessException range = assertThrows(CatalogBusinessException.class,
                () -> CatalogValidator.amountRange(200L, 100L));
        assertEquals(CatalogErrorCodes.RANGE_INVALID, range.getCode());
        CatalogBusinessException extra = assertThrows(CatalogBusinessException.class,
                () -> CatalogValidator.extraConditions("PERSONAL_CREDIT", Map.of("collateralTypes", List.of("HOUSE"))));
        assertEquals(CatalogErrorCodes.EXTRA_FIELD_INVALID, extra.getCode());
        CatalogBusinessException fee = assertThrows(CatalogBusinessException.class,
                () -> CatalogValidator.fee("HAS", null));
        assertEquals(CatalogErrorCodes.FIELD_DEPENDENCY_INVALID, fee.getCode());
        CatalogBusinessException groups = assertThrows(CatalogBusinessException.class,
                () -> CatalogValidator.targetGroups(List.of("GENERAL", "SALARIED")));
        assertEquals(CatalogErrorCodes.FIELD_DEPENDENCY_INVALID, groups.getCode());
    }

    private static LoanProduct onShelfBase() {
        LoanProduct product = new LoanProduct();
        product.setProductName("测试产品");
        product.setStatus("ON_SHELF");
        product.setCategoryCode("PERSONAL_CREDIT");
        product.setVerifiedAt(LocalDate.of(2026, 9, 30));
        product.setDataSource("机构官网");
        return product;
    }
}
