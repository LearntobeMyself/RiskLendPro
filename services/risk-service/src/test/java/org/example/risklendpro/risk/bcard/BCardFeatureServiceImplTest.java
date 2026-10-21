package org.example.risklendpro.risk.bcard;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 A2：as-of 特征快照。
 *
 * <p>覆盖 asOfDate 过去/现在边界、asOfDate 之后才发生的还款不得进入历史、
 * maxDpd12m 窗口上下界、额度不得读取当前值，以及快照的标签占位与序列化可复现性。</p>
 */
@ExtendWith(MockitoExtension.class)
class BCardFeatureServiceImplTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 11);

    @InjectMocks
    private BCardFeatureServiceImpl service;

    @Mock
    private LoanMapper loanMapper;

    @Mock
    private RepaymentPlanMapper repaymentPlanMapper;

    @Mock
    private RepaymentRecordMapper repaymentRecordMapper;

    @Mock
    private UserCreditLimitMapper userCreditLimitMapper;

    @Mock
    private BCardFeatureSnapshotMapper bCardFeatureSnapshotMapper;

    @Mock
    private BCardCreditLimitHistoryReader creditLimitHistoryReader;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildFeatureVector_derivesDelinquencyAndUtilizationAsOf() {
        stubBaseScenario();
        when(creditLimitHistoryReader.findAsOf(68L, AS_OF)).thenReturn(Optional.of(
                new BCardCreditLimitHistoryReader.CreditLimitAsOf(AS_OF, 10000, 6000, 4000, 0, false)));

        BCardFeatureVector result = service.buildFeatureVector(68L, AS_OF);

        assertEquals(1, result.getOverduePlanCount());
        assertEquals(72, result.getMaxDpd12m());
        assertEquals(2, result.getLatePaymentCount12m());
        assertEquals(0.6, result.getUtilizationRate(), 0.001);
        assertTrue(result.isHasOverdue());
        assertEquals(72, result.getCurrentMaxDpd());
        assertFalse(result.getDataQualityFlags().contains(BCardFeatureServiceImpl.FLAG_LIMIT_HISTORY_MISSING),
                "有额度历史时不应出现缺失标记");
    }

    @Test
    void buildFeatureVector_ignoresRepaymentMadeAfterAsOfDate() {
        Loan loan = loan(100L, 68L, "REPAID", LocalDate.of(2026, 5, 1));
        // 该期次在 asOfDate 之后才还款：历史快照必须仍然视为未还清
        RepaymentRecord record = record(300L, 100L, LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 20), "COMPLETED", 1000, 1000);
        stubSingleLoan(loan, List.of(record));
        when(creditLimitHistoryReader.findAsOf(any(), any())).thenReturn(Optional.of(
                new BCardCreditLimitHistoryReader.CreditLimitAsOf(AS_OF, 10000, 0, 10000, 0, false)));

        BCardFeatureVector atAsOf = service.buildFeatureVector(68L, AS_OF);

        // asOfDate 当天仍视为未还清：DPD 计算到 asOfDate，余额仍计入
        assertEquals(72, atAsOf.getCurrentMaxDpd());
        assertTrue(atAsOf.isHasOverdue());
        assertEquals(1000.0, atAsOf.getOutstandingAmount(), 0.001);
        assertEquals(0L, atAsOf.getPaidRecordCount());

        LocalDate afterRepayment = LocalDate.of(2026, 9, 25);
        when(creditLimitHistoryReader.findAsOf(68L, afterRepayment)).thenReturn(Optional.of(
                new BCardCreditLimitHistoryReader.CreditLimitAsOf(afterRepayment, 10000, 0, 10000, 0, false)));

        BCardFeatureVector later = service.buildFeatureVector(68L, afterRepayment);

        // 还款发生之后再看：已还清，余额归零，最大 DPD 固定为 actual 逾期天数
        assertEquals(1L, later.getPaidRecordCount());
        assertEquals(0.0, later.getOutstandingAmount(), 0.001);
        assertFalse(later.isHasOverdue());
        assertEquals(81, later.getMaxDpd12m());
    }

    @Test
    void buildFeatureVector_doesNotUseFutureOrTooOldRecordsInDpdWindow() {
        Loan loan = loan(100L, 68L, "DISBURSED", LocalDate.of(2025, 1, 1));
        // 窗口下界之前的逾期：不计入 12 个月窗口，但计入当前 DPD
        RepaymentRecord tooOld = record(300L, 100L, LocalDate.of(2025, 8, 1), null, "OVERDUE", 1000, 0);
        // 窗口内逾期
        RepaymentRecord inWindow = record(301L, 100L, LocalDate.of(2025, 12, 1), null, "OVERDUE", 1000, 0);
        // asOfDate 之后才到期：不得计入 12 个月窗口
        RepaymentRecord future = record(302L, 100L, LocalDate.of(2026, 12, 1), null, "OVERDUE", 1000, 0);
        // 期次在观察期内已建好，但到期日晚于 asOfDate：必须被 12 个月窗口上界排除
        future.setCreateTime(toDate(LocalDate.of(2026, 1, 1)));
        stubSingleLoan(loan, List.of(tooOld, inWindow, future));
        when(creditLimitHistoryReader.findAsOf(any(), any())).thenReturn(Optional.of(
                new BCardCreditLimitHistoryReader.CreditLimitAsOf(AS_OF, 10000, 0, 10000, 0, false)));

        BCardFeatureVector result = service.buildFeatureVector(68L, AS_OF);

        long expectedWindowDpd = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(2025, 12, 1), AS_OF);
        assertEquals((int) expectedWindowDpd, result.getMaxDpd12m(), "窗口内最大 DPD 应来自 2025-12-01 期次");
        long expectedCurrentDpd = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(2025, 8, 1), AS_OF);
        assertEquals((int) expectedCurrentDpd, result.getCurrentMaxDpd(), "当前 DPD 应包含更早的欠款");
    }

    @Test
    void buildFeatureVector_doesNotFallBackToCurrentLimitForHistoricalAsOfDate() {
        Loan loan = loan(100L, 68L, "DISBURSED", LocalDate.of(2026, 5, 1));
        stubSingleLoan(loan, List.of());
        when(creditLimitHistoryReader.findAsOf(68L, AS_OF)).thenReturn(Optional.empty());

        BCardFeatureVector result = service.buildFeatureVector(68L, AS_OF);

        assertEquals(0.0, result.getUtilizationRate(), 0.001);
        assertEquals(0.0, result.getTotalLimit(), 0.001);
        assertTrue(result.getDataQualityFlags().contains(BCardFeatureServiceImpl.FLAG_LIMIT_HISTORY_MISSING));
        verify(userCreditLimitMapper, never()).selectOne(any());
    }

    @Test
    void buildFeatureVector_usesLiveLimitOnlyWhenAsOfDateIsToday() {
        LocalDate today = LocalDate.now();
        Loan loan = loan(100L, 68L, "DISBURSED", today.minusMonths(3));
        stubSingleLoan(loan, List.of());
        when(creditLimitHistoryReader.findAsOf(68L, today)).thenReturn(Optional.empty());
        UserCreditLimit live = new UserCreditLimit();
        live.setUserId(68L);
        live.setTotalLimit(BigDecimal.valueOf(10000));
        live.setUsedLimit(BigDecimal.valueOf(6000));
        live.setRemainingLimit(BigDecimal.valueOf(4000));
        when(userCreditLimitMapper.selectOne(any())).thenReturn(live);

        BCardFeatureVector result = service.buildFeatureVector(68L, today);

        assertEquals(0.6, result.getUtilizationRate(), 0.001);
        assertTrue(result.getDataQualityFlags().contains(BCardFeatureServiceImpl.FLAG_LIMIT_FROM_LIVE_ROW_TODAY));
        assertFalse(result.getDataQualityFlags().contains(BCardFeatureServiceImpl.FLAG_LIMIT_HISTORY_MISSING));
    }

    @Test
    void createSnapshot_leavesLabelsEmptyAndRecordsMaturityMetadata() throws Exception {
        stubBaseScenario();
        when(creditLimitHistoryReader.findAsOf(68L, AS_OF)).thenReturn(Optional.of(
                new BCardCreditLimitHistoryReader.CreditLimitAsOf(AS_OF, 10000, 6000, 4000, 0, false)));
        when(bCardFeatureSnapshotMapper.selectOne(any())).thenReturn(null);
        when(bCardFeatureSnapshotMapper.insert(any(BCardFeatureSnapshot.class))).thenReturn(1);

        BCardFeatureSnapshot snapshot = service.createSnapshot(68L, AS_OF);

        assertEquals(BCardFeatureServiceImpl.STATUS_OPEN, snapshot.getSampleStatus());
        assertNull(snapshot.getLabel30dpd6m(), "创建时不得生成未成熟标签");
        assertNull(snapshot.getLabel90dpd12m(), "创建时不得生成未成熟标签");
        assertNull(snapshot.getLabelVersion());
        assertNull(snapshot.getLabelCalculatedAt());
        assertEquals(AS_OF.plusMonths(6), snapshot.getMaturityDate30dpd6m());
        assertEquals(AS_OF.plusMonths(12), snapshot.getMaturityDate90dpd12m());
        assertEquals(AS_OF.minusDays(180), snapshot.getObservationStart());
        assertEquals(AS_OF, snapshot.getObservationEnd());
        assertEquals(AS_OF.plusDays(1), snapshot.getPerformanceStart());
        assertEquals(AS_OF.plusMonths(12), snapshot.getPerformanceEnd());
        assertEquals(BCardFeatureServiceImpl.FEATURE_VERSION, snapshot.getFeatureVersion());
        assertEquals(BCardFeatureServiceImpl.QUALITY_OK, snapshot.getDataQualityStatus());
        assertNotNull(snapshot.getDataSource());
        assertEquals(BCardFeatureServiceImpl.sha256Hex(snapshot.getFeatureJson()), snapshot.getFeatureHash());
        verify(bCardFeatureSnapshotMapper).insert(snapshot);
    }

    @Test
    void createSnapshot_preservesExistingLabelsWhenFeatureIsRecalculated() {
        stubBaseScenario();
        when(creditLimitHistoryReader.findAsOf(68L, AS_OF)).thenReturn(Optional.of(
                new BCardCreditLimitHistoryReader.CreditLimitAsOf(AS_OF, 10000, 6000, 4000, 0, false)));

        BCardFeatureSnapshot existing = new BCardFeatureSnapshot();
        existing.setId(9L);
        existing.setUserId(68L);
        existing.setAsOfDate(AS_OF);
        existing.setFeatureVersion(BCardFeatureServiceImpl.FEATURE_VERSION);
        existing.setSampleStatus(BCardFeatureServiceImpl.STATUS_LABELED);
        existing.setLabel30dpd6m(1);
        existing.setLabel90dpd12m(0);
        existing.setLabelVersion("LBL_V1");
        existing.setLabelCalculatedAt(LocalDateTime.of(2027, 6, 1, 0, 0));
        when(bCardFeatureSnapshotMapper.selectOne(any())).thenReturn(existing);
        when(bCardFeatureSnapshotMapper.updateById(any(BCardFeatureSnapshot.class))).thenReturn(1);

        BCardFeatureSnapshot snapshot = service.createSnapshot(68L, AS_OF);

        assertEquals("LBL_V1", snapshot.getLabelVersion());
        assertEquals(1, snapshot.getLabel30dpd6m());
        assertEquals(0, snapshot.getLabel90dpd12m());
        assertEquals(BCardFeatureServiceImpl.STATUS_LABELED, snapshot.getSampleStatus());
        verify(bCardFeatureSnapshotMapper, never()).insert(any(BCardFeatureSnapshot.class));
        verify(bCardFeatureSnapshotMapper).updateById(snapshot);
    }

    @Test
    void featureVectorSerializationIsReproducibleAndRoundTrips() throws Exception {
        stubBaseScenario();
        when(creditLimitHistoryReader.findAsOf(68L, AS_OF)).thenReturn(Optional.of(
                new BCardCreditLimitHistoryReader.CreditLimitAsOf(AS_OF, 10000, 6000, 4000, 0, false)));

        BCardFeatureVector first = service.buildFeatureVector(68L, AS_OF);
        String firstJson = objectMapper.writeValueAsString(first);
        String secondJson = objectMapper.writeValueAsString(service.buildFeatureVector(68L, AS_OF));

        assertEquals(firstJson, secondJson, "同一 asOfDate 与同一输入必须可复现同一份特征 JSON");
        assertEquals(BCardFeatureServiceImpl.sha256Hex(firstJson), BCardFeatureServiceImpl.sha256Hex(secondJson));

        BCardFeatureVector restored = objectMapper.readValue(firstJson, BCardFeatureVector.class);
        assertEquals(first, restored, "特征向量序列化/反序列化必须一致");
    }

    // ------------------------------------------------------------------

    private void stubBaseScenario() {
        Loan loan = loan(100L, 68L, "OVERDUE", LocalDate.of(2026, 5, 1));
        RepaymentPlan plan = new RepaymentPlan();
        plan.setPlanId(200L);
        plan.setLoanId(100L);
        plan.setUserId(68L);
        plan.setStatus("OVERDUE");
        plan.setOverdueDays(30);
        plan.setRemainingAmount(BigDecimal.valueOf(3000));
        plan.setCreateTime(toDate(LocalDate.of(2026, 5, 1)));

        // 未还清期次：DPD 计算到 asOfDate 为 72 天
        RepaymentRecord overdue = record(300L, 100L, LocalDate.of(2026, 7, 1), null, "OVERDUE", 1000, 0);
        // 已还清但逾期 2 天
        RepaymentRecord latePaid = record(301L, 100L, LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3), "COMPLETED", 1000, 1000);

        when(loanMapper.selectList(any())).thenReturn(List.of(loan));
        when(repaymentPlanMapper.selectList(any())).thenReturn(List.of(plan));
        when(repaymentRecordMapper.selectList(any())).thenReturn(List.of(overdue, latePaid));
    }

    private void stubSingleLoan(Loan loan, List<RepaymentRecord> records) {
        when(loanMapper.selectList(any())).thenReturn(List.of(loan));
        when(repaymentPlanMapper.selectList(any())).thenReturn(List.of());
        if (!records.isEmpty()) {
            when(repaymentRecordMapper.selectList(any())).thenReturn(records);
        }
    }

    private static Loan loan(Long loanId, Long userId, String status, LocalDate disbursement) {
        Loan loan = new Loan();
        loan.setLoanId(loanId);
        loan.setUserId(userId);
        loan.setAmount(BigDecimal.valueOf(5000));
        loan.setStatus(status);
        loan.setDisbursementTime(toDate(disbursement));
        loan.setApplyTime(toDate(disbursement.minusDays(10)));
        return loan;
    }

    private static RepaymentRecord record(Long recordId, Long loanId, LocalDate dueDate,
                                          LocalDate repaymentDate, String status,
                                          double amount, double actualAmount) {
        RepaymentRecord record = new RepaymentRecord();
        record.setRecordId(recordId);
        record.setLoanId(loanId);
        record.setDueDate(toDate(dueDate));
        if (repaymentDate != null) {
            record.setRepaymentDate(toDate(repaymentDate));
        }
        record.setStatus(status);
        record.setAmount(BigDecimal.valueOf(amount));
        record.setActualAmount(BigDecimal.valueOf(actualAmount));
        record.setCreateTime(toDate(dueDate.minusMonths(1)));
        return record;
    }

    private static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}