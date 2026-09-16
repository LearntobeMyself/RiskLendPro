package org.example.risklendpro.risk.bcard;

import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.risk.mapper.BCardFeatureSnapshotMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BCardFeatureServiceImplTest {

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

    @Test
    void buildFeatureVector_calculatesDelinquencyAndUtilization() {
        LocalDate asOf = LocalDate.of(2026, 9, 11);

        Loan loan = new Loan();
        loan.setLoanId(100L);
        loan.setUserId(68L);
        loan.setAmount(BigDecimal.valueOf(5000));
        loan.setStatus("OVERDUE");
        loan.setDisbursementTime(toDate(LocalDate.of(2026, 5, 1)));

        RepaymentPlan plan = new RepaymentPlan();
        plan.setPlanId(200L);
        plan.setLoanId(100L);
        plan.setUserId(68L);
        plan.setStatus("OVERDUE");
        plan.setOverdueDays(30);
        plan.setRemainingAmount(BigDecimal.valueOf(3000));

        RepaymentRecord overdue = new RepaymentRecord();
        overdue.setRecordId(300L);
        overdue.setLoanId(100L);
        overdue.setDueDate(toDate(LocalDate.of(2026, 7, 1)));
        overdue.setAmount(BigDecimal.valueOf(1000));
        overdue.setStatus("OVERDUE");

        RepaymentRecord latePaid = new RepaymentRecord();
        latePaid.setRecordId(301L);
        latePaid.setLoanId(100L);
        latePaid.setDueDate(toDate(LocalDate.of(2026, 5, 1)));
        latePaid.setRepaymentDate(toDate(LocalDate.of(2026, 5, 3)));
        latePaid.setAmount(BigDecimal.valueOf(1000));
        latePaid.setActualAmount(BigDecimal.valueOf(1000));
        latePaid.setStatus("COMPLETED");

        UserCreditLimit limit = new UserCreditLimit();
        limit.setUserId(68L);
        limit.setTotalLimit(BigDecimal.valueOf(10000));
        limit.setUsedLimit(BigDecimal.valueOf(6000));
        limit.setOverdueAmount(BigDecimal.valueOf(1000));
        limit.setHasOverdue(true);

        when(loanMapper.selectList(any())).thenReturn(List.of(loan));
        when(repaymentPlanMapper.selectList(any())).thenReturn(List.of(plan));
        when(repaymentRecordMapper.selectList(any())).thenReturn(List.of(overdue, latePaid));
        when(userCreditLimitMapper.selectOne(any())).thenReturn(limit);

        BCardFeatureVector result = service.buildFeatureVector(68L, asOf);

        assertEquals(1, result.getOverduePlanCount());
        assertEquals(72, result.getMaxDpd12m());
        assertEquals(2, result.getLatePaymentCount12m());
        assertEquals(0.6, result.getUtilizationRate(), 0.001);
        assertTrue(result.isHasOverdue());
    }

    private static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
