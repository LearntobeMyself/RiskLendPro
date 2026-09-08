package org.example.risklendpro.loan.repay;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.common.mail.EmailUtil;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.user.entity.User;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.user.mapper.UserMapper;
import org.example.risklendpro.risk.score.BehaviorScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
public class RepaymentScheduleTask {

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private EmailUtil emailUtil;

    @Autowired
    private BehaviorScoreService behaviorScoreService;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void dailyCheckAndUpdate() {
        Date today = new Date();

        QueryWrapper<RepaymentPlan> planQuery = new QueryWrapper<>();
        planQuery.eq("status", "ACTIVE");
        List<RepaymentPlan> activePlans = repaymentPlanMapper.selectList(planQuery);

        for (RepaymentPlan plan : activePlans) {
            checkAndUpdateActivePlan(plan, today);
        }

        QueryWrapper<RepaymentPlan> overdueQuery = new QueryWrapper<>();
        overdueQuery.eq("status", "OVERDUE");
        List<RepaymentPlan> overduePlans = repaymentPlanMapper.selectList(overdueQuery);

        for (RepaymentPlan plan : overduePlans) {
            updateOverduePlan(plan, today);
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

        User user = userMapper.selectById(plan.getUserId());
        if (user == null) {
            return;
        }

        long diffMillis = today.getTime() - dueDate.getTime();
        long diffDays = diffMillis / (1000 * 60 * 60 * 24);

        if (diffDays == 3) {
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

        long diffMillis = today.getTime() - dueDate.getTime();
        long diffDays = diffMillis / (1000 * 60 * 60 * 24);

        if (diffDays >= 1) {
            updateOverdueLevel(plan, (int) diffDays);
            behaviorScoreService.recalculate(plan.getUserId());
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

    private void sendReminderEmail(User user, RepaymentRecord record, Date dueDate) {
        emailUtil.sendRepaymentReminderNotification(
                user.getEmail(),
                user.getRealName(),
                record.getPeriod(),
                record.getAmount().toString(),
                DATE_FORMAT.format(dueDate)
        );
    }

    private void sendDueTodayEmail(User user, RepaymentRecord record) {
        emailUtil.sendRepaymentDueTodayNotification(
                user.getEmail(),
                user.getRealName(),
                record.getPeriod(),
                record.getAmount().toString()
        );
    }

    private void handleOverdue(RepaymentPlan plan, RepaymentRecord record, User user, int overdueDays, boolean isFirstOverdue) {
        if (!"OVERDUE".equals(record.getStatus())) {
            record.setStatus("OVERDUE");
            repaymentRecordMapper.updateById(record);
        }

        plan.setStatus("OVERDUE");
        plan.setOverdueDays(overdueDays);
        plan.setOverdueLevel(calculateOverdueLevel(overdueDays));
        plan.setUpdateTime(new Date());
        repaymentPlanMapper.updateById(plan);

        updateUserCreditLimit(plan.getUserId(), record.getAmount(), true);

        behaviorScoreService.recalculate(plan.getUserId());

        if (isFirstOverdue) {
            emailUtil.sendOverdueNotification(
                    user.getEmail(),
                    user.getRealName(),
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

    private void updateUserCreditLimit(Long userId, java.math.BigDecimal overdueAmount, boolean hasOverdue) {
        UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId)
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
