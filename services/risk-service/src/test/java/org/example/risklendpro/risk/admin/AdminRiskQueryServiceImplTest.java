package org.example.risklendpro.risk.admin;

import org.example.risklendpro.api.dto.CreditLimitGrantCommand;
import org.example.risklendpro.risk.client.LoanServiceClient;
import org.example.risklendpro.risk.client.UserServiceClient;
import org.example.risklendpro.risk.entity.RiskAssessment;
import org.example.risklendpro.risk.mapper.RiskAssessmentMapper;
import org.example.risklendpro.risk.supplement.SupplementMaterialService;
import org.example.risklendpro.common.mail.EmailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRiskQueryServiceImplTest {

    @InjectMocks
    private AdminRiskQueryServiceImpl service;

    @Mock
    private RiskAssessmentMapper riskAssessmentMapper;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private LoanServiceClient loanServiceClient;
    @Mock
    private EmailUtil emailUtil;
    @Mock
    private SupplementMaterialService supplementMaterialService;

    @BeforeEach
    void useDirectSelf() {
        ReflectionTestUtils.setField(service, "self", service);
    }

    @Test
    void approveRisk_mailFailureDoesNotUndoApproval() {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setApplyId("apply-1");
        assessment.setUserId(7L);
        assessment.setEmail("a@example.com");
        assessment.setName("测试");
        assessment.setStatus("MANUAL_REVIEW");
        assessment.setIsFinal(false);
        when(riskAssessmentMapper.selectOne(any())).thenReturn(assessment);
        doThrow(new RuntimeException("smtp down")).when(emailUtil)
                .sendRiskAssessmentNotification(any(), any(), any(), any());

        RiskApproveRequest request = new RiskApproveRequest();
        request.setApplyId("apply-1");
        request.setAuditResult("PASS");
        request.setCreditLimit(new BigDecimal("50000"));

        assertFalse(service.approveRisk(request));

        InOrder order = inOrder(riskAssessmentMapper, loanServiceClient, emailUtil);
        order.verify(loanServiceClient).grantCreditLimit(any(CreditLimitGrantCommand.class));
        order.verify(riskAssessmentMapper).updateById(any(RiskAssessment.class));
        order.verify(emailUtil).sendRiskAssessmentNotification(any(), any(), any(), any());
        verify(supplementMaterialService).clearSupplementOnFinalApproval("apply-1");
    }

    @Test
    void approveRisk_mailSuccess() {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setApplyId("apply-2");
        assessment.setUserId(8L);
        assessment.setStatus("MANUAL_REVIEW");
        assessment.setIsFinal(false);
        when(riskAssessmentMapper.selectOne(any())).thenReturn(assessment);

        RiskApproveRequest request = new RiskApproveRequest();
        request.setApplyId("apply-2");
        request.setAuditResult("REJECT");

        assertTrue(service.approveRisk(request));
    }
}
