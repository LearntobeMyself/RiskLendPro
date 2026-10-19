package org.example.risklendpro.risk.score;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.risklendpro.risk.credit.BehaviorScoringRules;
import org.example.risklendpro.risk.credit.mapper.BehaviorScoringRulesMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 A1：规则加载的 TTL、版本切换、内容哈希与结构校验。
 *
 * <p>A0 已保证非法规则不会返回 700 中性分；A1 在此基础上把校验面扩展到
 * 模型类型、卡类型、特征列表、系数长度、WOE 长度、阈值与 PDO 参数。</p>
 */
@ExtendWith(MockitoExtension.class)
class BehaviorScoreEngineImplTest {

    @InjectMocks
    private BehaviorScoreEngineImpl engine;

    @Mock
    private BehaviorScoringRulesMapper behaviorScoringRulesMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------------- A0 保留：规则不可用不得静默返回 700 ----------------

    @Test
    void calculateBaseScore_throwsRuleNotFoundWhenNoActiveRule() {
        when(behaviorScoringRulesMapper.selectActiveRule()).thenReturn(null);

        BehaviorScoreEngineImpl.RuleUnavailableException ex = assertThrows(
                BehaviorScoreEngineImpl.RuleUnavailableException.class,
                () -> engine.calculateBaseScore(Map.of("f1", 1.0)));

        assertEquals(BehaviorScoreEngineImpl.STATUS_RULE_NOT_FOUND, ex.getDataStatus());
    }

    @Test
    void calculateBaseScore_throwsParseErrorWhenRuleIsNotJson() {
        when(behaviorScoringRulesMapper.selectActiveRule()).thenReturn(rule(1L, "v1.0-b-woe", "not json at all"));

        BehaviorScoreEngineImpl.RuleUnavailableException ex = assertThrows(
                BehaviorScoreEngineImpl.RuleUnavailableException.class,
                () -> engine.calculateBaseScore(Map.of("f1", 1.0)));

        assertEquals(BehaviorScoreEngineImpl.STATUS_RULE_PARSE_ERROR, ex.getDataStatus());
    }

    @Test
    void calculateBaseScore_throwsValidationErrorWhenRuleIncomplete() {
        when(behaviorScoringRulesMapper.selectActiveRule()).thenReturn(rule(1L, "v1.0-b-woe", "{\"version\":\"v1\"}"));

        BehaviorScoreEngineImpl.RuleUnavailableException ex = assertThrows(
                BehaviorScoreEngineImpl.RuleUnavailableException.class,
                () -> engine.calculateBaseScore(Map.of("f1", 1.0)));

        assertEquals(BehaviorScoreEngineImpl.STATUS_RULE_VALIDATION_ERROR, ex.getDataStatus());
    }

    // ---------------- A1 扩展校验面 ----------------

    @Test
    void rejectsWoeLengthMismatch() throws Exception {
        JsonNode rule = parse(validRuleContent(684.6, 547.4));
        ((ObjectNode) rule.get("features").get("f1")).set("woe", parse("[0.1]"));

        assertValidationRejected(rule, "woe 长度与 cuts 不一致");
    }

    @Test
    void rejectsWrongModelType() throws Exception {
        JsonNode rule = parse(validRuleContent(684.6, 547.4));
        ((ObjectNode) rule).put("model_type", "a_card_woe");

        assertValidationRejected(rule, "model_type");
    }

    @Test
    void rejectsWrongCardType() throws Exception {
        JsonNode rule = parse(validRuleContent(684.6, 547.4));
        ((ObjectNode) rule).put("card_type", "A");

        assertValidationRejected(rule, "card_type");
    }

    @Test
    void rejectsFeatureListCoefficientMismatch() throws Exception {
        JsonNode rule = parse(validRuleContent(684.6, 547.4));
        ((ObjectNode) rule).putArray("selected_features").add("f1").add("f2");

        assertValidationRejected(rule, "selected_features 与 coefficients 不一致");
    }

    @Test
    void rejectsMissingFeatureDefinition() throws Exception {
        JsonNode rule = parse(validRuleContent(684.6, 547.4));
        ((ObjectNode) rule.get("features")).remove("f1");

        assertValidationRejected(rule, "缺少特征定义");
    }

    @Test
    void rejectsInvertedThresholds() throws Exception {
        JsonNode rule = parse(validRuleContent(547.4, 684.6));

        assertValidationRejected(rule, "reduce_limit < watch");
    }

    @Test
    void rejectsIllegalPdo() throws Exception {
        JsonNode rule = parse(validRuleContent(684.6, 547.4));
        ((ObjectNode) rule.get("scorecard")).put("pdo", 0);

        assertValidationRejected(rule, "scorecard.pdo");
    }

    @Test
    void rejectsMissingScorecard() throws Exception {
        JsonNode rule = parse(validRuleContent(684.6, 547.4));
        ((ObjectNode) rule).remove("scorecard");

        assertValidationRejected(rule, "scorecard");
    }

    // ---------------- 合法规则：评分与阈值来源 ----------------

    @Test
    void calculatesScoreAndReportsDbColumnThresholdSource() throws Exception {
        BehaviorScoringRules rules = rule(1L, "v1.0-b-woe", validRuleContent(684.6, 547.4));
        rules.setModelType("b_card_woe");
        rules.setCardType("B");
        rules.setThresholdWatch(new BigDecimal("684.6"));
        rules.setThresholdReduceLimit(new BigDecimal("547.4"));
        when(behaviorScoringRulesMapper.selectActiveRule()).thenReturn(rules);
        when(behaviorScoringRulesMapper.countActiveRules()).thenReturn(1);

        Map<String, Double> features = new HashMap<>();
        features.put("f1", 1.5);
        double score = engine.calculateBaseScore(features);

        assertTrue(score >= 350.0 && score <= 950.0, "score out of range: " + score);
        assertEquals(684.6, engine.getThresholdWatch(), 1e-9);
        assertEquals(547.4, engine.getThresholdReduceLimit(), 1e-9);

        BehaviorScoreEngineImpl.RuleRuntimeInfo info = engine.describeActiveRule();
        assertEquals("v1.0-b-woe", info.version());
        assertEquals(BehaviorScoreEngineImpl.THRESHOLD_SOURCE_DB_COLUMN, info.thresholdSource());
        assertEquals(1, info.activeVersionCount());
        assertFalse(info.contentHash() == null || info.contentHash().isBlank(), "必须记录内容哈希");
    }

    @Test
    void fallsBackToRuleContentThresholdsWhenColumnsAreNull() {
        BehaviorScoringRules rules = rule(1L, "v1.0-b-woe", validRuleContent(700.0, 600.0));
        when(behaviorScoringRulesMapper.selectActiveRule()).thenReturn(rules);

        assertEquals(700.0, engine.getThresholdWatch(), 1e-9);
        assertEquals(600.0, engine.getThresholdReduceLimit(), 1e-9);
        assertEquals(BehaviorScoreEngineImpl.THRESHOLD_SOURCE_RULE_CONTENT,
                engine.describeActiveRule().thresholdSource());
    }

    // ---------------- A1 核心：TTL 与免重启版本切换 ----------------

    @Test
    void cacheServesWithinTtlAndReloadsAfterExpiry() {
        ReflectionTestUtils.setField(engine, "ruleCacheTtlSeconds", 60L);
        when(behaviorScoringRulesMapper.selectActiveRule())
                .thenReturn(rule(1L, "v1.0-b-woe", validRuleContent(684.6, 547.4)));

        engine.getThresholdWatch();
        engine.getThresholdWatch();
        verify(behaviorScoringRulesMapper, times(1)).selectActiveRule();

        // TTL 设为 0 等价于「已过期」，下一次调用必须重新读取
        ReflectionTestUtils.setField(engine, "ruleCacheTtlSeconds", 0L);
        engine.getThresholdWatch();
        verify(behaviorScoringRulesMapper, times(2)).selectActiveRule();
    }

    @Test
    void ttlExpirySwitchesToNewlyActivatedVersionWithoutRestart() {
        ReflectionTestUtils.setField(engine, "ruleCacheTtlSeconds", 0L);
        BehaviorScoringRules v1 = rule(1L, "v1.0-b-woe", validRuleContent(684.6, 547.4));
        v1.setThresholdWatch(new BigDecimal("684.6"));
        v1.setThresholdReduceLimit(new BigDecimal("547.4"));
        BehaviorScoringRules v11 = rule(2L, "v1.1-b-woe", validRuleContent(700.0, 600.0));
        v11.setThresholdWatch(new BigDecimal("700.0"));
        v11.setThresholdReduceLimit(new BigDecimal("600.0"));

        when(behaviorScoringRulesMapper.selectActiveRule()).thenReturn(v1).thenReturn(v11);

        assertEquals(684.6, engine.getThresholdWatch(), 1e-9);
        assertEquals(700.0, engine.getThresholdWatch(), 1e-9);
    }

    @Test
    void equalContentHashKeepsParsedRuleAndOnlyRenewsTtl() {
        ReflectionTestUtils.setField(engine, "ruleCacheTtlSeconds", 0L);
        when(behaviorScoringRulesMapper.selectActiveRule())
                .thenReturn(rule(7L, "v1.0-b-woe", validRuleContent(684.6, 547.4)));

        engine.getThresholdWatch();
        engine.getThresholdWatch();

        // 规则行与内容哈希都没变，说明没有产生新的解析结果
        verify(behaviorScoringRulesMapper, times(2)).selectActiveRule();
        assertEquals(684.6, engine.getThresholdWatch(), 1e-9);
    }

    @Test
    void refreshRulesForcesReloadWithinTtl() {
        ReflectionTestUtils.setField(engine, "ruleCacheTtlSeconds", 3600L);
        BehaviorScoringRules v1 = rule(1L, "v1.0-b-woe", validRuleContent(684.6, 547.4));
        when(behaviorScoringRulesMapper.selectActiveRule()).thenReturn(v1);
        when(behaviorScoringRulesMapper.countActiveRules()).thenReturn(1);

        engine.getThresholdWatch();

        BehaviorScoringRules v11 = rule(2L, "v1.1-b-woe", validRuleContent(700.0, 600.0));
        when(behaviorScoringRulesMapper.selectActiveRule()).thenReturn(v11);

        BehaviorScoreEngineImpl.RuleRuntimeInfo info = engine.refreshRules();

        assertEquals("v1.1-b-woe", info.version());
        assertEquals(1, info.activeVersionCount());
        verify(behaviorScoringRulesMapper, times(2)).selectActiveRule();
    }

    // ---------------- helper ----------------

    private void assertValidationRejected(JsonNode rule, String expectedMessageFragment) {
        String content;
        try {
            content = objectMapper.writeValueAsString(rule);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(behaviorScoringRulesMapper.selectActiveRule()).thenReturn(rule(1L, "v1.0-b-woe", content));

        BehaviorScoreEngineImpl.RuleUnavailableException ex = assertThrows(
                BehaviorScoreEngineImpl.RuleUnavailableException.class,
                () -> engine.calculateBaseScore(Map.of("f1", 1.0)));

        assertEquals(BehaviorScoreEngineImpl.STATUS_RULE_VALIDATION_ERROR, ex.getDataStatus());
        assertTrue(ex.getMessage().contains(expectedMessageFragment),
                "期望校验信息包含「" + expectedMessageFragment + "」，实际：" + ex.getMessage());
    }

    private JsonNode parse(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    private static String validRuleContent(double watch, double reduce) {
        return "{"
                + "\"version\":\"v1.0-b-woe\","
                + "\"model_type\":\"b_card_woe\","
                + "\"card_type\":\"B\","
                + "\"selected_features\":[\"f1\"],"
                + "\"features\":{\"f1\":{\"type\":\"numeric\",\"cuts\":[1.0,2.0],"
                + "\"woe\":[0.1,-0.1,0.2],\"missing_bin\":{\"woe\":0.0}}},"
                + "\"coefficients\":{\"f1\":1.0},"
                + "\"intercept\":0.0,"
                + "\"thresholds\":{\"watch\":" + watch + ",\"reduce_limit\":" + reduce + "},"
                + "\"scorecard\":{\"pdo\":80,\"target_score\":650,\"target_odds\":1,"
                + "\"min_score\":350,\"max_score\":950}"
                + "}";
    }

    private static BehaviorScoringRules rule(Long id, String version, String content) {
        BehaviorScoringRules rules = new BehaviorScoringRules();
        rules.setId(id);
        rules.setVersion(version);
        rules.setRuleContent(content);
        return rules;
    }
}