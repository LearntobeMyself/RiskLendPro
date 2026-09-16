package org.example.risklendpro.risk.bcard;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BCardFeatureVector {
    private long loanCountTotal;
    private long activeLoanCount;
    private long repaidLoanCount;
    private long overdueLoanCount;
    private long overduePlanCount;
    private int currentMaxDpd;
    private int maxDpd12m;
    private double avgDpd12m;
    private long latePaymentCount12m;
    private double onTimeRate12m;
    private double partialPaymentRatio12m;
    private double utilizationRate;
    private double cappedUtilizationRate;
    private double overdueAmount;
    private double outstandingAmount;
    private double totalLimit;
    private double usedLimit;
    private double remainingLimit;
    private long recentNewLoanCount90d;
    private long recentNewLoanCount180d;
    private Long daysSinceLastOverdue;
    private long tenureDays;
    private double totalLoanAmount;
    private double avgLoanAmount;
    private double lastLoanAmount;
    private long recordCount;
    private long paidRecordCount;
    private boolean hasOverdue;
    private List<String> dataQualityFlags = new ArrayList<>();
}
