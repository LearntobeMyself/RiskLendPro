package org.example.risklendpro.loan.repay;

import org.example.risklendpro.api.dto.UserSummary;
import org.example.risklendpro.common.mail.EmailUtil;
import org.example.risklendpro.loan.client.RiskServiceClient;
import org.example.risklendpro.loan.client.UserServiceClient;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 贷后催收定时任务回归测试：
 * 提前3天提醒（-3）、到期当天提醒（0）、首逾落库（>=1）、
 * 用户服务异常时逾期状态机照常推进仅跳过邮件。
 */
@ExtendWith(MockitoExtension.class)
class RepaymentScheduleTaskTest {

    @InjectMocks
    private RepaymentScheduleTask task;

    @Mock
    private RepaymentPlanMapper repaymentPlanMapper;

    @Mock
    private RepaymentRecordMapper repaymentRecordMapper;

    @Mock
    private LoanMapper loanMapper;

    @Mock
    private OverdueStateSync overdueStateSync;

    @Mock
    private EmailUtil emailUtil;

    @Mock
    private RiskServiceClient riskServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    private static final Long USER_ID = 10L;

    @Test
    void reminderEmailSentThreeDaysBeforeDue() {
        RepaymentPlan plan = activePlan();
        RepaymentRecord record = pendingRecord(dueDatePlus(3));
        prepareActiveScan(plan, record);
        when(userServiceClient.getUser(USER_ID)).thenReturn(userSummary());

        task.dailyCheckAndUpdate();

        verify(emailUtil).sendRepaymentReminderNotification(
                eq("u@test.com"), eq("张三"), eq(1), eq("1000"), anyString());
        verify(emailUtil, never()).sendRepaymentDueTodayNotification(
                anyString(), anyString(), anyInt(), anyString());
        verify(repaymentRecordMapper, never()).updateById(any(RepaymentRecord.class));
    }

    @Test
    void dueTodayEmailSentOnDueDate() {
        RepaymentPlan plan = activePlan();
        RepaymentRecord record = pendingRecord(dueDatePlus(0));
        prepareActiveScan(plan, record);
        when(userServiceClient.getUser(USER_ID)).thenReturn(userSummary());

        task.dailyCheckAndUpdate();

        verify(emailUtil).sendRepaymentDueTodayNotification(
                eq("u@test.com"), eq("张三"), eq(1), eq("1000"));
        verify(emailUtil, never()).sendRepaymentReminderNotification(
                anyString(), anyString(), anyInt(), anyString(), anyString());
    }

    @Test
    void firstOverdueMarksRecordPlanLoanAndSendsEmail() {
        RepaymentPlan plan = activePlan();
        RepaymentRecord record = pendingRecord(dueDatePlus(-1));
        prepareActiveScan(plan, record);
        when(userServiceClient.getUser(USER_ID)).thenReturn(userSummary());
        Loan loan = new Loan();
        loan.setLoanId(5L);
        loan.setStatus("DISBURSED");
        when(loanMapper.selectById(5L)).thenReturn(loan);

        task.dailyCheckAndUpdate();

        // 记录 / 计划 / 借据全部转逾期
        org.junit.jupiter.api.Assertions.assertEquals("OVERDUE", record.getStatus());
        verify(repaymentRecordMapper).updateById(record);
        org.junit.jupiter.api.Assertions.assertEquals("OVERDUE", plan.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(1, plan.getOverdueDays());
        org.junit.jupiter.api.Assertions.assertEquals("M1", plan.getOverdueLevel());
        verify(repaymentPlanMapper).updateById(plan);
        org.junit.jupiter.api.Assertions.assertEquals("OVERDUE", loan.getStatus());
        verify(loanMapper).updateById(loan);
        verify(overdueStateSync).syncUserOverdueState(USER_ID);
        verify(emailUtil).sendOverdueNotification(
                eq("u@test.com"), eq("张三"), eq(1), eq(1), eq("1000"));
    }

    @Test
    void overduePersistedEvenWhenUserUnavailable() {
        RepaymentPlan plan = activePlan();
        RepaymentRecord record = pendingRecord(dueDatePlus(-1));
        prepareActiveScan(plan, record);
        // 用户服务异常/无数据：逾期状态机必须照常落库，仅跳过邮件
        when(userServiceClient.getUser(USER_ID)).thenReturn(null);
        when(loanMapper.selectById(5L)).thenReturn(null);

        task.dailyCheckAndUpdate();

        org.junit.jupiter.api.Assertions.assertEquals("OVERDUE", record.getStatus());
        verify(repaymentRecordMapper).updateById(record);
        org.junit.jupiter.api.Assertions.assertEquals("OVERDUE", plan.getStatus());
        verify(repaymentPlanMapper).updateById(plan);
        verify(overdueStateSync).syncUserOverdueState(USER_ID);
        verify(emailUtil, never()).sendOverdueNotification(
                anyString(), anyString(), anyInt(), anyInt(), anyString());
    }

    private void prepareActiveScan(RepaymentPlan plan, RepaymentRecord record) {
        // 第一次 selectList 返回 ACTIVE 计划，第二次（OVERDUE 扫描）返回空
        when(repaymentPlanMapper.selectList(any())).thenReturn(List.of(plan), List.of());
        when(repaymentRecordMapper.selectOne(any())).thenReturn(record);
    }

    private RepaymentPlan activePlan() {
        RepaymentPlan plan = new RepaymentPlan();
        plan.setPlanId(1L);
        plan.setUserId(USER_ID);
        plan.setLoanId(5L);
        plan.setCurrentPeriod(1);
        plan.setTotalPeriods(12);
        plan.setStatus("ACTIVE");
        return plan;
    }

    private RepaymentRecord pendingRecord(Date dueDate) {
        RepaymentRecord record = new RepaymentRecord();
        record.setRecordId(100L);
        record.setPlanId(1L);
        record.setPeriod(1);
        record.setAmount(new BigDecimal("1000"));
        record.setStatus("PENDING");
        record.setDueDate(dueDate);
        return record;
    }

    private static Date dueDatePlus(int days) {
        return Date.from(LocalDate.now().plusDays(days)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static UserSummary userSummary() {
        return new UserSummary(USER_ID, "张三", "13800000000", "u@test.com",
                null, "ACTIVE", "COMPLETED", 0L);
    }
}
