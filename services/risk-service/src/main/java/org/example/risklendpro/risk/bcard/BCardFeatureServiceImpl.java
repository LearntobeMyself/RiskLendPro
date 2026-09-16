package org.example.risklendpro.risk.bcard;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.risk.entity.BCardFeatureSnapshot;
import org.example.risklendpro.risk.mapper.BCardFeatureSnapshotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class BCardFeatureServiceImpl implements BCardFeatureService {

    private static final Logger log = LoggerFactory.getLogger(BCardFeatureServiceImpl.class);
    private static final String FEATURE_VERSION = "BF_V2.1";
    private static final int OBSERVATION_DAYS = 180;

    @Autowired
    private LoanMapper loanMapper;

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private BCardFeatureSnapshotMapper bCardFeatureSnapshotMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public BCardFeatureVector buildFeatureVector(Long userId, LocalDate asOfDate) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");

        List<Loan> loans = loanMapper.selectList(
                new QueryWrapper<Loan>().eq("user_id", userId));
        List<Loan> effectiveLoans = loans.stream()
                .filter(this::isEffectiveLoan)
                .collect(Collectors.toList());
        List<Long> loanIds = effectiveLoans.stream()
                .map(Loan::getLoanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<RepaymentPlan> plans = repaymentPlanMapper.selectList(
                new QueryWrapper<RepaymentPlan>().eq("user_id", userId));
        List<RepaymentRecord> records;
        if (loanIds.isEmpty()) {
            records = Collections.emptyList();
        } else {
            records = repaymentRecordMapper.selectList(
                    new QueryWrapper<RepaymentRecord>().in("loan_id", loanIds));
        }

        UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId));

        BCardFeatureVector feature = new BCardFeatureVector();
        feature.setLoanCountTotal(effectiveLoans.size());
        feature.setActiveLoanCount(effectiveLoans.stream()
                .filter(loan -> "DISBURSED".equals(loan.getStatus())
                        || "OVERDUE".equals(loan.getStatus()))
                .count());
        feature.setRepaidLoanCount(effectiveLoans.stream()
                .filter(loan -> "REPAID".equals(loan.getStatus()))
                .count());
        feature.setOverdueLoanCount(effectiveLoans.stream()
                .filter(loan -> "OVERDUE".equals(loan.getStatus()))
                .count());

        feature.setOverduePlanCount(plans.stream()
                .filter(plan -> "OVERDUE".equals(plan.getStatus()))
                .count());
        feature.setOutstandingAmount(plans.stream()
                .filter(plan -> !"COMPLETED".equals(plan.getStatus()))
                .map(RepaymentPlan::getRemainingAmount)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .sum());

        int currentMaxDpd = plans.stream()
                .map(RepaymentPlan::getOverdueDays)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        LocalDate twelveMonthsAgo = asOfDate.minusMonths(12);
        int maxDpd12m = 0;
        for (RepaymentPlan plan : plans) {
            if (plan.getOverdueDays() == null || plan.getOverdueDays() <= 0) {
                continue;
            }
            LocalDate windowDate = plan.getUpdateTime() != null
                    ? toLocalDate(plan.getUpdateTime())
                    : toLocalDate(plan.getCreateTime());
            if (windowDate == null || !windowDate.isBefore(twelveMonthsAgo)) {
                maxDpd12m = Math.max(maxDpd12m, plan.getOverdueDays());
            }
        }
        long dpdSum12m = 0;
        long dpdCount12m = 0;
        long latePaymentCount12m = 0;
        LocalDate lastOverdueDate = null;

        long paidRecordCount = 0;
        long onTimeRecordCount12m = 0;
        long paidRecordCount12m = 0;
        long partialPaymentCount12m = 0;

        for (RepaymentRecord record : records) {
            int dpd = calculateDpd(record, asOfDate);
            boolean paid = isPaid(record);
            if (paid) {
                paidRecordCount++;
            }
            if (!paid && dpd > 0) {
                currentMaxDpd = Math.max(currentMaxDpd, dpd);
            }

            LocalDate dueDate = toLocalDate(record.getDueDate());
            if (dueDate == null || dueDate.isBefore(twelveMonthsAgo) || dueDate.isAfter(asOfDate)) {
                continue;
            }

            if (dpd > 0) {
                maxDpd12m = Math.max(maxDpd12m, dpd);
                dpdSum12m += dpd;
                dpdCount12m++;
                latePaymentCount12m++;
                if (lastOverdueDate == null || dueDate.isAfter(lastOverdueDate)) {
                    lastOverdueDate = dueDate;
                }
            }

            if (paid || dueDate.isBefore(asOfDate)) {
                paidRecordCount12m++;
                if (paid && isOnTime(record)) {
                    onTimeRecordCount12m++;
                }
            }
            if (paid && isPartialPayment(record)) {
                partialPaymentCount12m++;
            }
        }

        feature.setCurrentMaxDpd(currentMaxDpd);
        feature.setMaxDpd12m(maxDpd12m);
        feature.setAvgDpd12m(dpdCount12m == 0 ? 0.0 : (double) dpdSum12m / dpdCount12m);
        feature.setLatePaymentCount12m(latePaymentCount12m);
        feature.setOnTimeRate12m(paidRecordCount12m == 0
                ? 0.0
                : (double) onTimeRecordCount12m / paidRecordCount12m);
        feature.setPartialPaymentRatio12m(paidRecordCount12m == 0
                ? 0.0
                : (double) partialPaymentCount12m / paidRecordCount12m);
        feature.setDaysSinceLastOverdue(lastOverdueDate == null
                ? null
                : ChronoUnit.DAYS.between(lastOverdueDate, asOfDate));
        feature.setRecordCount(records.size());
        feature.setPaidRecordCount(paidRecordCount);

        double totalLimit = creditLimit == null ? 0.0 : toDouble(creditLimit.getTotalLimit());
        double usedLimit = creditLimit == null ? 0.0 : toDouble(creditLimit.getUsedLimit());
        double remainingLimit = creditLimit == null ? 0.0 : toDouble(creditLimit.getRemainingLimit());
        double utilizationRate = totalLimit <= 0.0 ? 0.0 : usedLimit / totalLimit;
        feature.setUtilizationRate(utilizationRate);
        feature.setCappedUtilizationRate(Math.min(utilizationRate, 2.0));
        feature.setTotalLimit(totalLimit);
        feature.setUsedLimit(usedLimit);
        feature.setRemainingLimit(remainingLimit);
        feature.setOverdueAmount(creditLimit != null && creditLimit.getOverdueAmount() != null
                ? toDouble(creditLimit.getOverdueAmount())
                : calculateUnpaidOverdueAmount(records, asOfDate));
        feature.setHasOverdue(creditLimit != null && Boolean.TRUE.equals(creditLimit.getHasOverdue())
                || currentMaxDpd > 0);

        double totalLoanAmount = effectiveLoans.stream()
                .map(Loan::getAmount)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
        feature.setTotalLoanAmount(totalLoanAmount);
        feature.setAvgLoanAmount(effectiveLoans.isEmpty()
                ? 0.0
                : totalLoanAmount / effectiveLoans.size());

        Loan lastLoan = effectiveLoans.stream()
                .filter(loan -> loan.getDisbursementTime() != null)
                .max((left, right) -> left.getDisbursementTime().compareTo(right.getDisbursementTime()))
                .orElse(null);
        feature.setLastLoanAmount(lastLoan == null ? 0.0 : toDouble(lastLoan.getAmount()));

        LocalDate ninetyDaysAgo = asOfDate.minusDays(90);
        LocalDate oneEightyDaysAgo = asOfDate.minusDays(180);
        feature.setRecentNewLoanCount90d(effectiveLoans.stream()
                .filter(loan -> loan.getDisbursementTime() != null)
                .filter(loan -> !toLocalDate(loan.getDisbursementTime()).isBefore(ninetyDaysAgo))
                .count());
        feature.setRecentNewLoanCount180d(effectiveLoans.stream()
                .filter(loan -> loan.getDisbursementTime() != null)
                .filter(loan -> !toLocalDate(loan.getDisbursementTime()).isBefore(oneEightyDaysAgo))
                .count());

        List<LocalDate> knownDates = new ArrayList<>();
        for (Loan loan : effectiveLoans) {
            addDate(knownDates, loan.getDisbursementTime());
            addDate(knownDates, loan.getApplyTime());
            addDate(knownDates, loan.getApproveTime());
        }
        for (RepaymentPlan plan : plans) {
            addDate(knownDates, plan.getCreateTime());
            addDate(knownDates, plan.getUpdateTime());
        }
        for (RepaymentRecord record : records) {
            addDate(knownDates, record.getCreateTime());
            addDate(knownDates, record.getDueDate());
        }
        LocalDate firstKnownDate = knownDates.stream().min(LocalDate::compareTo).orElse(null);
        long tenureDays = firstKnownDate == null
                ? 0L
                : Math.max(0L, ChronoUnit.DAYS.between(firstKnownDate, asOfDate));
        feature.setTenureDays(tenureDays);

        List<String> dataQualityFlags = new ArrayList<>();
        if (utilizationRate > 2.0) {
            dataQualityFlags.add("UTILIZATION_GT_2");
        }
        if (tenureDays < currentMaxDpd) {
            dataQualityFlags.add("TENURE_LT_MAX_DPD");
        }
        if (plans.stream().anyMatch(plan -> plan.getOverdueDays() != null && plan.getOverdueDays() > 0)
                && maxDpd12m == 0) {
            dataQualityFlags.add("PLAN_OVERDUE_OUTSIDE_RECORD_WINDOW");
        }
        feature.setDataQualityFlags(dataQualityFlags);

        return feature;
    }

    @Override
    @Transactional
    public BCardFeatureSnapshot createSnapshot(Long userId, LocalDate asOfDate) {
        BCardFeatureVector featureVector = buildFeatureVector(userId, asOfDate);
        String featureJson;
        try {
            featureJson = objectMapper.writeValueAsString(featureVector);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize B-card feature vector", e);
        }

        BCardFeatureSnapshot snapshot = bCardFeatureSnapshotMapper.selectOne(
                new QueryWrapper<BCardFeatureSnapshot>()
                        .eq("user_id", userId)
                        .eq("as_of_date", asOfDate)
                        .eq("feature_version", FEATURE_VERSION));
        boolean insert = snapshot == null;
        if (insert) {
            snapshot = new BCardFeatureSnapshot();
        }

        snapshot.setUserId(userId);
        snapshot.setAsOfDate(asOfDate);
        snapshot.setSnapshotTime(LocalDateTime.now());
        snapshot.setFeatureVersion(FEATURE_VERSION);
        snapshot.setObservationStart(asOfDate.minusDays(OBSERVATION_DAYS));
        snapshot.setObservationEnd(asOfDate);
        snapshot.setPerformanceStart(asOfDate.plusDays(1));
        snapshot.setPerformanceEnd(asOfDate.plusDays(OBSERVATION_DAYS));
        snapshot.setSampleStatus("OPEN");
        snapshot.setFeatureJson(featureJson);

        if (insert) {
            bCardFeatureSnapshotMapper.insert(snapshot);
        } else {
            bCardFeatureSnapshotMapper.updateById(snapshot);
        }
        return snapshot;
    }

    @Override
    public BCardFeatureSnapshot getLatestSnapshot(Long userId) {
        if (userId == null) {
            return null;
        }
        return bCardFeatureSnapshotMapper.selectOne(
                new QueryWrapper<BCardFeatureSnapshot>()
                        .eq("user_id", userId)
                        .orderByDesc("snapshot_time")
                        .last("LIMIT 1"));
    }

    @Override
    public int createSnapshotsForBCardUsers(LocalDate asOfDate) {
        List<UserCreditLimit> users = userCreditLimitMapper.selectList(
                new QueryWrapper<UserCreditLimit>().eq("b_card_enabled", true));
        int count = 0;
        for (UserCreditLimit user : users) {
            try {
                createSnapshot(user.getUserId(), asOfDate);
                count++;
            } catch (Exception e) {
                log.warn("创建 B 卡特征快照失败 userId={}", user.getUserId(), e);
            }
        }
        return count;
    }

    private boolean isEffectiveLoan(Loan loan) {
        if (loan == null || loan.getStatus() == null) {
            return false;
        }
        return switch (loan.getStatus()) {
            case "DISBURSED", "OVERDUE", "REPAID" -> true;
            default -> false;
        };
    }

    private int calculateDpd(RepaymentRecord record, LocalDate asOfDate) {
        LocalDate dueDate = toLocalDate(record.getDueDate());
        if (dueDate == null) {
            return 0;
        }
        LocalDate endDate = toLocalDate(record.getRepaymentDate());
        if (endDate == null) {
            endDate = asOfDate;
        }
        long days = ChronoUnit.DAYS.between(dueDate, endDate);
        return days > 0 ? (int) days : 0;
    }

    private double calculateUnpaidOverdueAmount(List<RepaymentRecord> records, LocalDate asOfDate) {
        return records.stream()
                .filter(record -> !isPaid(record))
                .filter(record -> {
                    LocalDate dueDate = toLocalDate(record.getDueDate());
                    return dueDate != null && dueDate.isBefore(asOfDate);
                })
                .map(RepaymentRecord::getAmount)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
    }

    private static boolean isPaid(RepaymentRecord record) {
        String status = record.getStatus();
        return "COMPLETED".equals(status) || "PAID".equals(status) || "SETTLED".equals(status);
    }

    private static boolean isOnTime(RepaymentRecord record) {
        return record.getRepaymentDate() != null
                && record.getDueDate() != null
                && !record.getRepaymentDate().after(record.getDueDate());
    }

    private static boolean isPartialPayment(RepaymentRecord record) {
        return record.getActualAmount() != null
                && record.getAmount() != null
                && record.getActualAmount().compareTo(record.getAmount()) < 0;
    }

    private LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void addDate(List<LocalDate> dates, Date date) {
        LocalDate localDate = toLocalDate(date);
        if (localDate != null) {
            dates.add(localDate);
        }
    }

    private static double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}
