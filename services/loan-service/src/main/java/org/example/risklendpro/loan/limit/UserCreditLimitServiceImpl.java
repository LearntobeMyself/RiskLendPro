package org.example.risklendpro.loan.limit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.api.dto.UserSummary;
import org.example.risklendpro.common.mail.EmailUtil;
import org.example.risklendpro.loan.borrow.LoanStatusEnum;
import org.example.risklendpro.loan.client.RiskServiceClient;
import org.example.risklendpro.loan.client.UserServiceClient;
import org.example.risklendpro.loan.entity.LimitAdjustLog;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.LimitAdjustLogMapper;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.loan.limit.LimitAdjustRequest;
import org.example.risklendpro.loan.limit.LimitAdjustResponse;
import org.example.risklendpro.loan.limit.UserCreditLimitResponse;
import org.example.risklendpro.loan.limit.UserCreditLimitService;
import org.example.risklendpro.loan.repay.RepaymentCalculator;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
public class UserCreditLimitServiceImpl implements UserCreditLimitService {

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private LimitAdjustLogMapper limitAdjustLogMapper;

    @Autowired
    private LoanMapper loanMapper;

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    @Autowired
    private EmailUtil emailUtil;

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private RiskServiceClient riskServiceClient;

    @Override
    public UserCreditLimitResponse getUserCreditLimit(Long userId) {
        QueryWrapper<UserCreditLimit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(queryWrapper);

        if (creditLimit == null) {
            throw new RuntimeException("用户额度信息不存在");
        }

        UserCreditLimitResponse response = new UserCreditLimitResponse();
        response.setUserId(creditLimit.getUserId());
        response.setTotalLimit(creditLimit.getTotalLimit());
        response.setUsedLimit(creditLimit.getUsedLimit());
        response.setRemainingLimit(creditLimit.getRemainingLimit());
        response.setOverdueAmount(creditLimit.getOverdueAmount());
        response.setHasOverdue(creditLimit.getHasOverdue());
        response.setLastUpdateTime(creditLimit.getLastUpdateTime());

        return response;
    }

    @Override
    @Transactional
    public LimitAdjustResponse adjustLimit(LimitAdjustRequest request, Long operatorId) {
        QueryWrapper<UserCreditLimit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", request.getUserId());
        UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(queryWrapper);

        if (creditLimit == null) {
            throw new RuntimeException("用户额度信息不存在");
        }

        BigDecimal oldLimit = creditLimit.getTotalLimit();
        BigDecimal newLimit = request.getNewLimit();
        // 已用额度代表真实在贷余额，调额不能把它抹掉；总额度必须始终 >= 已用额度。
        BigDecimal oldUsedLimit = creditLimit.getUsedLimit();
        if (oldUsedLimit == null || oldUsedLimit.compareTo(BigDecimal.ZERO) < 0) {
            oldUsedLimit = BigDecimal.ZERO;
        }

        if (newLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("新额度不能为负数");
        }
        if (newLimit.compareTo(oldUsedLimit) < 0) {
            throw new RuntimeException("新额度不能小于已用额度（在贷余额 " + oldUsedLimit + "），请先等待用户还款");
        }

        BigDecimal newRemainingLimit = newLimit.subtract(oldUsedLimit);

        creditLimit.setTotalLimit(newLimit);
        creditLimit.setRemainingLimit(newRemainingLimit);
        creditLimit.setUsedLimit(oldUsedLimit);
        creditLimit.setLastUpdateTime(new Date());
        userCreditLimitMapper.updateById(creditLimit);

        LimitAdjustLog log = new LimitAdjustLog();
        log.setUserId(request.getUserId());
        log.setOldLimit(oldLimit);
        log.setNewLimit(newLimit);
        log.setReason(request.getReason());
        log.setOperatorId(operatorId);
        log.setAdjustTime(new Date());
        limitAdjustLogMapper.insert(log);

        LimitAdjustResponse response = new LimitAdjustResponse();
        response.setUserId(request.getUserId());
        response.setOldLimit(oldLimit);
        response.setNewLimit(newLimit);
        response.setAdjustTime(new Date());

        // 额度上调后，自动处理原本因额度不足而挂起的申请
        autoDisbursePendingLoans(request.getUserId(), creditLimit);

        return response;
    }

    private void autoDisbursePendingLoans(Long userId, UserCreditLimit creditLimit) {
        List<Loan> pendingLoans = loanMapper.selectList(
                new QueryWrapper<Loan>()
                        .eq("user_id", userId)
                        .eq("status", LoanStatusEnum.PENDING_APPROVAL.getCode())
                        .orderByAsc("apply_time")
        );
        if (pendingLoans == null || pendingLoans.isEmpty()) {
            return;
        }

        UserSummary user = userServiceClient.getUser(userId);
        for (Loan loan : pendingLoans) {
            BigDecimal remainingLimit = creditLimit.getRemainingLimit();
            if (remainingLimit == null || remainingLimit.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (loan.getAmount().compareTo(remainingLimit) > 0) {
                continue;
            }

            // 自动放款：与借款请求时的额度内处理逻辑保持一致
            loan.setStatus(LoanStatusEnum.DISBURSED.getCode());
            loan.setApproveTime(new Date());
            loan.setDisbursementTime(new Date());
            loan.setAutoApproved(true);
            loan.setUpdateTime(new Date());
            loanMapper.updateById(loan);

            deductCreditLimit(creditLimit, loan.getAmount());
            generateRepaymentPlan(loan);

            if (user != null && user.idCard() != null) {
                riskServiceClient.activateBehaviorScore(userId, user.idCard());
            }
            if (user != null && user.email() != null) {
                emailUtil.sendLoanSuccessNotification(
                        user.email(),
                        user.realName(),
                        loan.getAmount().toString()
                );
            }
        }
        creditLimit.setLastUpdateTime(new Date());
        userCreditLimitMapper.updateById(creditLimit);
    }

    private void deductCreditLimit(UserCreditLimit creditLimit, BigDecimal amount) {
        BigDecimal newUsedLimit = creditLimit.getUsedLimit().add(amount);
        BigDecimal newRemainingLimit = creditLimit.getTotalLimit().subtract(newUsedLimit);
        creditLimit.setUsedLimit(newUsedLimit);
        creditLimit.setRemainingLimit(newRemainingLimit);
    }

    private void generateRepaymentPlan(Loan loan) {
        List<RepaymentCalculator.RepaymentDetail> details;
        switch (loan.getRepaymentMethod()) {
            case "等额本息":
                details = RepaymentCalculator.calculateEqualPrincipalAndInterest(
                        loan.getAmount(), loan.getInterestRate(), loan.getTermMonths());
                break;
            case "等额本金":
                details = RepaymentCalculator.calculateEqualPrincipal(
                        loan.getAmount(), loan.getInterestRate(), loan.getTermMonths());
                break;
            case "先息后本":
                details = RepaymentCalculator.calculateInterestFirst(
                        loan.getAmount(), loan.getInterestRate(), loan.getTermMonths());
                break;
            default:
                throw new RuntimeException("不支持的还款方式: " + loan.getRepaymentMethod());
        }

        BigDecimal totalRepayable = BigDecimal.ZERO;
        for (RepaymentCalculator.RepaymentDetail detail : details) {
            totalRepayable = totalRepayable.add(detail.getAmount());
        }

        RepaymentPlan plan = new RepaymentPlan();
        plan.setLoanId(loan.getLoanId());
        plan.setUserId(loan.getUserId());
        plan.setTotalAmount(totalRepayable);
        plan.setPaidAmount(BigDecimal.ZERO);
        plan.setRemainingAmount(totalRepayable);
        plan.setTotalPeriods(loan.getTermMonths());
        plan.setCurrentPeriod(1);
        plan.setStatus("ACTIVE");
        plan.setCreateTime(new Date());
        plan.setUpdateTime(new Date());
        repaymentPlanMapper.insert(plan);

        Date now = new Date();
        for (RepaymentCalculator.RepaymentDetail detail : details) {
            RepaymentRecord record = new RepaymentRecord();
            record.setPlanId(plan.getPlanId());
            record.setLoanId(loan.getLoanId());
            record.setPeriod(detail.getPeriod());
            record.setPrincipal(detail.getPrincipal());
            record.setInterest(detail.getInterest());
            record.setAmount(detail.getAmount());
            record.setActualAmount(BigDecimal.ZERO);

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(now);
            calendar.add(Calendar.MONTH, detail.getPeriod());
            record.setDueDate(calendar.getTime());

            record.setStatus("PENDING");
            record.setCreateTime(now);
            repaymentRecordMapper.insert(record);
        }
    }
}
