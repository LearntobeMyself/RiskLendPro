package org.example.risklendpro.risk.bcard;

import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.risk.entity.BCardFeatureSnapshot;
import org.example.risklendpro.risk.mapper.BCardFeatureSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 A2：标签成熟与状态流转。
 *
 * <p>覆盖 30+ DPD / 90+ DPD 两个标签的成熟、未成熟不得提前打标签、
 * OPEN → MATURED → LABELED 流转以及幂等性。</p>
 */
@ExtendWith(MockitoExtension.class)
class BCardLabelServiceImplTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 10, 15);

    @InjectMocks
    private BCardLabelServiceImpl service;

    @Mock
    private BCardFeatureSnapshotMapper bCardFeatureSnapshotMapper;

    @Mock
    private LoanMapper loanMapper;

    @Mock
    private RepaymentRecordMapper repaymentRecordMapper;

    @BeforeEach
    void setUp() {
        service.setReferenceDateForReplay(REFERENCE_DATE);
    }

    @Test
    void applyLabelsIfMatured_skipsSampleWhoseWindowsAreNotMatured() {
        LocalDate asOf = REFERENCE_DATE.minusMonths(3);
        BCardFeatureSnapshot snapshot = snapshot(asOf);

        BCardFeatureSnapshot result = service.applyLabelsIfMatured(snapshot, REFERENCE_DATE);

        assertNull(result, "未成熟样本不得生成标签");
        assertNull(snapshot.getLabel30dpd6m());
        assertNull(snapshot.getLabel90dpd12m());
        assertEquals(BCardFeatureServiceImpl.STATUS_OPEN, snapshot.getSampleStatus());
        verify(bCardFeatureSnapshotMapper, never()).updateById(any(BCardFeatureSnapshot.class));
        verify(loanMapper, never()).selectList(any());
    }

    @Test
    void applyLabelsIfMatured_writesThirtyDpdLabelAndStaysMatured() {
        LocalDate asOf = REFERENCE_DATE.minusMonths(8);
        BCardFeatureSnapshot snapshot = snapshot(asOf);
        // 期次在表现期内长期未还清：DPD 计算到 6 个月窗口结束日（2026-08-15）共 122 天
        stubRecords(overdueRecord(300L, asOf.plusMonths(2), null));
        when(bCardFeatureSnapshotMapper.updateById(any(BCardFeatureSnapshot.class))).thenReturn(1);

        BCardFeatureSnapshot result = service.applyLabelsIfMatured(snapshot, REFERENCE_DATE);

        assertNotNull(result);
        assertEquals(1, snapshot.getLabel30dpd6m());
        assertNull(snapshot.getLabel90dpd12m(), "12 个月窗口未结束，标签必须保持为空");
        assertEquals(BCardLabelServiceImpl.LABEL_VERSION, snapshot.getLabelVersion());
        assertNotNull(snapshot.getLabelCalculatedAt());
        assertEquals(BCardFeatureServiceImpl.STATUS_MATURED, snapshot.getSampleStatus());
        verify(bCardFeatureSnapshotMapper).updateById(snapshot);
    }

    @Test
    void applyLabelsIfMatured_writesBothLabelsAndMarksLabeled() {
        LocalDate asOf = REFERENCE_DATE.minusMonths(14);
        BCardFeatureSnapshot snapshot = snapshot(asOf);
        // 逾期 36 天：达到 30+，未达到 90+
        stubRecords(overdueRecord(300L, asOf.plusMonths(2), asOf.plusMonths(2).plusDays(36)));
        when(bCardFeatureSnapshotMapper.updateById(any(BCardFeatureSnapshot.class))).thenReturn(1);

        service.applyLabelsIfMatured(snapshot, REFERENCE_DATE);

        assertEquals(1, snapshot.getLabel30dpd6m());
        assertEquals(0, snapshot.getLabel90dpd12m());
        assertEquals(BCardFeatureServiceImpl.STATUS_LABELED, snapshot.getSampleStatus());
    }

    @Test
    void applyLabelsIfMatured_writesZeroOnlyAfterWindowEndsWithoutEvent() {
        LocalDate asOf = REFERENCE_DATE.minusMonths(14);
        BCardFeatureSnapshot snapshot = snapshot(asOf);
        // 提前还款，无任何逾期事件
        stubRecords(overdueRecord(300L, asOf.plusMonths(2), asOf.plusMonths(2).minusDays(5)));
        when(bCardFeatureSnapshotMapper.updateById(any(BCardFeatureSnapshot.class))).thenReturn(1);

        service.applyLabelsIfMatured(snapshot, REFERENCE_DATE);

        assertEquals(0, snapshot.getLabel30dpd6m());
        assertEquals(0, snapshot.getLabel90dpd12m());
        assertEquals(BCardFeatureServiceImpl.STATUS_LABELED, snapshot.getSampleStatus());
    }

    @Test
    void applyLabelsIfMatured_isIdempotentForCompletedSamples() {
        BCardFeatureSnapshot snapshot = snapshot(REFERENCE_DATE.minusMonths(14));
        snapshot.setSampleStatus(BCardFeatureServiceImpl.STATUS_LABELED);
        snapshot.setLabelVersion(BCardLabelServiceImpl.LABEL_VERSION);
        snapshot.setLabel30dpd6m(1);
        snapshot.setLabel90dpd12m(0);

        assertNull(service.applyLabelsIfMatured(snapshot, REFERENCE_DATE));

        verify(bCardFeatureSnapshotMapper, never()).updateById(any(BCardFeatureSnapshot.class));
        verify(loanMapper, never()).selectList(any());
    }

    @Test
    void applyLabelsIfMatured_skipsExcludedSamples() {
        BCardFeatureSnapshot snapshot = snapshot(REFERENCE_DATE.minusMonths(14));
        snapshot.setSampleStatus(BCardFeatureServiceImpl.STATUS_EXCLUDED);

        assertNull(service.applyLabelsIfMatured(snapshot, REFERENCE_DATE));

        verify(bCardFeatureSnapshotMapper, never()).updateById(any(BCardFeatureSnapshot.class));
    }

    @Test
    void backfillMaturedLabels_countsOnlySamplesActuallyWritten() {
        BCardFeatureSnapshot notMatured = snapshot(REFERENCE_DATE.minusMonths(2));
        BCardFeatureSnapshot matured = snapshot(REFERENCE_DATE.minusMonths(14));
        when(bCardFeatureSnapshotMapper.selectLabelCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(notMatured, matured));
        stubRecords(overdueRecord(300L, matured.getAsOfDate().plusMonths(2),
                matured.getAsOfDate().plusMonths(2).plusDays(40)));
        when(bCardFeatureSnapshotMapper.updateById(any(BCardFeatureSnapshot.class))).thenReturn(1);

        int labeled = service.backfillMaturedLabels(500);

        assertEquals(1, labeled);
        assertNull(notMatured.getLabel30dpd6m());
        assertEquals(BCardFeatureServiceImpl.STATUS_LABELED, matured.getSampleStatus());
    }

    @Test
    void maxDpdWithinWindow_ignoresDelinquencyEndedBeforeWindowStart() {
        LocalDate windowStart = LocalDate.of(2025, 6, 1);
        LocalDate windowEnd = LocalDate.of(2025, 8, 15);
        // 该期次在窗口开始前就已经还清：不应计入本次表现期
        RepaymentRecord settledBefore = overdueRecord(300L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1));
        // 窗口内未还清：DPD 计算到窗口结束日 2025-08-15 共 61 天
        RepaymentRecord openInWindow = overdueRecord(301L, LocalDate.of(2025, 6, 15), null);

        assertEquals(61, BCardLabelServiceImpl.maxDpdWithinWindow(
                List.of(settledBefore, openInWindow), windowStart, windowEnd));
        assertEquals(0, BCardLabelServiceImpl.computeLabel(
                List.of(settledBefore), windowStart, windowEnd, 30));
    }

    // ------------------------------------------------------------------

    private static BCardFeatureSnapshot snapshot(LocalDate asOf) {
        BCardFeatureSnapshot snapshot = new BCardFeatureSnapshot();
        snapshot.setId(1L);
        snapshot.setUserId(68L);
        snapshot.setAsOfDate(asOf);
        snapshot.setFeatureVersion(BCardFeatureServiceImpl.FEATURE_VERSION);
        snapshot.setSampleStatus(BCardFeatureServiceImpl.STATUS_OPEN);
        snapshot.setMaturityDate30dpd6m(asOf.plusMonths(6));
        snapshot.setMaturityDate90dpd12m(asOf.plusMonths(12));
        return snapshot;
    }

    private void stubRecords(RepaymentRecord... records) {
        Loan loan = new Loan();
        loan.setLoanId(100L);
        loan.setUserId(68L);
        when(loanMapper.selectList(any())).thenReturn(List.of(loan));
        when(repaymentRecordMapper.selectList(any())).thenReturn(List.of(records));
    }

    private static RepaymentRecord overdueRecord(Long recordId, LocalDate dueDate, LocalDate repaymentDate) {
        RepaymentRecord record = new RepaymentRecord();
        record.setRecordId(recordId);
        record.setLoanId(100L);
        record.setDueDate(toDate(dueDate));
        if (repaymentDate != null) {
            record.setRepaymentDate(toDate(repaymentDate));
        }
        record.setStatus(repaymentDate == null ? "OVERDUE" : "COMPLETED");
        record.setAmount(BigDecimal.valueOf(1000));
        record.setActualAmount(BigDecimal.valueOf(repaymentDate == null ? 0 : 1000));
        return record;
    }

    private static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}