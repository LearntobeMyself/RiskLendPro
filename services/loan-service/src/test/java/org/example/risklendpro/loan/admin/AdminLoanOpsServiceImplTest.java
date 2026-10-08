package org.example.risklendpro.loan.admin;

import org.example.risklendpro.loan.borrow.LoanApproveRequest;
import org.example.risklendpro.loan.borrow.LoanStatusEnum;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.mapper.LimitAdjustLogMapper;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.loan.mapper.VintageDataMapper;
import org.example.risklendpro.common.mail.EmailUtil;
import org.example.risklendpro.loan.client.RiskServiceClient;
import org.example.risklendpro.loan.client.UserServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminLoanOpsServiceImplTest {

    @InjectMocks
    private AdminLoanOpsServiceImpl service;

    @Mock
    private LoanMapper loanMapper;
    @Mock
    private UserCreditLimitMapper userCreditLimitMapper;
    @Mock
    private LimitAdjustLogMapper limitAdjustLogMapper;
    @Mock
    private VintageDataMapper vintageDataMapper;
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

    @Test
    void approveLoan_alreadyDisbursed_throws() {
        Loan loan = new Loan();
        loan.setLoanId(1L);
        loan.setUserId(7L);
        loan.setStatus(LoanStatusEnum.DISBURSED.getCode());
        when(loanMapper.selectOne(any())).thenReturn(loan);

        LoanApproveRequest request = new LoanApproveRequest();
        request.setLoanId(1L);
        request.setApproveResult("APPROVE");

        assertThrows(RuntimeException.class, () -> service.approveLoan(request));
        verify(loanMapper, never()).updateById(any(Loan.class));
        verify(repaymentPlanMapper, never()).insert(any(RepaymentPlan.class));
    }
}
