package org.example.risklendpro.utils;

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
}
