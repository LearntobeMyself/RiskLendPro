package org.example.risklendpro.loan.repay;

import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归测试：修复2（越权校验）、修复3（金额足额校验）、修复7（幂等 + 行锁 + 并发保护）。
 */
@ExtendWith(MockitoExtension.class)
class RepaymentServiceImplTest {

    @InjectMocks
    private RepaymentServiceImpl service;

    @Mock
    private RepaymentPlanMapper repaymentPlanMapper;

    @Mock
    private RepaymentRecordMapper repaymentRecordMapper;

    private RepaymentPlan plan(Long ownerId) {
        RepaymentPlan p = new RepaymentPlan();
        p.setPlanId(1L);
        p.setUserId(ownerId);
        p.setTotalAmount(BigDecimal.valueOf(12000));
        p.setPaidAmount(BigDecimal.ZERO);
        p.setRemainingAmount(BigDecimal.valueOf(12000));
        p.setCurrentPeriod(1);
        p.setTotalPeriods(12);
        p.setStatus("ACTIVE");
        return p;
    }

    private RepaymentRecord record(BigDecimal amount) {
        RepaymentRecord r = new RepaymentRecord();
        r.setRecordId(100L);
        r.setPlanId(1L);
        r.setPeriod(1);
        r.setAmount(amount);
        r.setStatus("PENDING");
        return r;
    }

    private RepaymentExecuteRequest request() {
        RepaymentExecuteRequest req = new RepaymentExecuteRequest();
        req.setPlanId(1L);
        req.setPeriod(1);
        req.setAmount(BigDecimal.valueOf(1000));
        return req;
    }

    @Test
    void executeRepayment_otherUser_throwsAccessDenied() {
        RepaymentPlan plan = plan(10L); // 计划属于 userId = 10
        when(repaymentPlanMapper.selectOne(any())).thenReturn(plan);

        RepaymentExecuteRequest req = request();
        assertThrows(RuntimeException.class,
                () -> service.executeRepayment(99L, req)); // 越权：操作者非本人
    }

    @Test
    void executeRepayment_partialAmount_throwsValidation() {
        RepaymentPlan plan = plan(10L);
        when(repaymentPlanMapper.selectOne(any())).thenReturn(plan);
        when(repaymentRecordMapper.selectOne(any())).thenReturn(record(BigDecimal.valueOf(1000)));

        RepaymentExecuteRequest req = request();
        req.setAmount(BigDecimal.valueOf(500)); // 部分还款，不合法
        assertThrows(RuntimeException.class, () -> service.executeRepayment(10L, req));
        verify(repaymentRecordMapper, never()).updateById(any());
    }

    @Test
    void executeRepayment_alreadyCompleted_throwsIdempotencyGuard() {
        RepaymentPlan plan = plan(10L);
        when(repaymentPlanMapper.selectOne(any())).thenReturn(plan);

        RepaymentRecord completed = record(BigDecimal.valueOf(1000));
        completed.setStatus("COMPLETED");
        when(repaymentRecordMapper.selectOne(any())).thenReturn(completed);

        assertThrows(RuntimeException.class, () -> service.executeRepayment(10L, request()));
        verify(repaymentRecordMapper, never()).updateById(any());
    }

    @Test
    void executeRepayment_zeroOrNullAmount_rejected() {
        RepaymentPlan plan = plan(10L);
        when(repaymentPlanMapper.selectOne(any())).thenReturn(plan);
        when(repaymentRecordMapper.selectOne(any())).thenReturn(record(BigDecimal.valueOf(1000)));

        RepaymentExecuteRequest req = request();
        req.setAmount(BigDecimal.ZERO);
        assertThrows(RuntimeException.class, () -> service.executeRepayment(10L, req));
        verify(repaymentRecordMapper, never()).updateById(any());
    }

    @Test
    void executeRepayment_fullAmountAdvancesPlan() {
        RepaymentPlan plan = plan(10L);
        when(repaymentPlanMapper.selectOne(any())).thenReturn(plan);
        when(repaymentRecordMapper.selectOne(any())).thenReturn(record(BigDecimal.valueOf(1000)));

        RepaymentResponse resp = service.executeRepayment(10L, request());

        assertEquals("COMPLETED", resp.getStatus());
        verify(repaymentRecordMapper).updateById(any());

        ArgumentCaptor<RepaymentPlan> planCaptor = ArgumentCaptor.forClass(RepaymentPlan.class);
        verify(repaymentPlanMapper).updateById(planCaptor.capture());
        assertEquals(BigDecimal.valueOf(11000), planCaptor.getValue().getRemainingAmount());
        assertEquals(2, planCaptor.getValue().getCurrentPeriod());
    }
}