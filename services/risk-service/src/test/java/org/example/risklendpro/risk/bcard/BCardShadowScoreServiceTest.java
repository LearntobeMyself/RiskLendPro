package org.example.risklendpro.risk.bcard;

import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.risk.score.BehaviorScoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 A4 必需测试：影子模式不写额度。
 *
 * <p>影子评分只允许写 b_card_shadow_decision（由 recorder 承担）；
 * 本测试校验 UserCreditLimitMapper 只被读取，从未发生 update/insert/delete。</p>
 */
@ExtendWith(MockitoExtension.class)
class BCardShadowScoreServiceTest {

    private static final String MODEL_JSON = "{"
            + "\"model_version\":\"A4-test\","
            + "\"feature_version\":\"BF_V2.2\","
            + "\"intercept\":0.0,"
            + "\"coefficients\":{\"f1\":1.0},"
            + "\"feature_defs\":{\"f1\":{\"type\":\"numeric\",\"cuts\":[1.0],\"woe\":[0.5]}},"
            + "\"calibration\":{\"method\":\"platt\",\"a\":1.0,\"b\":0.0},"
            + "\"policy\":{\"approve_below\":0.3,\"reject_above\":0.6,"
            + "\"multipliers\":{\"approve\":1.0,\"reduce\":0.5,\"reject\":0.0}},"
            + "\"pdo\":{\"pdo\":80,\"target_score\":650,\"target_odds\":1,\"min_score\":350,\"max_score\":950}"
            + "}";

    @InjectMocks
    private BCardShadowScoreService service;

    @Mock
    private UserCreditLimitMapper userCreditLimitMapper;

    @Mock
    private BehaviorScoreService behaviorScoreService;

    @Mock
    private BCardShadowSnapshotReader snapshotReader;

    @Mock
    private BCardShadowDecisionRecorder recorder;

    @TempDir
    Path tempDir;

    @Test
    void shadowRunRecordsDecisionButNeverWritesCreditLimit() throws Exception {
        Path modelFile = writeModel();
        ReflectionTestUtils.setField(service, "modelPath", modelFile.toString());
        ReflectionTestUtils.setField(service, "maxUsers", 10);

        UserCreditLimit limit = new UserCreditLimit();
        limit.setUserId(68L);
        limit.setBCardEnabled(true);
        limit.setBScore(BigDecimal.valueOf(600));
        when(userCreditLimitMapper.selectList(any())).thenReturn(List.of(limit));
        when(snapshotReader.findLatest(68L, "BF_V2.2")).thenReturn(Optional.of(
                new BCardShadowSnapshotReader.ShadowFeature(
                        LocalDate.of(2026, 10, 19), "BF_V2.2", Map.of("f1", 1.5))));
        when(behaviorScoreService.resolveLimitMultiplier(600.0)).thenReturn(0.8);

        int recorded = service.scoreShadowDecisions();

        assertEquals(1, recorded);
        ArgumentCaptor<BCardShadowDecisionRecorder.ShadowDecision> captor =
                ArgumentCaptor.forClass(BCardShadowDecisionRecorder.ShadowDecision.class);
        verify(recorder).record(captor.capture());
        BCardShadowDecisionRecorder.ShadowDecision decision = captor.getValue();

        assertEquals(68L, decision.userId());
        assertEquals(LocalDate.of(2026, 10, 19), decision.asOfDate());
        assertEquals("A4-test", decision.modelVersion());
        assertNotNull(decision.modelProbability());
        assertTrue(decision.modelProbability() > 0.6, "sigmoid(0.5)≈0.6225");
        assertEquals(0.0, decision.modelMultiplier(), 1e-9, "p>=reject_above -> 0.0");
        assertEquals(0.8, decision.currentMultiplier(), 1e-9);
        assertTrue(decision.decisionDiffers());
        assertTrue(decision.reason().contains("REJECT_DIFF"));

        // 关键护栏：额度表只读，绝无任何写入
        verify(userCreditLimitMapper, never()).updateById(any(UserCreditLimit.class));
        verify(userCreditLimitMapper, never()).insert(any(UserCreditLimit.class));
        verify(userCreditLimitMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void shadowRunSkipsWhenModelNotConfigured() {
        ReflectionTestUtils.setField(service, "modelPath", "");

        assertEquals(0, service.scoreShadowDecisions());
        verify(recorder, never()).record(any());
        verify(userCreditLimitMapper, never()).selectList(any());
    }

    @Test
    void shadowRunSkipsUsersWithoutSnapshot() throws Exception {
        Path modelFile = writeModel();
        ReflectionTestUtils.setField(service, "modelPath", modelFile.toString());
        ReflectionTestUtils.setField(service, "maxUsers", 10);

        UserCreditLimit limit = new UserCreditLimit();
        limit.setUserId(69L);
        limit.setBCardEnabled(true);
        when(userCreditLimitMapper.selectList(any())).thenReturn(List.of(limit));
        when(snapshotReader.findLatest(69L, "BF_V2.2")).thenReturn(Optional.empty());

        assertEquals(0, service.scoreShadowDecisions());
        verify(recorder, never()).record(any());
        verify(userCreditLimitMapper, never()).updateById(any(UserCreditLimit.class));
    }

    private Path writeModel() throws IOException {
        Path file = tempDir.resolve("shadow_model.json");
        Files.write(file, MODEL_JSON.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}