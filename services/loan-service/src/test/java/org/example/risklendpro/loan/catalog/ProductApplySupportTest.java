package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductApplySupportTest {

    @Test
    void inPlatformApplyRequiresMonthAmountAndRate() {
        LoanProduct product = new LoanProduct();
        product.setTermUnit("DAY");
        product.setMinAmount(1000L);
        product.setMinAnnualRate(new BigDecimal("3.60"));
        assertFalse(ProductApplySupport.inPlatformApply(product));

        product.setTermUnit("MONTH");
        assertTrue(ProductApplySupport.inPlatformApply(product));

        product.setMinAmount(null);
        product.setMaxAmount(null);
        assertFalse(ProductApplySupport.inPlatformApply(product));
    }

    @Test
    void normalizeRepaymentAcceptsCodeAndChinese() {
        assertEquals("等额本息", ProductApplySupport.normalizeRepaymentMethod("EQUAL_INSTALLMENT"));
        assertEquals("等额本金", ProductApplySupport.normalizeRepaymentMethod("等额本金"));
        assertThrows(RuntimeException.class, () -> ProductApplySupport.normalizeRepaymentMethod("FLEXIBLE"));
    }

    @Test
    void toLoanRateUsesPercentDividedBy100() {
        LoanProduct product = new LoanProduct();
        product.setMinAnnualRate(new BigDecimal("3.60"));
        assertEquals(new BigDecimal("0.0360"), ProductApplySupport.toLoanRate(product));
    }
}
