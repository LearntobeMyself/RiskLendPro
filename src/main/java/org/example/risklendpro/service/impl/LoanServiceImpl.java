package org.example.risklendpro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.entity.Loan;
import org.example.risklendpro.entity.RepaymentPlan;
import org.example.risklendpro.entity.RepaymentRecord;
import org.example.risklendpro.entity.RiskAssessment;
import org.example.risklendpro.entity.User;
import org.example.risklendpro.entity.UserCreditLimit;
import org.example.risklendpro.enums.LoanStatusEnum;
import org.example.risklendpro.enums.StatusEnum;
import org.example.risklendpro.mapper.LoanMapper;
import org.example.risklendpro.mapper.RepaymentPlanMapper;
import org.example.risklendpro.mapper.RepaymentRecordMapper;
import org.example.risklendpro.mapper.RiskAssessmentMapper;
import org.example.risklendpro.mapper.UserCreditLimitMapper;
import org.example.risklendpro.mapper.UserMapper;
import org.example.risklendpro.pojo.request.LoanRequest;
import org.example.risklendpro.pojo.request.RiskAssessmentRequest;
import org.example.risklendpro.pojo.response.LoanResponse;
import org.example.risklendpro.service.CreditScoreEngine;
import org.example.risklendpro.service.LoanService;
import org.example.risklendpro.utils.EmailUtil;
import org.example.risklendpro.utils.RepaymentCalculator;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private UserMapper userMapper;

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    @Autowired
    private RiskAssessmentMapper riskAssessmentMapper;

    @Autowired
    private CreditScoreEngine creditScoreEngine;

    @Autowired
    private EmailUtil emailUtil;

    @Override
    @Transactional
    public LoanResponse requestLoan(Long userId, LoanRequest request) {
        // 1. 获取用户最新授信评估信息（用于获取身份证等必要信息）
        RiskAssessment latestAssessment = riskAssessmentMapper.selectOne(
            new QueryWrapper<RiskAssessment>()
                .eq("user_id", userId)
                .eq("is_final", true)
                .orderByDesc("approval_time")
                .last("LIMIT 1")
        );
        
        if (latestAssessment == null) {
            throw new RuntimeException("用户尚未完成授信评估，无法借款");
        }
        
        // 2. 检查用户是否有未处理逾期
        checkOverdue(userId);

        // 3. 重新进行风控评估（每次借款前都需要重新评估）
        int newScore = creditScoreEngine.calculateScore(buildRiskRequest(latestAssessment));
        String newDecision = creditScoreEngine.getDecision(newScore);

        // 4. 如果风控评估未通过，拒绝借款
        if (!"APPROVE".equals(newDecision)) {
            throw new RuntimeException("您的风控评估未通过（评分：" + newScore + "），无法借款");
        }

        // 5. 计算新的授信额度
        double newCreditLimit = creditScoreEngine.calculateCreditLimitWithFeatures(
            newScore, latestAssessment.getMonthlyIncome(), latestAssessment.getIdCard());

        // 6. 检查用户是否有授信额度
        UserCreditLimit creditLimit = getUserCreditLimit(userId);
        if (creditLimit == null) {
            throw new RuntimeException("用户尚未获得授信额度，无法借款");
        }

        // 7. 检查用户剩余额度
        BigDecimal remainingLimit = creditLimit.getRemainingLimit();
        if (remainingLimit == null || remainingLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("您的额度已用完，无法借款");
        }

        // 8. 检查借款金额是否有效
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("借款金额必须大于0");
        }

        // 9. 检查还款期限是否有效
        if (request.getTermMonths() == null || request.getTermMonths() < 1) {
            throw new RuntimeException("还款期限必须大于0");
        }

        // 所有前置检查通过，开始创建贷款记录
        // 6. 保存贷款记录
        Loan loan = new Loan();
        loan.setUserId(userId);
        loan.setAmount(request.getAmount());
        loan.setTermMonths(request.getTermMonths());
        loan.setInterestRate(new BigDecimal("0.05"));
        loan.setRepaymentMethod(request.getRepaymentMethod());
        loan.setApplyTime(new Date());
        loan.setCreateTime(new Date());
        loan.setUpdateTime(new Date());

        // 4. 根据额度判断处理方式
        if (request.getAmount().compareTo(remainingLimit) <= 0) {
            // 4.1 额度内借款，自动审批通过
            loan.setStatus(LoanStatusEnum.DISBURRSED.getCode());
            loan.setDisbursementTime(new Date());
            loan.setAutoApproved(true);
            
            // 4.2 扣减额度
            deductCreditLimit(creditLimit, request.getAmount());
        } else {
            // 4.3 额度外借款，需要审批
            loan.setStatus(LoanStatusEnum.PENDING_APPROVAL.getCode());
            loan.setAutoApproved(false);
        }

        loanMapper.insert(loan);

        // 6. 生成还款计划和还款记录（仅当自动审批通过时）
        if (loan.getAutoApproved()) {
            generateRepaymentPlan(loan, request.getRepaymentMethod());
        }

        // 7. 发送邮件通知
        sendLoanNotification(userId, request, remainingLimit, loan.getAutoApproved());

        // 6. 构建响应
        LoanResponse response = new LoanResponse();
        BeanUtils.copyProperties(loan, response);
        if (loan.getAutoApproved()) {
            response.setRemark("额度充足，自动审批通过");
        } else {
            response.setRemark("借款金额超出剩余额度(" + remainingLimit + ")，请等待管理员审批");
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
     * 获取用户信用额度
     */
    private UserCreditLimit getUserCreditLimit(Long userId) {
        return userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId)
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
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }

        if (autoApproved) {
            // 发送借款成功通知给用户
            emailUtil.sendLoanSuccessNotification(
                    user.getEmail(),
                    user.getRealName(),
                    request.getAmount().toString()
            );
        } else {
            // 发送借款审批通知给用户
            emailUtil.sendLoanApprovalNotification(
                    user.getEmail(),
                    user.getRealName(),
                    request.getAmount().toString(),
                    remainingLimit.toString()
            );
            
        }
    }

    /**
     * 生成还款计划和还款记录
     */
    private void generateRepaymentPlan(Loan loan, String repaymentMethod) {
        // 1. 创建还款计划
        RepaymentPlan plan = new RepaymentPlan();
        plan.setLoanId(loan.getLoanId());
        plan.setUserId(loan.getUserId());
        plan.setTotalAmount(loan.getAmount());
        plan.setPaidAmount(BigDecimal.ZERO);
        plan.setRemainingAmount(loan.getAmount());
        plan.setTotalPeriods(loan.getTermMonths());
        plan.setCurrentPeriod(1);
        plan.setStatus("ACTIVE");
        plan.setCreateTime(new Date());
        plan.setUpdateTime(new Date());
        
        repaymentPlanMapper.insert(plan);
        
        // 2. 计算每期还款金额
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
        
        // 3. 创建还款记录
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

    private RiskAssessmentRequest buildRiskRequest(RiskAssessment assessment) {
        RiskAssessmentRequest request = new RiskAssessmentRequest();
        request.setIdCard(assessment.getIdCard());
        request.setName(assessment.getName());
        request.setPhone(assessment.getPhone());
        request.setEmail(assessment.getEmail());
        request.setGender(assessment.getGender());
        request.setBirthday(assessment.getBirthday() != null ? assessment.getBirthday().toString() : null);
        request.setEducation(assessment.getEducation());
        request.setMarriage(assessment.getMarriage());
        request.setJobType(assessment.getJobType());
        request.setMonthlyIncome(assessment.getMonthlyIncome());
        request.setHasHouse(assessment.getHasHouse());
        request.setHasCar(assessment.getHasCar());
        request.setContactPhone(assessment.getContactPhone());
        return request;
    }
}
