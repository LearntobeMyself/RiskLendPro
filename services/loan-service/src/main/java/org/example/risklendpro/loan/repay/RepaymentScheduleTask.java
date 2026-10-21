package org.example.risklendpro.loan.repay;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.api.dto.UserSummary;
import org.example.risklendpro.common.mail.EmailUtil;
import org.example.risklendpro.loan.borrow.LoanStatusEnum;
import org.example.risklendpro.loan.client.RiskServiceClient;
import org.example.risklendpro.loan.client.UserServiceClient;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.springframework.beans.factory.annotation.Autowired;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Component
public class RepaymentScheduleTask {

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private LoanMapper loanMapper;

    @Autowired
    private EmailUtil emailUtil;

    @Autowired
    private RiskServiceClient riskServiceClient;

    @Autowired
    private UserServiceClient userServiceClient;

    private static final Logger log = LoggerFactory.getLogger(RepaymentScheduleTask.class);

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Scheduled(cron = "0 0 1 * * ?")
    @SchedulerLock(name = "repaymentDailyCheck", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10S")
    public void dailyCheckAndUpdate() {
        Date today = new Date();

        QueryWrapper<RepaymentPlan> planQuery = new QueryWrapper<>();
        planQuery.eq("status", "ACTIVE");
        List<RepaymentPlan> activePlans = repaymentPlanMapper.selectList(planQuery);

        for (RepaymentPlan plan : activePlans) {
            try {
                checkAndUpdateActivePlan(plan, today);
            } catch (Exception e) {
                // 单个计划处理失败不影响其它计划，也避免邮件/Feign 异常中断整批任务
                log.warn("处理还款计划[{}]失败，跳过继续: {}", plan.getPlanId(), e.getMessage());
            }
        }

        QueryWrapper<RepaymentPlan> overdueQuery = new QueryWrapper<>();
        overdueQuery.eq("status", "OVERDUE");
        List<RepaymentPlan> overduePlans = repaymentPlanMapper.selectList(overdueQuery);

        for (RepaymentPlan plan : overduePlans) {
            try {
                updateOverduePlan(plan, today);
            } catch (Exception e) {
                log.warn("更新逾期计划[{}]失败，跳过继续: {}", plan.getPlanId(), e.getMessage());
            }
        }
    }

    private void checkAndUpdateActivePlan(RepaymentPlan plan, Date today) {
        Integer currentPeriod = plan.getCurrentPeriod();

        QueryWrapper<RepaymentRecord> recordQuery = new QueryWrapper<>();
        recordQuery.eq("plan_id", plan.getPlanId());
        recordQuery.eq("period", currentPeriod);
        RepaymentRecord currentRecord = repaymentRecordMapper.selectOne(recordQuery);

        if (currentRecord == null) {
            return;
        }

        if (isRepaid(currentRecord)) {
            advancePlanPeriodIfNeeded(plan, currentPeriod, today);
            return;
        }

        Date dueDate = currentRecord.getDueDate();
        if (dueDate == null) {
            return;
        }

        UserSummary user = userServiceClient.getUser(plan.getUserId());
        if (user == null) {
            return;
        }

        long diffDays = daysSinceDue(dueDate, today);

        if (diffDays == -3) {
            sendReminderEmail(user, currentRecord, dueDate);
        } else if (diffDays == 0) {
            sendDueTodayEmail(user, currentRecord);
        } else if (diffDays >= 1) {
            handleOverdue(plan, currentRecord, user, (int) diffDays, diffDays == 1);
        }
    }

    private void updateOverduePlan(RepaymentPlan plan, Date today) {
        Integer currentPeriod = plan.getCurrentPeriod();

        QueryWrapper<RepaymentRecord> recordQuery = new QueryWrapper<>();
        recordQuery.eq("plan_id", plan.getPlanId());
        recordQuery.eq("period", currentPeriod);
        RepaymentRecord currentRecord = repaymentRecordMapper.selectOne(recordQuery);

        if (currentRecord == null) {
            return;
        }

        if (isRepaid(currentRecord)) {
            advancePlanPeriodIfNeeded(plan, currentPeriod, today);
            return;
        }

        Date dueDate = currentRecord.getDueDate();
        if (dueDate == null) {
            return;
        }

        long diffDays = daysSinceDue(dueDate, today);

        if (diffDays >= 1) {
            String oldLevel = plan.getOverdueLevel();
            updateOverdueLevel(plan, (int) diffDays);
            markLoanOverdue(plan.getLoanId());
            riskServiceClient.recalculateBehaviorScore(plan.getUserId());

            // 逾期等级升级（M1→M2→M3→M4）时再次发送催收邮件，实现持续催收
            String newLevel = plan.getOverdueLevel();
            if (newLevel != null && !"N".equals(newLevel) && !Objects.equals(oldLevel, newLevel)) {
                UserSummary user = userServiceClient.getUser(plan.getUserId());
                if (user != null) {
                    emailUtil.sendOverdueNotification(
                            user.email(),
                            user.realName(),
                            currentRecord.getPeriod(),
                            (int) diffDays,
                            currentRecord.getAmount().toString()
                    );
                }
            }
        }
    }

    private void advancePlanPeriodIfNeeded(RepaymentPlan plan, int currentPeriod, Date today) {
        Integer totalPeriods = plan.getTotalPeriods();
        if (totalPeriods == null || currentPeriod >= totalPeriods) {
            return;
        }
        plan.setCurrentPeriod(currentPeriod + 1);
        plan.setUpdateTime(new Date());
        repaymentPlanMapper.updateById(plan);
        checkAndUpdateActivePlan(plan, today);
    }

    private static boolean isRepaid(RepaymentRecord record) {
        String status = record.getStatus();
        return "COMPLETED".equals(status) || "PAID".equals(status) || "SETTLED".equals(status);
    }

    /**
     * 按自然日计算今天相对到期日的天数差（忽略时分秒）：
     * 负数表示距到期还有几天（如 -3 即提前3天），0 表示到期当天，正数表示已逾期天数。
     */
    private static long daysSinceDue(Date dueDate, Date today) {
        LocalDate due = dueDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate now = today.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return ChronoUnit.DAYS.between(due, now);
    }

    private void sendReminderEmail(UserSummary user, RepaymentRecord record, Date dueDate) {
        emailUtil.sendRepaymentReminderNotification(
                user.email(),
                user.realName(),
                record.getPeriod(),
                record.getAmount().toString(),
                DATE_FORMAT.format(dueDate)
        );
    }

    private void sendDueTodayEmail(UserSummary user, RepaymentRecord record) {
        emailUtil.sendRepaymentDueTodayNotification(
                user.email(),
                user.realName(),
                record.getPeriod(),
                record.getAmount().toString()
        );
    }

    private void handleOverdue(RepaymentPlan plan, RepaymentRecord record, UserSummary user, int overdueDays, boolean isFirstOverdue) {
        if (!"OVERDUE".equals(record.getStatus())) {
            record.setStatus("OVERDUE");
            repaymentRecordMapper.updateById(record);
        }

        plan.setStatus("OVERDUE");
        plan.setOverdueDays(overdueDays);
        plan.setOverdueLevel(calculateOverdueLevel(overdueDays));
        plan.setUpdateTime(new Date());
        repaymentPlanMapper.updateById(plan);
        markLoanOverdue(plan.getLoanId());

        updateUserCreditLimit(plan.getUserId(), record.getAmount(), true);

        riskServiceClient.recalculateBehaviorScore(plan.getUserId());

        if (isFirstOverdue) {
            emailUtil.sendOverdueNotification(
                    user.email(),
                    user.realName(),
                    record.getPeriod(),
                    overdueDays,
                    record.getAmount().toString()
            );
        }
    }

    private void updateOverdueLevel(RepaymentPlan plan, int overdueDays) {
        boolean needsUpdate = false;

        if (plan.getOverdueDays() == null || plan.getOverdueDays() != overdueDays) {
            plan.setOverdueDays(overdueDays);
            needsUpdate = true;
        }

        String newLevel = calculateOverdueLevel(overdueDays);
        if (newLevel != null && !newLevel.equals(plan.getOverdueLevel())) {
            plan.setOverdueLevel(newLevel);
            needsUpdate = true;
        }

        if (needsUpdate) {
            plan.setUpdateTime(new Date());
            repaymentPlanMapper.updateById(plan);
        }
    }

    private String calculateOverdueLevel(int overdueDays) {
        if (overdueDays <= 0) {
            return "N";
        } else if (overdueDays <= 30) {
            return "M1";
        } else if (overdueDays <= 60) {
            return "M2";
        } else if (overdueDays <= 90) {
            return "M3";
        } else {
            return "M4";
        }
    }

    private void markLoanOverdue(Long loanId) {
        if (loanId == null) {
            return;
        }
        Loan loan = loanMapper.selectById(loanId);
        if (loan == null) {
            return;
        }
        String status = loan.getStatus();
        if (LoanStatusEnum.OVERDUE.getCode().equals(status)
                || LoanStatusEnum.REPAID.getCode().equals(status)
                || LoanStatusEnum.REJECTED.getCode().equals(status)) {
            return;
        }
        loan.setStatus(LoanStatusEnum.OVERDUE.getCode());
        loan.setUpdateTime(new Date());
        loanMapper.updateById(loan);
    }

    private void updateUserCreditLimit(Long userId, java.math.BigDecimal overdueAmount, boolean hasOverdue) {
        UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId).last("FOR UPDATE")
        );

        if (creditLimit != null) {
            if (overdueAmount != null && overdueAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
                creditLimit.setOverdueAmount(creditLimit.getOverdueAmount().add(overdueAmount));
            }
            creditLimit.setHasOverdue(hasOverdue);
            creditLimit.setLastUpdateTime(new Date());
            userCreditLimitMapper.updateById(creditLimit);
        }
    }
}
