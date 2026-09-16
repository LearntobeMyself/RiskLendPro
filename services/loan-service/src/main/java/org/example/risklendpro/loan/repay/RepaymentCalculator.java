package org.example.risklendpro.loan.repay;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class RepaymentCalculator {

    /**
     * 计算等额本息还款
     * 每月还款额 = [贷款本金 × 月利率 × (1+月利率)^还款月数] ÷ [(1+月利率)^还款月数－1]
     */
    public static List<RepaymentDetail> calculateEqualPrincipalAndInterest(
            BigDecimal principal, BigDecimal annualRate, int months) {
        validate(principal, months);
        // 零利率：退化为等额本金（无利息），避免 (1+0)^n - 1 = 0 除零
        if (annualRate == null || annualRate.signum() == 0) {
            return equalPaymentNoInterest(principal, months);
        }
        List<RepaymentDetail> details = new ArrayList<>();
        
        // 月利率
        BigDecimal monthlyRate = annualRate.divide(new BigDecimal(12), 6, RoundingMode.HALF_UP);
        
        // 计算每月还款额
        BigDecimal pow = monthlyRate.add(BigDecimal.ONE).pow(months);
        BigDecimal monthlyPayment = principal.multiply(monthlyRate).multiply(pow)
                .divide(pow.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
        
        BigDecimal remainingPrincipal = principal;
        for (int i = 1; i <= months; i++) {
            // 每月利息 = 剩余本金 × 月利率
            BigDecimal interest = remainingPrincipal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            
            // 每月本金 = 每月还款额 - 每月利息
            BigDecimal principalPayment = monthlyPayment.subtract(interest).setScale(2, RoundingMode.HALF_UP);
            
            // 处理最后一期可能的尾差
            if (i == months) {
                principalPayment = remainingPrincipal;
                interest = monthlyPayment.subtract(principalPayment).setScale(2, RoundingMode.HALF_UP);
            }
            
            details.add(new RepaymentDetail(i, principalPayment, interest, monthlyPayment));
            remainingPrincipal = remainingPrincipal.subtract(principalPayment);
        }
        
        return details;
    }

    /**
     * 计算等额本金还款
     * 每月还款额 = (贷款本金 ÷ 还款月数) + (贷款本金 - 已归还本金累计额) × 月利率
     */
    public static List<RepaymentDetail> calculateEqualPrincipal(
            BigDecimal principal, BigDecimal annualRate, int months) {
        validate(principal, months);
        List<RepaymentDetail> details = new ArrayList<>();
        
        // 月利率
        BigDecimal monthlyRate = annualRate.divide(new BigDecimal(12), 6, RoundingMode.HALF_UP);
        
        // 每月应还本金
        BigDecimal monthlyPrincipal = principal.divide(new BigDecimal(months), 2, RoundingMode.HALF_UP);
        
        BigDecimal remainingPrincipal = principal;
        for (int i = 1; i <= months; i++) {
            // 每月利息 = 剩余本金 × 月利率
            BigDecimal interest = remainingPrincipal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            
            // 处理最后一期可能的尾差
            BigDecimal currentPrincipal = monthlyPrincipal;
            if (i == months) {
                currentPrincipal = remainingPrincipal;
            }
            
            // 每月还款额 = 每月本金 + 每月利息
            BigDecimal monthlyPayment = currentPrincipal.add(interest).setScale(2, RoundingMode.HALF_UP);
            
            details.add(new RepaymentDetail(i, currentPrincipal, interest, monthlyPayment));
            remainingPrincipal = remainingPrincipal.subtract(currentPrincipal);
        }
        
        return details;
    }

    /**
     * 计算先息后本还款
     * 每月还息，到期还本
     */
    public static List<RepaymentDetail> calculateInterestFirst(
            BigDecimal principal, BigDecimal annualRate, int months) {
        validate(principal, months);
        List<RepaymentDetail> details = new ArrayList<>();
        
        // 月利率
        BigDecimal monthlyRate = annualRate.divide(new BigDecimal(12), 6, RoundingMode.HALF_UP);
        
        // 每月利息
        BigDecimal monthlyInterest = principal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
        
        for (int i = 1; i <= months; i++) {
            if (i < months) {
                // 前n-1期只还利息
                details.add(new RepaymentDetail(i, BigDecimal.ZERO, monthlyInterest, monthlyInterest));
            } else {
                // 最后一期还本金和利息
                BigDecimal finalPayment = principal.add(monthlyInterest).setScale(2, RoundingMode.HALF_UP);
                details.add(new RepaymentDetail(i, principal, monthlyInterest, finalPayment));
            }
        }
        
        return details;
    }

    /**
     * 还款详情类
     */
    public static class RepaymentDetail {
        private int period;           // 期数
        private BigDecimal principal;  // 本金
        private BigDecimal interest;   // 利息
        private BigDecimal amount;     // 总还款额

        public RepaymentDetail(int period, BigDecimal principal, BigDecimal interest, BigDecimal amount) {
            this.period = period;
            this.principal = principal;
            this.interest = interest;
            this.amount = amount;
        }

        public int getPeriod() {
            return period;
        }

        public BigDecimal getPrincipal() {
            return principal;
        }

        public BigDecimal getInterest() {
            return interest;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }

    private static void validate(BigDecimal principal, int months) {
        if (months <= 0) {
            throw new IllegalArgumentException("还款期数必须大于0");
        }
        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException("贷款本金必须大于0");
        }
    }

    /** 零利率场景：等额本金、无利息，避免除零。 */
    private static List<RepaymentDetail> equalPaymentNoInterest(BigDecimal principal, int months) {
        BigDecimal each = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        BigDecimal remaining = principal;
        List<RepaymentDetail> details = new ArrayList<>();
        for (int i = 1; i <= months; i++) {
            BigDecimal p = (i == months) ? remaining : each;
            details.add(new RepaymentDetail(i, p, BigDecimal.ZERO, p));
            remaining = remaining.subtract(p);
        }
        return details;
    }
}
