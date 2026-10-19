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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * B 卡贷后特征快照（as-of 口径，阶段 A2）。
 *
 * <p>硬性约束：</p>
 * <ul>
 *   <li>所有贷款、还款计划、还款记录、额度、逾期金额都按 asOfDate 过滤，
 *       任何 asOfDate 之后才发生的数据（放款、还款、逾期、额度变化）都不得进入历史快照；</li>
 *   <li>贷款/计划状态不读取当前行的 status、overdueDays 等可变字段，而是由 asOfDate
 *       当时的还款记录推导；</li>
 *   <li>额度一律读取 {@code user_credit_limit_history} 不可变快照；仅当 asOfDate 就是当天时
 *       才允许回退到实时额度行（此时实时值即为 as-of 值）；</li>
 *   <li>创建快照时不生成任何标签，标签由 BCardLabelService 在表现期结束后回填。</li>
 * </ul>
 */
@Service
public class BCardFeatureServiceImpl implements BCardFeatureService {

    private static final Logger log = LoggerFactory.getLogger(BCardFeatureServiceImpl.class);

    /** 特征口径版本：A2 起为 as-of 口径，与 BF_V2.1 语义不兼容，因此并存而不是覆盖。 */
    public static final String FEATURE_VERSION = "BF_V2.2";
    private static final int OBSERVATION_DAYS = 180;
    /** 两个标签的最长表现期（12 个月），写入 performance_end。 */
    private static final int PERFORMANCE_MONTHS = 12;

    private static final String DATA_SOURCE =
            "RiskLendPro:loan,repayment_plan,repayment_record,user_credit_limit_history";

    public static final String QUALITY_OK = "OK";
    public static final String QUALITY_DEGRADED = "DEGRADED";
    public static final String QUALITY_INSUFFICIENT = "INSUFFICIENT";

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_MATURED = "MATURED";
    public static final String STATUS_LABELED = "LABELED";
    public static final String STATUS_EXCLUDED = "EXCLUDED";

    public static final String FLAG_UTILIZATION_GT_2 = "UTILIZATION_GT_2";
    public static final String FLAG_TENURE_LT_MAX_DPD = "TENURE_LT_MAX_DPD";
    public static final String FLAG_LIMIT_HISTORY_MISSING = "LIMIT_HISTORY_MISSING";
    public static final String FLAG_LIMIT_FROM_LIVE_ROW_TODAY = "LIMIT_FROM_LIVE_ROW_TODAY";
    public static final String FLAG_RECORD_CREATE_TIME_MISSING = "RECORD_CREATE_TIME_MISSING";
    public static final String FLAG_PAID_WITHOUT_REPAYMENT_DATE = "PAID_WITHOUT_REPAYMENT_DATE";
    public static final String FLAG_PLAN_WITHOUT_RECORDS = "PLAN_WITHOUT_RECORDS";
    public static final String FLAG_NO_OBSERVATION_DATA = "NO_OBSERVATION_DATA";

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

    @Autowired
    private BCardCreditLimitHistoryReader creditLimitHistoryReader;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public BCardFeatureVector buildFeatureVector(Long userId, LocalDate asOfDate) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");

        // ---- 贷款：只保留 asOfDate 当日及以前已放款的贷款；状态按 asOfDate 推导 ----
        List<Loan> effectiveLoans = loanMapper.selectList(
                        new QueryWrapper<Loan>().eq("user_id", userId))
                .stream()
                .filter(loan -> isLoanEffectiveAt(loan, asOfDate))
                .collect(Collectors.toList());
        List<Long> loanIds = effectiveLoans.stream()
                .map(Loan::getLoanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // ---- 还款计划：创建时间晚于 asOfDate 的计划当时并不存在 ----
        List<RepaymentPlan> plans = repaymentPlanMapper.selectList(
                        new QueryWrapper<RepaymentPlan>().eq("user_id", userId))
                .stream()
                .filter(plan -> isVisibleAt(plan.getCreateTime(), asOfDate))
                .collect(Collectors.toList());

        // ---- 还款记录：同样按创建时间过滤，避免把未来新增的期次算进历史 ----
        List<RepaymentRecord> records = loanIds.isEmpty()
                ? new ArrayList<>()
                : repaymentRecordMapper.selectList(
                                new QueryWrapper<RepaymentRecord>().in("loan_id", loanIds))
                        .stream()
                        .filter(record -> isVisibleAt(record.getCreateTime(), asOfDate))
                        .collect(Collectors.toList());

        Map<Long, List<RepaymentRecord>> recordsByLoan = records.stream()
                .filter(record -> record.getLoanId() != null)
                .collect(Collectors.groupingBy(RepaymentRecord::getLoanId));

        BCardFeatureVector feature = new BCardFeatureVector();
        List<String> flags = new ArrayList<>();

        // ---- 贷款维度（as-of 推导，不读当前 status 作为历史状态）----
        long repaidLoanCount = effectiveLoans.stream()
                .filter(loan -> isLoanRepaidAt(recordsByLoan.get(loan.getLoanId()), asOfDate))
                .count();
        long overdueLoanCount = effectiveLoans.stream()
                .filter(loan -> isLoanOverdueAt(recordsByLoan.get(loan.getLoanId()), asOfDate))
                .count();
        feature.setLoanCountTotal(effectiveLoans.size());
        feature.setRepaidLoanCount(repaidLoanCount);
        feature.setOverdueLoanCount(overdueLoanCount);
        feature.setActiveLoanCount(effectiveLoans.size() - repaidLoanCount);

        long plansWithoutRecords = plans.stream()
                .filter(plan -> {
                    List<RepaymentRecord> loanRecords = recordsByLoan.get(plan.getLoanId());
                    return loanRecords == null || loanRecords.isEmpty();
                })
                .count();
        if (plansWithoutRecords > 0) {
            flags.add(FLAG_PLAN_WITHOUT_RECORDS);
        }

        feature.setOverduePlanCount(plans.stream()
                .filter(plan -> isLoanOverdueAt(recordsByLoan.get(plan.getLoanId()), asOfDate))
                .count());

        // ---- 逐条记录的 as-of 派生 ----
        LocalDate windowStart = asOfDate.minusMonths(12);
        int currentMaxDpd = 0;
        int maxDpd12m = 0;
        long dpdSum12m = 0;
        long dpdCount12m = 0;
        long latePaymentCount12m = 0;
        LocalDate lastOverdueDate = null;
        long paidRecordCount = 0;
        long onTimeRecordCount12m = 0;
        long paidRecordCount12m = 0;
        long partialPaymentCount12m = 0;
        double outstandingAmount = 0.0;
        double derivedOverdueAmount = 0.0;
        boolean recordCreateTimeMissing = false;
        boolean paidWithoutRepaymentDate = false;

        for (RepaymentRecord record : records) {
            if (record.getCreateTime() == null) {
                recordCreateTimeMissing = true;
            }
            boolean paid = isPaidAt(record, asOfDate);
            if (paid) {
                paidRecordCount++;
                if (record.getRepaymentDate() == null) {
                    paidWithoutRepaymentDate = true;
                }
            }

            LocalDate dueDate = toLocalDate(record.getDueDate());
            double outstanding = outstandingAsOf(record, paid);
            outstandingAmount += outstanding;

            boolean pastDue = dueDate != null && dueDate.isBefore(asOfDate);
            if (pastDue && !paid) {
                currentMaxDpd = Math.max(currentMaxDpd, (int) ChronoUnit.DAYS.between(dueDate, asOfDate));
                derivedOverdueAmount += outstanding;
            }

            // 12 个月窗口：必须同时满足下界与上界，asOfDate 之后到期的期次不计入历史
            if (dueDate == null || dueDate.isBefore(windowStart) || dueDate.isAfter(asOfDate)) {
                continue;
            }

            int dpd = dpdAt(record, asOfDate);
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
        feature.setOutstandingAmount(outstandingAmount);
        feature.setOverdueAmount(derivedOverdueAmount);
        feature.setHasOverdue(currentMaxDpd > 0);

        if (recordCreateTimeMissing) {
            flags.add(FLAG_RECORD_CREATE_TIME_MISSING);
        }
        if (paidWithoutRepaymentDate) {
            flags.add(FLAG_PAID_WITHOUT_REPAYMENT_DATE);
        }

        // ---- 额度：只认 as-of 历史快照，绝不把当前额度写进历史 ----
        Optional<BCardCreditLimitHistoryReader.CreditLimitAsOf> limitAsOf =
                creditLimitHistoryReader.findAsOf(userId, asOfDate);
        double totalLimit;
        double usedLimit;
        double remainingLimit;
        if (limitAsOf.isPresent()) {
            BCardCreditLimitHistoryReader.CreditLimitAsOf snapshot = limitAsOf.get();
            totalLimit = snapshot.totalLimit();
            usedLimit = snapshot.usedLimit();
            remainingLimit = snapshot.remainingLimit();
            feature.setOverdueAmount(Math.max(derivedOverdueAmount, snapshot.overdueAmount()));
            feature.setHasOverdue(feature.isHasOverdue() || snapshot.hasOverdue());
        } else if (isToday(asOfDate)) {
            // asOfDate 就是当天时，实时额度行本身就是 as-of 值，允许回退
            UserCreditLimit live = userCreditLimitMapper.selectOne(
                    new QueryWrapper<UserCreditLimit>().eq("user_id", userId));
            totalLimit = live == null ? 0.0 : toDouble(live.getTotalLimit());
            usedLimit = live == null ? 0.0 : toDouble(live.getUsedLimit());
            remainingLimit = live == null ? 0.0 : toDouble(live.getRemainingLimit());
            flags.add(FLAG_LIMIT_FROM_LIVE_ROW_TODAY);
        } else {
            // 历史日期且没有额度快照：宁可用 0 并打标，也不能用当前额度造成时间穿越
            totalLimit = 0.0;
            usedLimit = 0.0;
            remainingLimit = 0.0;
            flags.add(FLAG_LIMIT_HISTORY_MISSING);
            log.warn("历史快照缺少额度历史 userId={} asOfDate={}，额度特征置 0", userId, asOfDate);
        }

        double utilizationRate = totalLimit <= 0.0 ? 0.0 : usedLimit / totalLimit;
        feature.setUtilizationRate(utilizationRate);
        feature.setCappedUtilizationRate(Math.min(utilizationRate, 2.0));
        feature.setTotalLimit(totalLimit);
        feature.setUsedLimit(usedLimit);
        feature.setRemainingLimit(remainingLimit);
        if (utilizationRate > 2.0) {
            flags.add(FLAG_UTILIZATION_GT_2);
        }

        // ---- 借款金额与近期新增贷款（含 asOfDate 上界）----
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
                .filter(loan -> !toLocalDate(loan.getDisbursementTime()).isAfter(asOfDate))
                .max((left, right) -> left.getDisbursementTime().compareTo(right.getDisbursementTime()))
                .orElse(null);
        feature.setLastLoanAmount(lastLoan == null ? 0.0 : toDouble(lastLoan.getAmount()));

        LocalDate ninetyDaysAgo = asOfDate.minusDays(90);
        LocalDate oneEightyDaysAgo = asOfDate.minusDays(180);
        feature.setRecentNewLoanCount90d(countDisbursedBetween(effectiveLoans, ninetyDaysAgo, asOfDate));
        feature.setRecentNewLoanCount180d(countDisbursedBetween(effectiveLoans, oneEightyDaysAgo, asOfDate));

        // ---- 账龄：只取 asOfDate 当日及以前的已知时间点 ----
        List<LocalDate> knownDates = new ArrayList<>();
        for (Loan loan : effectiveLoans) {
            addVisibleDate(knownDates, loan.getDisbursementTime(), asOfDate);
            addVisibleDate(knownDates, loan.getApplyTime(), asOfDate);
            addVisibleDate(knownDates, loan.getApproveTime(), asOfDate);
        }
        for (RepaymentPlan plan : plans) {
            addVisibleDate(knownDates, plan.getCreateTime(), asOfDate);
            addVisibleDate(knownDates, plan.getUpdateTime(), asOfDate);
        }
        for (RepaymentRecord record : records) {
            addVisibleDate(knownDates, record.getCreateTime(), asOfDate);
            addVisibleDate(knownDates, record.getDueDate(), asOfDate);
        }
        LocalDate firstKnownDate = knownDates.stream().min(LocalDate::compareTo).orElse(null);
        long tenureDays = firstKnownDate == null
                ? 0L
                : Math.max(0L, ChronoUnit.DAYS.between(firstKnownDate, asOfDate));
        feature.setTenureDays(tenureDays);

        if (effectiveLoans.isEmpty() && records.isEmpty()) {
            flags.add(FLAG_NO_OBSERVATION_DATA);
        }
        if (tenureDays < currentMaxDpd) {
            flags.add(FLAG_TENURE_LT_MAX_DPD);
        }
        feature.setDataQualityFlags(flags);

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

        BCardFeatureSnapshot existing = bCardFeatureSnapshotMapper.selectOne(
                new QueryWrapper<BCardFeatureSnapshot>()
                        .eq("user_id", userId)
                        .eq("as_of_date", asOfDate)
                        .eq("feature_version", FEATURE_VERSION));
        boolean insert = existing == null;
        // 已存在的行直接复用：其标签、标签版本与状态不会被特征重算覆盖
        BCardFeatureSnapshot snapshot = insert ? new BCardFeatureSnapshot() : existing;

        snapshot.setUserId(userId);
        snapshot.setAsOfDate(asOfDate);
        snapshot.setSnapshotTime(LocalDateTime.now());
        snapshot.setFeatureVersion(FEATURE_VERSION);
        snapshot.setObservationStart(asOfDate.minusDays(OBSERVATION_DAYS));
        snapshot.setObservationEnd(asOfDate);
        snapshot.setPerformanceStart(asOfDate.plusDays(1));
        snapshot.setPerformanceEnd(asOfDate.plusMonths(PERFORMANCE_MONTHS));
        snapshot.setMaturityDate30dpd6m(asOfDate.plusMonths(6));
        snapshot.setMaturityDate90dpd12m(asOfDate.plusMonths(12));
        snapshot.setFeatureJson(featureJson);
        snapshot.setFeatureHash(sha256Hex(featureJson));
        snapshot.setDataSource(DATA_SOURCE);
        snapshot.setDataQualityStatus(resolveDataQualityStatus(featureVector.getDataQualityFlags()));

        if (insert) {
            // 创建时不生成任何标签：标签只能由 BCardLabelService 在表现期结束后写入
            snapshot.setLabel30dpd6m(null);
            snapshot.setLabel90dpd12m(null);
            snapshot.setLabelVersion(null);
            snapshot.setLabelCalculatedAt(null);
            snapshot.setSampleStatus(resolveInitialSampleStatus(snapshot.getDataQualityStatus()));
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

    /** 数据质量不合格（无任何观察数据）的样本直接排除，不进入训练。 */
    public static String resolveInitialSampleStatus(String dataQualityStatus) {
        return QUALITY_INSUFFICIENT.equals(dataQualityStatus) ? STATUS_EXCLUDED : STATUS_OPEN;
    }

    public static String resolveDataQualityStatus(List<String> flags) {
        if (flags == null || flags.isEmpty()) {
            return QUALITY_OK;
        }
        if (flags.contains(FLAG_NO_OBSERVATION_DATA)) {
            return QUALITY_INSUFFICIENT;
        }
        return QUALITY_DEGRADED;
    }

    // ------------------------------------------------------------------
    // as-of 判定
    // ------------------------------------------------------------------

    /** 贷款在 asOfDate 是否已经生效（当日及以前放款，且当前状态说明它曾经放款过）。 */
    private boolean isLoanEffectiveAt(Loan loan, LocalDate asOfDate) {
        if (loan == null || loan.getStatus() == null) {
            return false;
        }
        boolean disbursed = switch (loan.getStatus()) {
            case "DISBURSED", "OVERDUE", "REPAID" -> true;
            default -> false;
        };
        if (!disbursed) {
            return false;
        }
        return loan.getDisbursementTime() != null
                && !toLocalDate(loan.getDisbursementTime()).isAfter(asOfDate);
    }

    /** 该时间点在 asOfDate 是否已经发生（null 视为未知，保守保留）。 */
    private static boolean isVisibleAt(Date time, LocalDate asOfDate) {
        if (time == null) {
            return true;
        }
        return !toLocalDate(time).isAfter(asOfDate);
    }

    private static boolean isLoanOverdueAt(List<RepaymentRecord> loanRecords, LocalDate asOfDate) {
        if (loanRecords == null || loanRecords.isEmpty()) {
            return false;
        }
        return loanRecords.stream().anyMatch(record -> {
            LocalDate dueDate = toLocalDate(record.getDueDate());
            return dueDate != null && dueDate.isBefore(asOfDate) && !isPaidAt(record, asOfDate);
        });
    }

    private static boolean isLoanRepaidAt(List<RepaymentRecord> loanRecords, LocalDate asOfDate) {
        if (loanRecords == null || loanRecords.isEmpty()) {
            return false;
        }
        return loanRecords.stream().allMatch(record -> isPaidAt(record, asOfDate));
    }

    /**
     * 还款记录在 asOfDate 是否已还清。
     * 关键：还款日必须不晚于 asOfDate，否则「asOfDate 之后才发生的还款」会被算成历史已还。
     */
    private static boolean isPaidAt(RepaymentRecord record, LocalDate asOfDate) {
        if (!isPaidStatus(record.getStatus())) {
            return false;
        }
        LocalDate repaymentDate = toLocalDate(record.getRepaymentDate());
        return repaymentDate == null || !repaymentDate.isAfter(asOfDate);
    }

    /** 记录的 DPD：还款日不得晚于 asOfDate，未还清则计算到 asOfDate。 */
    private static int dpdAt(RepaymentRecord record, LocalDate asOfDate) {
        LocalDate dueDate = toLocalDate(record.getDueDate());
        if (dueDate == null) {
            return 0;
        }
        LocalDate repaymentDate = toLocalDate(record.getRepaymentDate());
        LocalDate endDate = repaymentDate != null && !repaymentDate.isAfter(asOfDate)
                ? repaymentDate
                : asOfDate;
        long days = ChronoUnit.DAYS.between(dueDate, endDate);
        return days > 0 ? (int) days : 0;
    }

    private static double outstandingAsOf(RepaymentRecord record, boolean paidAsOf) {
        double amount = toDouble(record.getAmount());
        double actual = toDouble(record.getActualAmount());
        if (paidAsOf) {
            return Math.max(0.0, amount - actual);
        }
        return amount;
    }

    private static boolean isPaidStatus(String status) {
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

    private static long countDisbursedBetween(List<Loan> loans, LocalDate from, LocalDate to) {
        return loans.stream()
                .filter(loan -> loan.getDisbursementTime() != null)
                .filter(loan -> {
                    LocalDate date = toLocalDate(loan.getDisbursementTime());
                    return !date.isBefore(from) && !date.isAfter(to);
                })
                .count();
    }

    private static void addVisibleDate(List<LocalDate> dates, Date date, LocalDate asOfDate) {
        LocalDate localDate = toLocalDate(date);
        if (localDate != null && !localDate.isAfter(asOfDate)) {
            dates.add(localDate);
        }
    }

    private static boolean isToday(LocalDate date) {
        return date.equals(LocalDate.now());
    }

    private static LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    static String sha256Hex(String content) {
        if (content == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}