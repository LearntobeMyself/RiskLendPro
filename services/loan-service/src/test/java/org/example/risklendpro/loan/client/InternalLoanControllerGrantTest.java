package org.example.risklendpro.loan.client;

import org.example.risklendpro.api.dto.CreditLimitGrantCommand;
import org.example.risklendpro.api.dto.CreditLimitSnapshot;
import org.example.risklendpro.loan.entity.LimitAdjustLog;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.LimitAdjustLogMapper;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalLoanControllerGrantTest {

    @InjectMocks
    private InternalLoanController controller;

    @Mock
    private UserCreditLimitMapper userCreditLimitMapper;
    @Mock
    private LimitAdjustLogMapper limitAdjustLogMapper;
    @Mock
    private LoanMapper loanMapper;
    @Mock
    private RepaymentPlanMapper repaymentPlanMapper;
    @Mock
    private RepaymentRecordMapper repaymentRecordMapper;

    @Test
    void grantCreditLimit_sameAssessment_doesNotStack() {
        UserCreditLimit existing = limit(new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"));
        when(userCreditLimitMapper.selectOne(any())).thenReturn(existing);
        when(limitAdjustLogMapper.selectCount(any())).thenReturn(1L);

        CreditLimitSnapshot snapshot = controller.grantCreditLimit(
                new CreditLimitGrantCommand(7L, "apply-1", new BigDecimal("10000")));

        assertEquals(new BigDecimal("10000"), snapshot.totalLimit());
        verify(userCreditLimitMapper, never()).updateById(any());
        verify(limitAdjustLogMapper, never()).insert(any());
    }

    @Test
    void grantCreditLimit_newAssessment_addsOnce() {
        UserCreditLimit existing = limit(new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"));
        when(userCreditLimitMapper.selectOne(any())).thenReturn(existing);
        when(limitAdjustLogMapper.selectCount(any())).thenReturn(0L);

        CreditLimitSnapshot snapshot = controller.grantCreditLimit(
                new CreditLimitGrantCommand(7L, "apply-2", new BigDecimal("5000")));

        assertEquals(new BigDecimal("15000"), snapshot.totalLimit());
        assertEquals(new BigDecimal("15000"), snapshot.remainingLimit());
        verify(userCreditLimitMapper).updateById(existing);
        verify(limitAdjustLogMapper).insert(any(LimitAdjustLog.class));
    }

    private static UserCreditLimit limit(BigDecimal total, BigDecimal used, BigDecimal remaining) {
        UserCreditLimit cl = new UserCreditLimit();
        cl.setUserId(7L);
        cl.setTotalLimit(total);
        cl.setUsedLimit(used);
        cl.setRemainingLimit(remaining);
        cl.setHasOverdue(false);
        cl.setBCardEnabled(false);
        return cl;
    }
}
