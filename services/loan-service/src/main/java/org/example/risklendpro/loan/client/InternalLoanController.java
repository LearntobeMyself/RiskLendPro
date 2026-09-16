package org.example.risklendpro.loan.client;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.api.contract.LoanApi;
import org.example.risklendpro.api.dto.BCardRepaymentSnapshot;
import org.example.risklendpro.api.dto.CreditBehaviorUpsertCommand;
import org.example.risklendpro.api.dto.CreditLimitGrantCommand;
import org.example.risklendpro.api.dto.CreditLimitSnapshot;
import org.example.risklendpro.api.dto.LoanBehaviorSnapshot;
import org.example.risklendpro.api.dto.LoanUserSummaryItem;
import org.example.risklendpro.loan.borrow.LoanStatusEnum;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * loan-service 对外契约实现（授信/额度域 + 贷款查询域），供 user/risk 通过 Feign 消费。
 */
@RestController
public class InternalLoanController implements LoanApi {

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private LoanMapper loanMapper;

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    @Override
    public CreditLimitSnapshot getCreditLimit(Long userId) {
        UserCreditLimit limit = findLimit(userId);
        if (limit == null) {
            return null;
        }
        return toSnapshot(limit);
    }

    @Override
    public CreditLimitSnapshot ensureCreditLimit(Long userId) {
        UserCreditLimit limit = findLimit(userId);
        if (limit == null) {
            limit = new UserCreditLimit();
            limit.setUserId(userId);
            limit.setTotalLimit(BigDecimal.ZERO);
            limit.setUsedLimit(BigDecimal.ZERO);
            limit.setRemainingLimit(BigDecimal.ZERO);
            limit.setOverdueAmount(BigDecimal.ZERO);
            limit.setHasOverdue(false);
            limit.setBCardEnabled(false);
            limit.setLastUpdateTime(new Date());
            userCreditLimitMapper.insert(limit);
        }
        return toSnapshot(limit);
    }

    @Override
    public List<CreditLimitSnapshot> listBCardLimits() {
        return userCreditLimitMapper.selectList(
                new QueryWrapper<UserCreditLimit>().eq("b_card_enabled", true)
        ).stream().map(this::toSnapshot).toList();
    }

    @Override
    public CreditLimitSnapshot grantCreditLimit(CreditLimitGrantCommand command) {
        UserCreditLimit limit = findLimit(command.userId());
        if (limit == null) {
            limit = new UserCreditLimit();
            limit.setUserId(command.userId());
            limit.setTotalLimit(command.approvedLimit());
            limit.setUsedLimit(BigDecimal.ZERO);
            limit.setRemainingLimit(command.approvedLimit());
            limit.setOverdueAmount(BigDecimal.ZERO);
            limit.setHasOverdue(false);
            limit.setBCardEnabled(false);
            limit.setLastUpdateTime(new Date());
            userCreditLimitMapper.insert(limit);
        } else {
            limit.setTotalLimit(command.approvedLimit());
            limit.setRemainingLimit(command.approvedLimit().subtract(limit.getUsedLimit()));
            limit.setLastUpdateTime(new Date());
            userCreditLimitMapper.updateById(limit);
        }
        return toSnapshot(limit);
    }

    @Override
    public CreditLimitSnapshot upsertBehaviorScore(CreditBehaviorUpsertCommand command) {
        UserCreditLimit limit = findLimit(command.userId());
        if (limit == null) {
            return null;
        }
        if (command.bCardEnabled() != null) {
            limit.setBCardEnabled(command.bCardEnabled());
        }
        if (command.behaviorScore() != null) {
            limit.setBScore(command.behaviorScore());
            limit.setBScoreUpdatedAt(new Date());
        }
        limit.setLastUpdateTime(new Date());
        userCreditLimitMapper.updateById(limit);
        return toSnapshot(limit);
    }

    @Override
    public LoanBehaviorSnapshot getLoanBehavior(Long userId) {
        List<Loan> loans = loanMapper.selectList(
                new QueryWrapper<Loan>().eq("user_id", userId));
        List<Long> loanIds = loans.stream().map(Loan::getLoanId).collect(Collectors.toList());

        long activeLoanCount = loans.stream()
                .filter(l -> LoanStatusEnum.DISBURRSED.getCode().equals(l.getStatus())
                        || LoanStatusEnum.OVERDUE.getCode().equals(l.getStatus()))
                .count();

        BigDecimal outstandingAmount = loans.stream()
                .filter(l -> LoanStatusEnum.DISBURRSED.getCode().equals(l.getStatus())
                        || LoanStatusEnum.OVERDUE.getCode().equals(l.getStatus()))
                .map(l -> l.getAmount() == null ? BigDecimal.ZERO : l.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<RepaymentPlan> plans = loanIds.isEmpty() ? Collections.emptyList()
                : repaymentPlanMapper.selectList(new QueryWrapper<RepaymentPlan>().in("loan_id", loanIds));

        Date now = new Date();
        int maxOverdueDays = 0;
        int overduePeriodCount = 0;
        long overdueRecordCount = 0;
        for (RepaymentPlan plan : plans) {
            if ("COMPLETED".equals(plan.getStatus())) {
                continue;
            }
            List<RepaymentRecord> planRecords = repaymentRecordMapper.selectList(
                    new QueryWrapper<RepaymentRecord>()
                            .eq("plan_id", plan.getPlanId())
                            .orderByAsc("period"));
            boolean planOverdue = false;
            for (RepaymentRecord record : planRecords) {
                if (isRepaid(record) || record.getDueDate() == null) {
                    continue;
                }
                if (record.getDueDate().before(now)) {
                    overdueRecordCount++;
                    maxOverdueDays = Math.max(maxOverdueDays, daysPastDue(record.getDueDate(), now));
                    planOverdue = true;
                }
            }
            if (!planOverdue && "OVERDUE".equals(plan.getStatus())) {
                planOverdue = true;
                if (plan.getOverdueDays() != null) {
                    maxOverdueDays = Math.max(maxOverdueDays, plan.getOverdueDays());
                }
            }
            if (planOverdue) {
                overduePeriodCount++;
            }
        }

        List<RepaymentRecord> records;
        if (loanIds.isEmpty()) {
            records = Collections.emptyList();
        } else {
            records = repaymentRecordMapper.selectList(
                    new QueryWrapper<RepaymentRecord>().in("loan_id", loanIds));
        }
        double onTimeRate = 1.0;
        if (!records.isEmpty()) {
            int paid = 0;
            int onTime = 0;
            for (RepaymentRecord r : records) {
                if (isRepaid(r)) {
                    paid++;
                    if (r.getRepaymentDate() != null && r.getDueDate() != null
                            && !r.getRepaymentDate().after(r.getDueDate())) {
                        onTime++;
                    }
                } else if (r.getDueDate() != null && r.getDueDate().before(now)) {
                    paid++;
                }
            }
            onTimeRate = paid == 0 ? 1.0 : (double) onTime / (double) paid;
        }

        return new LoanBehaviorSnapshot(
                userId,
                (int) activeLoanCount,
                (int) overdueRecordCount,
                outstandingAmount,
                maxOverdueDays,
                overduePeriodCount,
                onTimeRate
        );
    }

    @Override
    public LoanUserSummaryItem getUserLoanSummary(Long userId) {
        List<Loan> loans = loanMapper.selectList(new QueryWrapper<Loan>()
                .eq("user_id", userId)
                .in("status", LoanStatusEnum.DISBURRSED.getCode(), LoanStatusEnum.REPAID.getCode(),
                        LoanStatusEnum.OVERDUE.getCode()));
        BigDecimal totalAmount = loans.stream()
                .map(l -> l.getAmount() == null ? BigDecimal.ZERO : l.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long activeCount = loans.stream()
                .filter(l -> LoanStatusEnum.DISBURRSED.getCode().equals(l.getStatus())
                        || LoanStatusEnum.OVERDUE.getCode().equals(l.getStatus()))
                .count();
        return new LoanUserSummaryItem(userId, loans.size(), totalAmount, (int) activeCount);
    }

    @Override
    public BCardRepaymentSnapshot getBCardRepaymentMonitor(Long userId) {
        List<RepaymentPlan> plans = repaymentPlanMapper.selectList(
                new QueryWrapper<RepaymentPlan>().eq("user_id", userId));
        LocalDate today = LocalDate.now();
        int activePlanCount = 0;
        RepaymentPlan bestPlan = null;
        RepaymentRecord bestRecord = null;
        Integer daysToDue = null;
        int bestPriority = Integer.MAX_VALUE;
        for (RepaymentPlan plan : plans) {
            if ("COMPLETED".equals(plan.getStatus())) {
                continue;
            }
            activePlanCount++;
            RepaymentRecord record = resolveFocusRecord(plan.getPlanId(), plan.getCurrentPeriod());
            if (record == null || record.getDueDate() == null) {
                continue;
            }
            int d = (int) ChronoUnit.DAYS.between(today, toLocalDate(record.getDueDate()));
            int priority = planUrgencyPriority(plan, record, d);
            if (bestPlan == null
                    || priority < bestPriority
                    || (priority == bestPriority && (daysToDue == null || d < daysToDue))) {
                bestPriority = priority;
                daysToDue = d;
                bestPlan = plan;
                bestRecord = record;
            }
        }
        return new BCardRepaymentSnapshot(
                userId,
                activePlanCount,
                bestPlan != null ? bestPlan.getPlanId() : null,
                bestPlan != null ? bestPlan.getStatus() : null,
                bestPlan != null ? bestPlan.getOverdueLevel() : null,
                bestPlan != null ? bestPlan.getOverdueDays() : null,
                bestRecord != null && bestRecord.getDueDate() != null ? bestRecord.getDueDate().getTime() : null,
                bestRecord != null ? bestRecord.getPeriod() : null,
                bestRecord != null ? bestRecord.getStatus() : null,
                daysToDue
        );
    }

    /** 当期已还清时推进到首个未还期次，避免误报逾期。 */
    private RepaymentRecord resolveFocusRecord(Long planId, Integer currentPeriod) {
        if (planId == null || currentPeriod == null) {
            return null;
        }
        RepaymentRecord current = repaymentRecordMapper.selectOne(
                new QueryWrapper<RepaymentRecord>()
                        .eq("plan_id", planId)
                        .eq("period", currentPeriod));
        if (current != null && !isRepaidRecord(current)) {
            return current;
        }
        List<RepaymentRecord> records = repaymentRecordMapper.selectList(
                new QueryWrapper<RepaymentRecord>()
                        .eq("plan_id", planId)
                        .orderByAsc("period"));
        for (RepaymentRecord record : records) {
            if (!isRepaidRecord(record)) {
                return record;
            }
        }
        return current;
    }

    private static boolean isRepaidRecord(RepaymentRecord record) {
        String status = record.getStatus();
        return "COMPLETED".equals(status) || "PAID".equals(status) || "SETTLED".equals(status);
    }

    /** 还款计划紧迫度：数值越小越优先展示。 */
    private static int planUrgencyPriority(RepaymentPlan plan, RepaymentRecord record, int daysToDue) {
        if (plan != null && "OVERDUE".equals(plan.getStatus())) {
            return 0;
        }
        if (record != null && "OVERDUE".equals(record.getStatus())) {
            return 0;
        }
        if (daysToDue < 0) {
            return 0;
        }
        if (daysToDue == 0) {
            return 1;
        }
        if (daysToDue <= 3) {
            return 2;
        }
        return 3;
    }

    private static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private UserCreditLimit findLimit(Long userId) {
        return userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId));
    }

    private CreditLimitSnapshot toSnapshot(UserCreditLimit limit) {
        return new CreditLimitSnapshot(
                limit.getUserId(),
                limit.getTotalLimit(),
                limit.getUsedLimit(),
                limit.getRemainingLimit(),
                limit.getOverdueAmount(),
                Boolean.TRUE.equals(limit.getHasOverdue()),
                limit.getBScore(),
                limit.getBScoreUpdatedAt() == null ? null : limit.getBScoreUpdatedAt().getTime(),
                Boolean.TRUE.equals(limit.getBCardEnabled()),
                limit.getLastUpdateTime() == null ? null : limit.getLastUpdateTime().getTime()
        );
    }

    private static boolean isRepaid(RepaymentRecord record) {
        String status = record.getStatus();
        return "COMPLETED".equals(status) || "PAID".equals(status) || "SETTLED".equals(status);
    }

    private static int daysPastDue(Date dueDate, Date now) {
        LocalDate due = dueDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return (int) ChronoUnit.DAYS.between(due, today);
    }
}
