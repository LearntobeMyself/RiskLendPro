package org.example.risklendpro.loan.borrow;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.api.dto.RiskAssessmentSummary;
import org.example.risklendpro.api.dto.UserSummary;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.borrow.LoanStatusEnum;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.loan.borrow.LoanRequest;
import org.example.risklendpro.loan.borrow.LoanResponse;
import org.example.risklendpro.loan.borrow.LoanService;
import org.example.risklendpro.loan.client.RiskServiceClient;
import org.example.risklendpro.loan.client.UserServiceClient;
import org.example.risklendpro.common.mail.EmailUtil;
import org.example.risklendpro.loan.repay.RepaymentCalculator;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LoanServiceImpl implements LoanService {

    @Autowired
    private LoanMapper loanMapper;

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    @Autowired
    private EmailUtil emailUtil;

    @Autowired
    private RiskServiceClient riskServiceClient;

    @Autowired
    private UserServiceClient userServiceClient;

    private final TransactionTemplate transactionTemplate;

    public LoanServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public LoanResponse requestLoan(Long userId, LoanRequest request) {
        // 1. 获取用户最新授信评估信息（Feign 读取，放到事务外，避免持有 DB 连接）
        RiskAssessmentSummary latestAssessment = riskServiceClient.getLatestFinalAssessment(userId);

        if (latestAssessment == null) {
            throw new RuntimeException("用户尚未完成授信评估，无法借款");
        }

        // 8. 检查借款金额是否有效
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("借款金额必须大于0");
        }

        // 9. 检查还款期限是否有效
        if (request.getTermMonths() == null || request.getTermMonths() < 1) {
            throw new RuntimeException("还款期限必须大于0");
        }

        // DB 写操作统一放在一个事务内（含 FOR UPDATE 额度行锁）
        final boolean[] autoApprovedRef = {false};
        final BigDecimal[] remainingLimitRef = {BigDecimal.ZERO};
        Loan loan = transactionTemplate.execute(status -> {
            // 2. 检查用户是否有未处理逾期
            checkOverdue(userId);

            // 3. 检查用户是否有授信额度（FOR UPDATE 行锁）
            UserCreditLimit creditLimit = getUserCreditLimit(userId);
            if (creditLimit == null) {
                throw new RuntimeException("用户尚未获得授信额度，无法借款");
            }

            // 7. 检查用户剩余额度
            BigDecimal remainingLimit = creditLimit.getRemainingLimit();
            if (remainingLimit == null || remainingLimit.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("您的额度已用完，无法借款");
            }
            remainingLimitRef[0] = remainingLimit;

            // 6. 保存贷款记录
            Loan newLoan = new Loan();
            newLoan.setUserId(userId);
            newLoan.setAmount(request.getAmount());
            newLoan.setTermMonths(request.getTermMonths());
            newLoan.setInterestRate(new BigDecimal("0.05"));
            newLoan.setRepaymentMethod(request.getRepaymentMethod());
            newLoan.setApplyTime(new Date());
            newLoan.setCreateTime(new Date());
            newLoan.setUpdateTime(new Date());

            // 4. 根据额度判断处理方式
            if (request.getAmount().compareTo(remainingLimit) <= 0) {
                // 4.1 额度内借款，自动审批通过
                newLoan.setStatus(LoanStatusEnum.DISBURRSED.getCode());
                newLoan.setDisbursementTime(new Date());
                newLoan.setAutoApproved(true);

                // 4.2 扣减额度
                deductCreditLimit(creditLimit, request.getAmount());
            } else {
                // 4.3 额度外借款，需要审批
                newLoan.setStatus(LoanStatusEnum.PENDING_APPROVAL.getCode());
                newLoan.setAutoApproved(false);
            }

            loanMapper.insert(newLoan);

            // 6. 生成还款计划和还款记录（仅当自动审批通过时）
            if (newLoan.getAutoApproved()) {
                generateRepaymentPlan(newLoan, request.getRepaymentMethod());
            }
            autoApprovedRef[0] = Boolean.TRUE.equals(newLoan.getAutoApproved());
            return newLoan;
        });

        // 事务已提交，再做外部副作用调用，避免持有 DB 连接跨 HTTP
        if (autoApprovedRef[0]) {
            riskServiceClient.activateBehaviorScore(userId, latestAssessment.idCard());
        }

        // 7. 发送邮件通知
        sendLoanNotification(userId, request, remainingLimitRef[0], autoApprovedRef[0]);

        // 6. 构建响应
        LoanResponse response = new LoanResponse();
        BeanUtils.copyProperties(loan, response);
        if (autoApprovedRef[0]) {
            response.setRemark("额度充足，自动审批通过");
        } else {
            response.setRemark("借款金额超出剩余额度(" + remainingLimitRef[0] + ")，请等待管理员审批");
        }

        return response;
    }

    @Override
    public Page<LoanResponse> getUserLoanHistory(Long userId, Integer pageNum, Integer pageSize) {
        Page<Loan> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Loan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.orderByDesc("create_time");
        
        Page<Loan> loanPage = loanMapper.selectPage(page, queryWrapper);
        
        // 转换为响应对象
        List<LoanResponse> records = loanPage.getRecords().stream()
                .map(loan -> {
                    LoanResponse response = new LoanResponse();
                    BeanUtils.copyProperties(loan, response);
                    return response;
                })
                .collect(Collectors.toList());
        
        Page<LoanResponse> resultPage = new Page<>(pageNum, pageSize);
        resultPage.setRecords(records);
        resultPage.setTotal(loanPage.getTotal());
        resultPage.setSize(loanPage.getSize());
        resultPage.setCurrent(loanPage.getCurrent());
        resultPage.setPages(loanPage.getPages());
        
        return resultPage;
    }

    @Override
    public Page<LoanResponse> getUserLoanApplications(Long userId, String status, Integer pageNum, Integer pageSize) {
        Page<Loan> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Loan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        
        if (status != null && !status.isEmpty()) {
            queryWrapper.eq("status", status);
        }
        
        queryWrapper.orderByDesc("create_time");
        
        Page<Loan> loanPage = loanMapper.selectPage(page, queryWrapper);
        
        // 转换为响应对象
        List<LoanResponse> records = loanPage.getRecords().stream()
                .map(loan -> {
                    LoanResponse response = new LoanResponse();
                    BeanUtils.copyProperties(loan, response);
                    return response;
                })
                .collect(Collectors.toList());
        
        Page<LoanResponse> resultPage = new Page<>(pageNum, pageSize);
        resultPage.setRecords(records);
        resultPage.setTotal(loanPage.getTotal());
        resultPage.setSize(loanPage.getSize());
        resultPage.setCurrent(loanPage.getCurrent());
        resultPage.setPages(loanPage.getPages());
        
        return resultPage;
    }

    /**
     * 检查用户是否有未处理逾期
     */
    private void checkOverdue(Long userId) {
        QueryWrapper<Loan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("status", LoanStatusEnum.OVERDUE.getCode());
        
        long overdueCount = loanMapper.selectCount(queryWrapper);
        if (overdueCount > 0) {
            throw new RuntimeException("存在未处理逾期记录，无法发起新借款");
        }
    }

    /**
     * 获取用户信用额度（FOR UPDATE 行锁，防止并发借款超额）
     */
    private UserCreditLimit getUserCreditLimit(Long userId) {
        return userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>()
                        .eq("user_id", userId)
                        .last("FOR UPDATE")
        );
    }

    /**
     * 扣减用户信用额度
     */
    private void deductCreditLimit(UserCreditLimit creditLimit, BigDecimal amount) {
        // 计算新的已用额度和剩余额度
        BigDecimal newUsedLimit = creditLimit.getUsedLimit().add(amount);
        BigDecimal newRemainingLimit = creditLimit.getTotalLimit().subtract(newUsedLimit);
        
        // 更新额度记录
        creditLimit.setUsedLimit(newUsedLimit);
        creditLimit.setRemainingLimit(newRemainingLimit);
        creditLimit.setLastUpdateTime(new Date());
        
        userCreditLimitMapper.updateById(creditLimit);
    }

    /**
     * 发送借款通知
     */
    private void sendLoanNotification(Long userId, LoanRequest request, BigDecimal remainingLimit, boolean autoApproved) {
        UserSummary user = userServiceClient.getUser(userId);
        if (user == null) {
            return;
        }

        if (autoApproved) {
            // 发送借款成功通知给用户
            emailUtil.sendLoanSuccessNotification(
                    user.email(),
                    user.realName(),
                    request.getAmount().toString()
            );
        } else {
            // 发送借款审批通知给用户
            emailUtil.sendLoanApprovalNotification(
                    user.email(),
                    user.realName(),
                    request.getAmount().toString(),
                    remainingLimit.toString()
            );
            
        }
    }

    /**
     * 生成还款计划和还款记录
     */
    private void generateRepaymentPlan(Loan loan, String repaymentMethod) {
        // 1. 计算每期还款金额
        List<RepaymentCalculator.RepaymentDetail> details;
        switch (repaymentMethod) {
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
                throw new RuntimeException("不支持的还款方式: " + repaymentMethod);
        }

        // 2. 总金额按【本金+利息】累计，避免本金/利息口径不一致
        BigDecimal totalRepayable = BigDecimal.ZERO;
        for (RepaymentCalculator.RepaymentDetail detail : details) {
            totalRepayable = totalRepayable.add(detail.getAmount());
        }

        // 3. 创建还款计划
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

        // 4. 创建还款记录
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

            // 计算到期日
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
