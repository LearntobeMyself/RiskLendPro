package org.example.risklendpro.loan.borrow;

import org.example.risklendpro.api.contract.IdentityProofMaterials;
import org.example.risklendpro.api.dto.BorrowGateResult;
import org.example.risklendpro.common.mail.EmailUtil;
import org.example.risklendpro.loan.catalog.ProductApplySupport;
import org.example.risklendpro.loan.client.RiskServiceClient;
import org.example.risklendpro.loan.client.UserServiceClient;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 需求文档第 7 节「测试时用哪条产品」中可在单测内验证的规则：
 * 超过剩余额度必须拒绝、特定身份证明未上传不能借、房抵/车抵准入、年龄区间。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanApplyRuleTest {

    private static final Long USER_ID = 7L;
    private static final Long PRODUCT_ID = 100L;
    /** 出生于 1990-01-01，当前年龄落在常见 22~60 岁区间内。 */
    private static final String ADULT_ID_CARD = "110101199001011234";
    /** 出生于 2015-01-01，未满 18 岁。 */
    private static final String MINOR_ID_CARD = "110101201501011234";

    private LoanServiceImpl service;

    @Mock
    private LoanMapper loanMapper;
    @Mock
    private UserCreditLimitMapper userCreditLimitMapper;
    @Mock
    private RepaymentPlanMapper repaymentPlanMapper;
    @Mock
    private RepaymentRecordMapper repaymentRecordMapper;
    @Mock
    private EmailUtil emailUtil;
    @Mock
    private RiskServiceClient riskServiceClient;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private ProductApplySupport productApplySupport;
    @Mock
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        service = new LoanServiceImpl(transactionManager);
        ReflectionTestUtils.setField(service, "loanMapper", loanMapper);
        ReflectionTestUtils.setField(service, "userCreditLimitMapper", userCreditLimitMapper);
        ReflectionTestUtils.setField(service, "repaymentPlanMapper", repaymentPlanMapper);
        ReflectionTestUtils.setField(service, "repaymentRecordMapper", repaymentRecordMapper);
        ReflectionTestUtils.setField(service, "emailUtil", emailUtil);
        ReflectionTestUtils.setField(service, "riskServiceClient", riskServiceClient);
        ReflectionTestUtils.setField(service, "userServiceClient", userServiceClient);
        ReflectionTestUtils.setField(service, "productApplySupport", productApplySupport);
    }

    private void gateAllows(Boolean hasHouse, Boolean hasCar) {
        when(riskServiceClient.getBorrowGate(USER_ID))
                .thenReturn(BorrowGateResult.allow("A1", ADULT_ID_CARD, hasHouse, hasCar));
    }

    private UserCreditLimit limitWithRemaining(String remaining) {
        UserCreditLimit limit = new UserCreditLimit();
        limit.setUserId(USER_ID);
        limit.setTotalLimit(new BigDecimal("200000"));
        limit.setUsedLimit(new BigDecimal("200000").subtract(new BigDecimal(remaining)));
        limit.setRemainingLimit(new BigDecimal(remaining));
        limit.setHasOverdue(false);
        return limit;
    }

    private void noOverdue() {
        when(loanMapper.selectCount(any())).thenReturn(0L);
        when(repaymentPlanMapper.selectCount(any())).thenReturn(0L);
    }

    private ProductApplySupport.ResolvedApply apply(String categoryCode, List<String> collateralTypes,
                                                    Integer minAge, Integer maxAge, List<String> targetGroups) {
        return new ProductApplySupport.ResolvedApply(
                PRODUCT_ID, "薪金闪贷", ProductApplySupport.CHANNEL_PRODUCT, new BigDecimal("0.072"),
                "等额本息", categoryCode, "NONE", collateralTypes, null, minAge, maxAge,
                targetGroups, List.of());
    }

    private LoanRequest request(String amount, int termMonths) {
        LoanRequest request = new LoanRequest();
        request.setProductId(PRODUCT_ID);
        request.setAmount(new BigDecimal(amount));
        request.setTermMonths(termMonths);
        request.setRepaymentMethod("等额本息");
        return request;
    }

    @Test
    void creditLoanOverRemainingLimitIsRejected() {
        gateAllows(null, null);
        noOverdue();
        when(productApplySupport.resolve(any(), any(), any(), any()))
                .thenReturn(apply("PERSONAL_CREDIT", List.of(), 22, 55, List.of()));
        when(userCreditLimitMapper.selectOne(any())).thenReturn(limitWithRemaining("5000"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.requestLoan(USER_ID, request("5000.01", 12)));
        assertTrue(ex.getMessage().contains("超出可用信用额度"));
        verify(loanMapper, never()).insert(any(Loan.class));
    }

    @Test
    void creditLoanEqualToRemainingLimitIsDisbursedAndDeductsLimit() {
        gateAllows(null, null);
        noOverdue();
        when(productApplySupport.resolve(any(), any(), any(), any()))
                .thenReturn(apply("PERSONAL_CREDIT", List.of(), 22, 55, List.of()));
        UserCreditLimit limit = limitWithRemaining("5000");
        when(userCreditLimitMapper.selectOne(any())).thenReturn(limit);

        service.requestLoan(USER_ID, request("5000", 12));

        ArgumentCaptor<Loan> captor = ArgumentCaptor.forClass(Loan.class);
        verify(loanMapper).insert(captor.capture());
        assertEquals(LoanStatusEnum.DISBURSED.getCode(), captor.getValue().getStatus());
        verify(userCreditLimitMapper).updateById(any(UserCreditLimit.class));
        assertEquals(new BigDecimal("0"), limit.getRemainingLimit());
    }

    @Test
    void creditLoanWithoutIdentityProofIsRejected() {
        gateAllows(null, null);
        noOverdue();
        when(productApplySupport.resolve(any(), any(), any(), any()))
                .thenReturn(apply("PERSONAL_CREDIT", List.of(), 22, 55, List.of("SALARIED")));
        when(riskServiceClient.listIdentityProofs(USER_ID)).thenReturn(List.of());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.requestLoan(USER_ID, request("1000", 12)));
        assertTrue(ex.getMessage().contains("身份证明"));
        verify(loanMapper, never()).insert(any(Loan.class));
    }

    @Test
    void creditLoanWithIdentityProofIsDisbursed() {
        gateAllows(null, null);
        noOverdue();
        when(productApplySupport.resolve(any(), any(), any(), any()))
                .thenReturn(apply("PERSONAL_CREDIT", List.of(), 22, 55, List.of("SALARIED")));
        when(riskServiceClient.listIdentityProofs(USER_ID))
                .thenReturn(List.of(IdentityProofMaterials.SALARY));
        when(userCreditLimitMapper.selectOne(any())).thenReturn(limitWithRemaining("10000"));

        service.requestLoan(USER_ID, request("1000", 12));

        verify(loanMapper).insert(any(Loan.class));
    }

    @Test
    void ageBelowProductMinIsRejected() {
        gateAllows(null, null);
        when(productApplySupport.resolve(any(), any(), any(), any()))
                .thenReturn(apply("PERSONAL_CREDIT", List.of(), 40, 60, List.of()));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.requestLoan(USER_ID, request("1000", 12)));
        assertTrue(ex.getMessage().contains("年龄未达到"));
        verify(loanMapper, never()).insert(any(Loan.class));
    }

    @Test
    void ageAboveProductMaxIsRejected() {
        gateAllows(null, null);
        when(productApplySupport.resolve(any(), any(), any(), any()))
                .thenReturn(apply("PERSONAL_CREDIT", List.of(), 18, 30, List.of()));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.requestLoan(USER_ID, request("1000", 12)));
        assertTrue(ex.getMessage().contains("年龄超过"));
    }

    @Test
    void noAgeLimitStillRequiresAdult() {
        when(riskServiceClient.getBorrowGate(USER_ID))
                .thenReturn(BorrowGateResult.allow("A1", MINOR_ID_CARD, null, null));
        when(productApplySupport.resolve(any(), any(), any(), any()))
                .thenReturn(apply("PERSONAL_CREDIT", List.of(), null, null, List.of()));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.requestLoan(USER_ID, request("1000", 12)));
        assertTrue(ex.getMessage().contains("年龄未达到"));
    }

    @Test
    void houseMortgageWithoutHouseIsRejected() {
        gateAllows(false, null);
        when(productApplySupport.resolve(any(), any(), any(), any()))
                .thenReturn(apply("MORTGAGE", List.of(ProductApplySupport.COLLATERAL_HOUSE),
                        22, 60, List.of()));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.requestLoan(USER_ID, request("200000", 120)));
        assertTrue(ex.getMessage().contains("房抵产品仅限名下有房"));
    }

    @Test
    void vehicleMortgageWithoutCarIsRejected() {
        gateAllows(null, false);
        when(productApplySupport.resolve(any(), any(), any(), any()))
                .thenReturn(apply("MORTGAGE", List.of(ProductApplySupport.COLLATERAL_VEHICLE),
                        22, 55, List.of()));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.requestLoan(USER_ID, request("50000", 36)));
        assertTrue(ex.getMessage().contains("车抵产品仅限名下有车"));
    }

    @Test
    void vehicleMortgageGoesToPendingApprovalWithoutDeductingLimit() {
        gateAllows(null, true);
        noOverdue();
        when(productApplySupport.resolve(any(), any(), any(), any()))
                .thenReturn(apply("MORTGAGE", List.of(ProductApplySupport.COLLATERAL_VEHICLE),
                        22, 55, List.of()));
        when(userCreditLimitMapper.selectOne(any())).thenReturn(limitWithRemaining("3000"));

        service.requestLoan(USER_ID, request("50000", 36));

        ArgumentCaptor<Loan> captor = ArgumentCaptor.forClass(Loan.class);
        verify(loanMapper).insert(captor.capture());
        Loan inserted = captor.getValue();
        assertEquals(LoanStatusEnum.PENDING_APPROVAL.getCode(), inserted.getStatus());
        assertEquals(ProductApplySupport.COLLATERAL_VEHICLE, inserted.getCollateralType());
        verify(userCreditLimitMapper, never()).updateById(any(UserCreditLimit.class));
    }

    @Test
    void mortgageLoanIsNotBlockedByCreditLimit() {
        gateAllows(false, true);
        noOverdue();
        when(productApplySupport.resolve(any(), any(), any(), any()))
                .thenReturn(apply("MORTGAGE", List.of(ProductApplySupport.COLLATERAL_VEHICLE),
                        22, 55, List.of()));
        when(userCreditLimitMapper.selectOne(any())).thenReturn(limitWithRemaining("0"));

        service.requestLoan(USER_ID, request("500000", 36));

        verify(loanMapper).insert(any(Loan.class));
        verify(riskServiceClient, never()).activateBehaviorScore(eq(USER_ID), any());
    }
}
