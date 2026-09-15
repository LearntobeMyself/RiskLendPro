package org.example.risklendpro.risk.score;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.risk.credit.UserBehaviorFeatures;
import org.example.risklendpro.risk.credit.mapper.UserBehaviorFeaturesMapper;
import org.example.risklendpro.risk.entity.UserBCardLog;
import org.example.risklendpro.risk.mapper.UserBCardLogMapper;
import org.example.risklendpro.user.entity.User;
import org.example.risklendpro.user.mapper.UserMapper;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
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
    private UserCreditLimitMapper userCreditLimitMapper;

    @Mock
    private UserBCardLogMapper userBCardLogMapper;

    @Mock
    private UserMapper userMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recalculate_resolvesIdCardAndMarksScoreReliable() throws Exception {
        UserCreditLimit limit = limitFor(68L);
        User user = userWithIdCard(68L, "ID-68");
        UserBehaviorFeatures snapshot = new UserBehaviorFeatures();
        snapshot.setIdCard("ID-68");
        snapshot.setFeatureJson("{\"feature\":1.0}");

        BehaviorLiveFeatureService.LiveFeatures live = new BehaviorLiveFeatureService.LiveFeatures();
        live.setHistoryAvailable(true);
        live.setOnTimeRate(1.0);

        when(userCreditLimitMapper.selectOne(any())).thenReturn(limit);
        when(userMapper.selectById(68L)).thenReturn(user);
        when(userBehaviorFeaturesMapper.selectByIdCard("ID-68")).thenReturn(snapshot);
        when(behaviorScoreEngine.calculateBaseScore(anyMap())).thenReturn(620.0);
        when(behaviorLiveFeatureService.aggregate(68L)).thenReturn(live);
        when(userBCardLogMapper.insert(any())).thenReturn(1);

        behaviorScoreService.recalculate(68L);

        verify(userMapper).selectById(68L);
        verify(userBehaviorFeaturesMapper).selectByIdCard("ID-68");
        assertEquals(640.0, limit.getBScore().doubleValue(), 0.001);

        Map<String, Object> liveFeatures = captureLiveFeatures();
        assertTrue((Boolean) liveFeatures.get("baseScoreResolved"));
        assertTrue((Boolean) liveFeatures.get("scoreReliable"));
        assertEquals("RELIABLE", liveFeatures.get("dataStatus"));
    }

    @Test
    void recalculate_marksMissingFeatureSnapshotUnreliable() throws Exception {
        UserCreditLimit limit = limitFor(101L);
        User user = userWithIdCard(101L, "ID-101");

        BehaviorLiveFeatureService.LiveFeatures live = new BehaviorLiveFeatureService.LiveFeatures();
        live.setHistoryAvailable(true);
        live.setOnTimeRate(0.0);

        when(userCreditLimitMapper.selectOne(any())).thenReturn(limit);
        when(userMapper.selectById(101L)).thenReturn(user);
        when(userBehaviorFeaturesMapper.selectByIdCard("ID-101")).thenReturn(null);
        when(behaviorLiveFeatureService.aggregate(101L)).thenReturn(live);
        when(userBCardLogMapper.insert(any())).thenReturn(1);

        behaviorScoreService.recalculate(101L);

        Map<String, Object> liveFeatures = captureLiveFeatures();
        assertFalse((Boolean) liveFeatures.get("baseScoreResolved"));
        assertFalse((Boolean) liveFeatures.get("scoreReliable"));
        assertEquals("FEATURE_NOT_FOUND", liveFeatures.get("dataStatus"));
    }

    @Test
    void resolveLimitMultiplierForUser_returnsNullWhenScoreUnreliable() throws Exception {
        UserBCardLog latestLog = new UserBCardLog();
        latestLog.setUserId(101L);
        latestLog.setLiveFeatures("{\"scoreReliable\":false,\"dataStatus\":\"FEATURE_NOT_FOUND\"}");

        when(userBCardLogMapper.selectOne(any())).thenReturn(latestLog);

        assertNull(behaviorScoreService.resolveLimitMultiplierForUser(101L, 600.0));
    }

    private UserCreditLimit limitFor(Long userId) {
        UserCreditLimit limit = new UserCreditLimit();
        limit.setUserId(userId);
        limit.setBCardEnabled(true);
        limit.setTotalLimit(BigDecimal.valueOf(10000));
        limit.setUsedLimit(BigDecimal.ZERO);
        limit.setRemainingLimit(BigDecimal.valueOf(10000));
        return limit;
    }

    private User userWithIdCard(Long userId, String idCard) {
        User user = new User();
        user.setId(userId);
        user.setIdCard(idCard);
        return user;
    }

    private Map<String, Object> captureLiveFeatures() throws Exception {
        ArgumentCaptor<UserBCardLog> captor = ArgumentCaptor.forClass(UserBCardLog.class);
        verify(userBCardLogMapper).insert(captor.capture());
        return objectMapper.readValue(
                captor.getValue().getLiveFeatures(),
                new TypeReference<Map<String, Object>>() {});
    }
}
