package org.example.risklendpro.risk.client;

import org.example.risklendpro.api.dto.LimitMultiplierDecision;
import org.example.risklendpro.risk.score.BehaviorScoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 A0：对外的额度系数接口必须走带 userId 的可信度决策，而不是裸系数。
 */
@ExtendWith(MockitoExtension.class)
class InternalRiskControllerTest {

    @InjectMocks
    private InternalRiskController controller;

    @Mock
    private BehaviorScoreService behaviorScoreService;

    @Test
    void getLimitMultiplierForUser_delegatesToUserScopedDecision() {
        LimitMultiplierDecision decision = new LimitMultiplierDecision(
                7L, 0.8, true, true, "RELIABLE", "v1.0-b-woe",
                "HC_STATIC_V1+LIVE_V1", "BCARD_LIMIT_POLICY_V1", "hash", "ok");

        when(behaviorScoreService.resolveLimitMultiplierForUser(7L, 600.0)).thenReturn(decision);

        LimitMultiplierDecision result = controller.getLimitMultiplierForUser(7L, 600.0);

        assertSame(decision, result);
        verify(behaviorScoreService).resolveLimitMultiplierForUser(7L, 600.0);
    }
}