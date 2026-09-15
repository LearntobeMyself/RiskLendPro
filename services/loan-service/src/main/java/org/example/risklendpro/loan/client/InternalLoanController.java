package org.example.risklendpro.loan.client;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.api.contract.LoanApi;
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
        List<Loan> loans = loanMapper.selectList(
                new QueryWrapper<Loan>().eq("user_id", userId));
        BigDecimal totalAmount = loans.stream()
                .map(l -> l.getAmount() == null ? BigDecimal.ZERO : l.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long activeCount = loans.stream()
                .filter(l -> LoanStatusEnum.DISBURRSED.getCode().equals(l.getStatus())
                        || LoanStatusEnum.OVERDUE.getCode().equals(l.getStatus()))
                .count();
        return new LoanUserSummaryItem(userId, loans.size(), totalAmount, (int) activeCount);
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
