package org.example.risklendpro.risk.score;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.api.dto.CreditBehaviorUpsertCommand;
import org.example.risklendpro.api.dto.CreditLimitSnapshot;
import org.example.risklendpro.api.dto.LimitMultiplierDecision;
import org.example.risklendpro.api.dto.UserSummary;
import org.example.risklendpro.risk.client.LoanServiceClient;
import org.example.risklendpro.risk.client.UserServiceClient;
import org.example.risklendpro.risk.bcard.BCardDecisionAuditRecorder;
import org.example.risklendpro.risk.credit.BehaviorScoringRules;
import org.example.risklendpro.risk.credit.UserBehaviorFeatures;
import org.example.risklendpro.risk.credit.mapper.BehaviorScoringRulesMapper;
import org.example.risklendpro.risk.credit.mapper.UserBehaviorFeaturesMapper;
import org.example.risklendpro.risk.entity.UserBCardLog;
import org.example.risklendpro.risk.mapper.BCardFeatureSnapshotMapper;
import org.example.risklendpro.risk.mapper.UserBCardLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BehaviorScoreServiceTest {

    @InjectMocks
    private BehaviorScoreService behaviorScoreService;

    @Mock
    private BehaviorScoreEngine behaviorScoreEngine;

    @Mock
    private BehaviorLiveFeatureService behaviorLiveFeatureService;

    @Mock
    private UserBehaviorFeaturesMapper userBehaviorFeaturesMapper;

    @Mock
    private BCardFeatureSnapshotMapper bCardFeatureSnapshotMapper;

    @Mock
    private UserBCardLogMapper userBCardLogMapper;

    @Mock
    private BehaviorScoringRulesMapper behaviorScoringRulesMapper;

    @Mock
    private LoanServiceClient loanServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private BCardDecisionAuditRecorder decisionAuditRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recalculate_resolvesIdCardAndMarksScoreReliable() throws Exception {
        CreditLimitSnapshot limit = limitFor(68L);
        UserSummary user = userWithIdCard(68L, "ID-68");
        UserBehaviorFeatures snapshot = new UserBehaviorFeatures();
        snapshot.setIdCard("ID-68");
        snapshot.setFeatureJson("{\"feature\":1.0}");
        BehaviorScoringRules rules = new BehaviorScoringRules();
        rules.setVersion("v1.0-b-woe");
        rules.setRuleContent("{\"a\":1}");

        BehaviorLiveFeatureService.LiveFeatures live = new BehaviorLiveFeatureService.LiveFeatures();
        live.setHistoryAvailable(true);
        live.setOnTimeRate(1.0);

        when(loanServiceClient.getCreditLimit(68L)).thenReturn(limit);
        when(userServiceClient.getUser(68L)).thenReturn(user);
        when(userBehaviorFeaturesMapper.selectByIdCard("ID-68")).thenReturn(snapshot);
        when(behaviorScoreEngine.calculateBaseScore(anyMap())).thenReturn(620.0);
        when(behaviorScoringRulesMapper.selectActiveRule()).thenReturn(rules);
        when(behaviorLiveFeatureService.aggregate(68L)).thenReturn(live);
        when(userBCardLogMapper.insert(any(UserBCardLog.class))).thenReturn(1);

        behaviorScoreService.recalculate(68L);

        verify(userServiceClient).getUser(68L);
        verify(userBehaviorFeaturesMapper).selectByIdCard("ID-68");

        ArgumentCaptor<CreditBehaviorUpsertCommand> cmdCaptor = ArgumentCaptor.forClass(CreditBehaviorUpsertCommand.class);
        verify(loanServiceClient).upsertBehaviorScore(cmdCaptor.capture());
        assertEquals(640.0, cmdCaptor.getValue().behaviorScore().doubleValue(), 0.001);
        assertNull(cmdCaptor.getValue().bCardEnabled());

        Map<String, Object> liveFeatures = captureLiveFeatures();
        assertTrue((Boolean) liveFeatures.get("baseScoreResolved"));
        assertTrue((Boolean) liveFeatures.get("scoreReliable"));
        assertEquals("RELIABLE", liveFeatures.get("dataStatus"));
        // 额度调整日志所需审计字段：模型版本 / 特征版本 / 策略版本 / 规则哈希
        assertEquals("v1.0-b-woe", liveFeatures.get("modelVersion"));
        assertEquals(BehaviorScoreService.FEATURE_VERSION, liveFeatures.get("featureVersion"));
        assertEquals(BehaviorScoreService.POLICY_VERSION, liveFeatures.get("policyVersion"));
        assertNotNull(liveFeatures.get("ruleHash"));
    }

    @Test
    void recalculate_marksMissingFeatureSnapshotUnreliable() throws Exception {
        CreditLimitSnapshot limit = limitFor(101L);
        UserSummary user = userWithIdCard(101L, "ID-101");

        BehaviorLiveFeatureService.LiveFeatures live = new BehaviorLiveFeatureService.LiveFeatures();
        live.setHistoryAvailable(true);
        live.setOnTimeRate(0.0);

        when(loanServiceClient.getCreditLimit(101L)).thenReturn(limit);
        when(userServiceClient.getUser(101L)).thenReturn(user);
        when(userBehaviorFeaturesMapper.selectByIdCard("ID-101")).thenReturn(null);
        when(behaviorLiveFeatureService.aggregate(101L)).thenReturn(live);
        when(userBCardLogMapper.insert(any(UserBCardLog.class))).thenReturn(1);

        behaviorScoreService.recalculate(101L);

        Map<String, Object> liveFeatures = captureLiveFeatures();
        assertFalse((Boolean) liveFeatures.get("baseScoreResolved"));
        assertFalse((Boolean) liveFeatures.get("scoreReliable"));
        assertEquals("FEATURE_NOT_FOUND", liveFeatures.get("dataStatus"));
        verify(loanServiceClient, never()).upsertBehaviorScore(any());
    }

    @Test
    void recalculate_marksRuleUnavailableAsUnreliable() throws Exception {
        CreditLimitSnapshot limit = limitFor(202L);
        UserSummary user = userWithIdCard(202L, "ID-202");
        UserBehaviorFeatures snapshot = new UserBehaviorFeatures();
        snapshot.setIdCard("ID-202");
        snapshot.setFeatureJson("{\"feature\":1.0}");

        BehaviorLiveFeatureService.LiveFeatures live = new BehaviorLiveFeatureService.LiveFeatures();
        live.setHistoryAvailable(true);
        live.setOnTimeRate(1.0);

        when(loanServiceClient.getCreditLimit(202L)).thenReturn(limit);
        when(userServiceClient.getUser(202L)).thenReturn(user);
        when(userBehaviorFeaturesMapper.selectByIdCard("ID-202")).thenReturn(snapshot);
        when(behaviorScoreEngine.calculateBaseScore(anyMap()))
                .thenThrow(new BehaviorScoreEngineImpl.RuleUnavailableException(
                        BehaviorScoreEngineImpl.STATUS_RULE_NOT_FOUND, "no active rule"));
        when(behaviorLiveFeatureService.aggregate(202L)).thenReturn(live);
        when(userBCardLogMapper.insert(any(UserBCardLog.class))).thenReturn(1);

        behaviorScoreService.recalculate(202L);

        Map<String, Object> liveFeatures = captureLiveFeatures();
        assertFalse((Boolean) liveFeatures.get("baseScoreResolved"));
        assertFalse((Boolean) liveFeatures.get("scoreReliable"));
        assertEquals(BehaviorScoreEngineImpl.STATUS_RULE_NOT_FOUND, liveFeatures.get("dataStatus"));
    }

    @Test
    void resolveLimitMultiplierForUser_deniesWhenScoreUnreliable() {
        UserBCardLog latestLog = new UserBCardLog();
        latestLog.setUserId(101L);
        latestLog.setLiveFeatures("{\"scoreReliable\":false,\"dataStatus\":\"FEATURE_NOT_FOUND\"}");

        when(userBCardLogMapper.selectOne(any())).thenReturn(latestLog);

        LimitMultiplierDecision decision = behaviorScoreService.resolveLimitMultiplierForUser(101L, 600.0);

        assertNotNull(decision);
        assertFalse(decision.scoreReliable());
        assertFalse(decision.decisionAllowed());
        assertNull(decision.multiplier());
        assertEquals("FEATURE_NOT_FOUND", decision.dataStatus());
    }

    @Test
    void resolveLimitMultiplierForUser_deniesWhenNoScoreLog() {
        when(userBCardLogMapper.selectOne(any())).thenReturn(null);

        LimitMultiplierDecision decision = behaviorScoreService.resolveLimitMultiplierForUser(303L, 600.0);

        assertFalse(decision.decisionAllowed());
        assertNull(decision.multiplier());
        assertEquals("NO_SCORE_LOG", decision.dataStatus());
    }

    @Test
    void resolveLimitMultiplierForUser_allowsWhenReliable() {
        UserBCardLog latestLog = new UserBCardLog();
        latestLog.setUserId(404L);
        latestLog.setLiveFeatures("{\"scoreReliable\":true,\"dataStatus\":\"RELIABLE\","
                + "\"modelVersion\":\"v1.0-b-woe\",\"featureVersion\":\"HC_STATIC_V1+LIVE_V1\","
                + "\"policyVersion\":\"BCARD_LIMIT_POLICY_V1\",\"ruleHash\":\"abcdef\"}");

        when(userBCardLogMapper.selectOne(any())).thenReturn(latestLog);
        when(behaviorScoreEngine.getThresholdWatch()).thenReturn(650.0);
        when(behaviorScoreEngine.getThresholdReduceLimit()).thenReturn(550.0);

        LimitMultiplierDecision decision = behaviorScoreService.resolveLimitMultiplierForUser(404L, 600.0);

        assertTrue(decision.scoreReliable());
        assertTrue(decision.decisionAllowed());
        assertEquals(0.8, decision.multiplier(), 0.0001);
        assertEquals("v1.0-b-woe", decision.modelVersion());
        assertEquals("HC_STATIC_V1+LIVE_V1", decision.featureVersion());
        assertEquals("abcdef", decision.ruleHash());
    }

    @Test
    void resolveLimitMultiplierForUser_recordsAuditableDecision() {
        UserBCardLog latestLog = new UserBCardLog();
        latestLog.setId(777L);
        latestLog.setUserId(505L);
        latestLog.setLiveFeatures("{\"scoreReliable\":true,\"dataStatus\":\"RELIABLE\","
                + "\"modelVersion\":\"v1.0-b-woe\",\"featureVersion\":\"HC_STATIC_V1+LIVE_V1\","
                + "\"policyVersion\":\"BCARD_LIMIT_POLICY_V1\",\"ruleHash\":\"h1\"}");
        when(userBCardLogMapper.selectOne(any())).thenReturn(latestLog);
        when(behaviorScoreEngine.getThresholdWatch()).thenReturn(650.0);
        when(behaviorScoreEngine.getThresholdReduceLimit()).thenReturn(550.0);

        behaviorScoreService.resolveLimitMultiplierForUser(505L, 600.0);

        ArgumentCaptor<BCardDecisionAuditRecorder.AuditEntry> captor =
                ArgumentCaptor.forClass(BCardDecisionAuditRecorder.AuditEntry.class);
        verify(decisionAuditRecorder).record(captor.capture());
        BCardDecisionAuditRecorder.AuditEntry entry = captor.getValue();
        assertEquals(505L, entry.userId());
        assertEquals("QUOTA_MULTIPLIER", entry.decisionType());
        assertEquals("v1.0-b-woe", entry.modelVersion());
        assertEquals("HC_STATIC_V1+LIVE_V1", entry.featureVersion());
        assertEquals("BCARD_LIMIT_POLICY_V1", entry.policyVersion());
        assertEquals("h1", entry.ruleHash());
        assertEquals("b_card_log:777", entry.snapshotRef());
        assertEquals(0.8, entry.multiplier(), 1e-9);
        assertTrue(entry.decisionAllowed());
    }

    private CreditLimitSnapshot limitFor(Long userId) {
        return new CreditLimitSnapshot(
                userId, BigDecimal.valueOf(10000), BigDecimal.ZERO,
                BigDecimal.valueOf(10000), BigDecimal.ZERO,
                false, null, null, true, null);
    }

    private UserSummary userWithIdCard(Long userId, String idCard) {
        return new UserSummary(userId, null, null, null, idCard, null, null, null);
    }

    private Map<String, Object> captureLiveFeatures() throws Exception {
        ArgumentCaptor<UserBCardLog> captor = ArgumentCaptor.forClass(UserBCardLog.class);
        verify(userBCardLogMapper).insert(captor.capture());
        return objectMapper.readValue(
                captor.getValue().getLiveFeatures(),
                new TypeReference<Map<String, Object>>() {});
    }
}