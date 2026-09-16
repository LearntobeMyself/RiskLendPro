package org.example.risklendpro.loan.repay;

import org.example.risklendpro.loan.repay.RepaymentCalculator.RepaymentDetail;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepaymentCalculatorTest {

    private static final BigDecimal PRINCIPAL = new BigDecimal("10000");
    private static final BigDecimal RATE = new BigDecimal("0.05"); // 5%

    @Test
    void equalPrincipalAndInterest_sumOfPrincipalEqualsPrinciple() {
        List<RepaymentDetail> details = RepaymentCalculator.calculateEqualPrincipalAndInterest(PRINCIPAL, RATE, 12);

        assertEquals(12, details.size());
        BigDecimal principalSum = details.stream()
                .map(RepaymentDetail::getPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(PRINCIPAL.stripTrailingZeros(), principalSum.stripTrailingZeros());

        // 每月还款额固定且为正
        BigDecimal first = details.get(0).getAmount();
        details.forEach(d -> {
            assertTrue(d.getAmount().compareTo(BigDecimal.ZERO) > 0);
            assertEquals(d.getAmount().setScale(2), d.getAmount());
        });
        assertEquals(first, details.get(11).getAmount());
    }

    @Test
    void equalPrincipal_sumOfPrincipalEqualsPrincipleAndDecreasing() {
        List<RepaymentDetail> details = RepaymentCalculator.calculateEqualPrincipal(PRINCIPAL, RATE, 12);

        BigDecimal principalSum = details.stream()
                .map(RepaymentDetail::getPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(PRINCIPAL.stripTrailingZeros(), principalSum.stripTrailingZeros());

        // 本金部分非增（四舍五入允许最后一期调尾差，此处仅校验总本金）
        assertTrue(details.get(0).getPrincipal().compareTo(details.get(1).getPrincipal()) >= 0);
    }

    @Test
    void interestFirst_onlyInterestBeforeFinalPeriod() {
        List<RepaymentDetail> details = RepaymentCalculator.calculateInterestFirst(PRINCIPAL, RATE, 6);

        assertEquals(6, details.size());
        // 前 5 期仅还利息，本金为 0
        for (int i = 0; i < 5; i++) {
            assertEquals(BigDecimal.ZERO, details.get(i).getPrincipal());
        }
        // 最后一期还清本金
        assertEquals(PRINCIPAL, details.get(5).getPrincipal());
        // 利息总计 = 本金 * 月利率 * 期数
        BigDecimal monthly = PRINCIPAL.multiply(RATE).divide(new BigDecimal(12), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal interestSum = details.stream()
                .map(RepaymentDetail::getInterest)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(monthly.multiply(new BigDecimal(6)).setScale(2), interestSum.setScale(2));
    }

    @Test
    void zeroRateIsSupportedWithoutDivisionByZero() {
        // 回归修复：零利率时等额本息不再除零崩溃
        List<RepaymentDetail> details = RepaymentCalculator.calculateEqualPrincipalAndInterest(
                PRINCIPAL, BigDecimal.ZERO, 12);
        assertEquals(12, details.size());
        BigDecimal principalSum = details.stream()
                .map(RepaymentDetail::getPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(PRINCIPAL.stripTrailingZeros(), principalSum.stripTrailingZeros());
        // 零利率无利息
        details.forEach(d -> assertEquals(BigDecimal.ZERO, d.getInterest()));
    }

    @Test
    void rejectsInvalidMonthsOrPrincipal() {
        assertThrows(IllegalArgumentException.class,
                () -> RepaymentCalculator.calculateEqualPrincipalAndInterest(PRINCIPAL, RATE, 0));
        assertThrows(IllegalArgumentException.class,
                () -> RepaymentCalculator.calculateEqualPrincipal(PRINCIPAL, RATE, -1));
        assertThrows(IllegalArgumentException.class,
                () -> RepaymentCalculator.calculateInterestFirst(new BigDecimal("0"), RATE, 12));
    }

    @Test
    void singlePeriodBorrow() {
        List<RepaymentDetail> details = RepaymentCalculator.calculateEqualPrincipal(PRINCIPAL, RATE, 1);
        assertEquals(1, details.size());
        // 最后一期本金=全部本金
        assertEquals(PRINCIPAL, details.get(0).getPrincipal());
    }
}