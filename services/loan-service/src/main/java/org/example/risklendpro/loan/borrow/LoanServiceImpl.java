package org.example.risklendpro.loan.borrow;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.api.contract.IdentityProofMaterials;
import org.example.risklendpro.api.dto.BorrowGateResult;
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
import org.example.risklendpro.loan.catalog.ProductApplySupport;
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
import java.time.LocalDate;
import java.time.Period;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
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

    @Autowired
    private ProductApplySupport productApplySupport;

    private final TransactionTemplate transactionTemplate;

    public LoanServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public LoanResponse requestLoan(Long userId, LoanRequest request) {
        BorrowGateResult gate = riskServiceClient.getBorrowGate(userId);
        if (gate == null || !gate.allowed()) {
            String message = gate == null || gate.reasonMessage() == null || gate.reasonMessage().isBlank()
                    ? "当前无法借款"
                    : gate.reasonMessage();
            throw new RuntimeException(message);
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("借款金额必须大于0");
        }
        if (request.getTermMonths() == null || request.getTermMonths() < 1) {
            throw new RuntimeException("还款期限必须大于0");
        }

        ProductApplySupport.ResolvedApply apply = resolveApply(request);

        if (!"等额本息".equals(apply.repaymentMethod())) {
            throw new RuntimeException("还款方式必须为等额本息");
        }
        validateAge(gate.idCard(), apply);
        validateIdentityProofs(userId, apply);

        if (ProductApplySupport.isMortgageCategory(apply.categoryCode())) {
            return requestMortgageLoan(userId, request, apply, gate);
        }
        return requestCreditLoan(userId, request, apply, gate);
    }

    /**
     * 信用类（个人信用贷、消费分期、经营贷、直接支用）：金额须落在产品区间内且不超过剩余信用额度，
     * 合格即发放并扣减剩余额度；超过剩余额度直接拒绝，不再转管理员审批。
     */
    private LoanResponse requestCreditLoan(Long userId, LoanRequest request,
                                           ProductApplySupport.ResolvedApply apply, BorrowGateResult gate) {
        Loan loan = transactionTemplate.execute(status -> {
            checkOverdue(userId);

            UserCreditLimit creditLimit = getUserCreditLimit(userId);
            if (creditLimit == null) {
                throw new RuntimeException("用户尚未获得授信额度，无法借款");
            }

            BigDecimal remainingLimit = creditLimit.getRemainingLimit();
            if (remainingLimit == null || remainingLimit.compareTo(BigDecimal.ZERO) <= 0
                    || request.getAmount().compareTo(remainingLimit) > 0) {
                throw new RuntimeException("超出可用信用额度");
            }

            Loan newLoan = buildLoan(userId, request, apply);
            newLoan.setStatus(LoanStatusEnum.DISBURSED.getCode());
            newLoan.setDisbursementTime(new Date());
            newLoan.setAutoApproved(true);
            deductCreditLimit(creditLimit, request.getAmount());

            loanMapper.insert(newLoan);
            generateRepaymentPlan(newLoan, apply.repaymentMethod());
            return newLoan;
        });

        riskServiceClient.activateBehaviorScore(userId, gate.idCard());
        notifyLoanDisbursed(userId, request);

        LoanResponse response = new LoanResponse();
        BeanUtils.copyProperties(loan, response);
        response.setRemark("额度充足，自动审批通过");
        return response;
    }

    /**
     * 房抵、车抵：按抵押物类型做准入与互斥校验，材料齐全后进入等待审批，不占用信用额度；
     * 管理员确认抵押物评估价后按 评估价×抵押率 放款。
     */
    private LoanResponse requestMortgageLoan(Long userId, LoanRequest request,
                                             ProductApplySupport.ResolvedApply apply, BorrowGateResult gate) {
        String collateralType = ProductApplySupport.collateralTypeOf(apply.collateralTypes());
        if (collateralType == null) {
            throw new RuntimeException("该抵押产品未配置可接受的抵押物类型");
        }
        boolean hasCollateral = ProductApplySupport.COLLATERAL_HOUSE.equals(collateralType)
                ? Boolean.TRUE.equals(gate.hasHouse()) : Boolean.TRUE.equals(gate.hasCar());
        // 名下有房/有车，或此前已有管理员确认过的同类抵押物（评估价已录入），均可申请
        if (!hasCollateral && !hasConfirmedCollateral(userId, collateralType)) {
            throw new RuntimeException(ProductApplySupport.COLLATERAL_HOUSE.equals(collateralType)
                    ? "房抵产品仅限名下有房或已有确认过的房产材料的用户申请"
                    : "车抵产品仅限名下有车或已有确认过的车辆材料的用户申请");
        }

        Loan loan = transactionTemplate.execute(status -> {
            checkOverdue(userId);
            long pendingSameType = loanMapper.selectCount(new QueryWrapper<Loan>()
                    .eq("user_id", userId)
                    .eq("status", LoanStatusEnum.PENDING_APPROVAL.getCode())
                    .eq("collateral_type", collateralType));
            if (pendingSameType > 0) {
                throw new RuntimeException("已有同类抵押申请等待审批，暂不能重复申请");
            }
            Loan newLoan = buildLoan(userId, request, apply);
            newLoan.setCollateralType(collateralType);
            newLoan.setStatus(LoanStatusEnum.PENDING_APPROVAL.getCode());
            newLoan.setAutoApproved(false);
            loanMapper.insert(newLoan);
            return newLoan;
        });

        notifyMortgagePending(userId, request, apply);

        LoanResponse response = new LoanResponse();
        BeanUtils.copyProperties(loan, response);
        response.setRemark("抵押申请已提交，等待管理员确认抵押物后放款");
        return response;
    }

    /** 年龄取最近一次已通过评估的出生日期（身份证号解析）。未写年龄限制的只要求已满 18 岁。 */
    private void validateAge(String idCard, ProductApplySupport.ResolvedApply apply) {
        Integer age = ageFromIdCard(idCard);
        if (age == null) {
            return;
        }
        int minAge = apply.minAge() != null ? apply.minAge() : 18;
        if (age < minAge) {
            throw new RuntimeException("年龄未达到该产品要求");
        }
        if (apply.maxAge() != null && age > apply.maxAge()) {
            throw new RuntimeException("年龄超过该产品限制");
        }
    }

    private Integer ageFromIdCard(String idCard) {
        if (idCard == null || idCard.length() < 14) {
            return null;
        }
        try {
            LocalDate birth = LocalDate.of(
                    Integer.parseInt(idCard.substring(6, 10)),
                    Integer.parseInt(idCard.substring(10, 12)),
                    Integer.parseInt(idCard.substring(12, 14)));
            return Period.between(birth, LocalDate.now()).getYears();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 该用户此前是否已有管理员确认过的同类抵押物（已录入评估价）。 */
    private boolean hasConfirmedCollateral(Long userId, String collateralType) {
        Long count = loanMapper.selectCount(new QueryWrapper<Loan>()
                .eq("user_id", userId)
                .eq("collateral_type", collateralType)
                .isNotNull("collateral_value"));
        return count != null && count > 0;
    }

    /** 产品要求特定身份时，对应证明材料必须先通过补充材料接口上传。 */
    private void validateIdentityProofs(Long userId, ProductApplySupport.ResolvedApply apply) {
        // 材料清单未提及身份证明的产品（如随心周转金「职业不限」）不要求上传证明
        if (!IdentityProofMaterials.mentionsIdentityProof(apply.materials())) {
            return;
        }
        Set<String> required = IdentityProofMaterials.requiredCodes(apply.targetGroups());
        if (required.isEmpty()) {
            return;
        }
        List<String> uploaded = riskServiceClient.listIdentityProofs(userId);
        boolean satisfied = uploaded != null && uploaded.stream().anyMatch(required::contains);
        if (!satisfied) {
            throw new RuntimeException("该产品需要上传身份证明，请先上传后再申请");
        }
    }

    private Loan buildLoan(Long userId, LoanRequest request, ProductApplySupport.ResolvedApply apply) {
        Loan newLoan = new Loan();
        newLoan.setUserId(userId);
        newLoan.setAmount(request.getAmount());
        newLoan.setTermMonths(request.getTermMonths());
        newLoan.setInterestRate(apply.interestRate());
        newLoan.setRepaymentMethod(apply.repaymentMethod());
        newLoan.setProductId(apply.productId());
        newLoan.setProductName(apply.productName());
        newLoan.setApplyChannel(apply.applyChannel());
        newLoan.setApplyTime(new Date());
        newLoan.setCreateTime(new Date());
        newLoan.setUpdateTime(new Date());
        return newLoan;
    }

    private ProductApplySupport.ResolvedApply resolveApply(LoanRequest request) {
        if (request.getProductId() == null) {
            return new ProductApplySupport.ResolvedApply(
                    null,
                    null,
                    ProductApplySupport.CHANNEL_DIRECT,
                    ProductApplySupport.DIRECT_RATE,
                    ProductApplySupport.normalizeRepaymentMethod(request.getRepaymentMethod()));
        }
        return productApplySupport.resolve(
                request.getProductId(),
                request.getAmount(),
                request.getTermMonths(),
                request.getRepaymentMethod());
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
        long overdueLoanCount = loanMapper.selectCount(queryWrapper);
        if (overdueLoanCount > 0) {
            throw new RuntimeException("存在未处理逾期记录，无法发起新借款");
        }

        UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId));
        if (creditLimit != null && Boolean.TRUE.equals(creditLimit.getHasOverdue())) {
            throw new RuntimeException("存在未处理逾期记录，无法发起新借款");
        }

        long overduePlanCount = repaymentPlanMapper.selectCount(
                new QueryWrapper<RepaymentPlan>().eq("user_id", userId).eq("status", "OVERDUE"));
        if (overduePlanCount > 0) {
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
     * 信用类自动放款后的借款成功通知
     */
    private void notifyLoanDisbursed(Long userId, LoanRequest request) {
        UserSummary user = userServiceClient.getUser(userId);
        if (user == null) {
            return;
        }
        emailUtil.sendLoanSuccessNotification(
                user.email(),
                user.realName(),
                request.getAmount().toString()
        );
    }

    /**
     * 抵押类申请已受理、等待管理员确认抵押物后的受理通知
     */
    private void notifyMortgagePending(Long userId, LoanRequest request, ProductApplySupport.ResolvedApply apply) {
        UserSummary user = userServiceClient.getUser(userId);
        if (user == null) {
            return;
        }
        emailUtil.sendMortgageLoanPendingNotification(
                user.email(),
                user.realName(),
                request.getAmount().toString(),
                apply.productName()
        );
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
