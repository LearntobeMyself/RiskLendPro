package org.example.risklendpro.risk.score;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 A1 回放测试：直接读取生产规则文件
 * {@code risk-assessment/output/b_scoring_rules.json}，验证
 *
 * <ol>
 *   <li>生产规则本身通过消费端结构校验（非法规则不能进入评分链路）；</li>
 *   <li>退化特征 pos_dpd_mean / pos_dpd_def_mean 已从入模特征中移除；</li>
 *   <li>移除退化特征前后，同一批样本的分数漂移在容差内（评分公式未被改变）；</li>
 *   <li>阈值、截距与 PDO 参数相对 v1.0 未发生变化。</li>
 * </ol>
 */
class BehaviorScoreRuleReplayTest {

    /** 回放容差：分数按 0.1 分取整，退化特征系数约 2.9e-08，理论上漂移远小于该值。 */
    private static final double REPLAY_TOLERANCE = 0.1;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void productionRulePassesConsumerValidationAndDropsDegenerateFeatures() throws Exception {
        JsonNode rule = loadProductionRule();

        BehaviorScoreEngineImpl.validateRuleContent(rule);

        assertTrue(rule.get("selected_features").size() == rule.get("coefficients").size(),
                "selected_features 与 coefficients 数量必须一致");
        for (JsonNode feature : rule.get("selected_features")) {
            assertFalse("pos_dpd_mean".equals(feature.asText()), "退化特征 pos_dpd_mean 必须移除");
            assertFalse("pos_dpd_def_mean".equals(feature.asText()), "退化特征 pos_dpd_def_mean 必须移除");
        }
        assertTrue(rule.get("selected_features").toString().contains("inst_amt_paid_1y"),
                "inst_amt_paid_1y 经评估后保留入模");
        assertEquals("v1.1-b-woe", rule.get("version").asText());
    }

    @Test
    void removingDegenerateFeaturesDoesNotMoveScores() throws Exception {
        JsonNode v11 = loadProductionRule();
        JsonNode v10 = v11.deepCopy();
        addBackDegenerateFeatures((ObjectNode) v10);

        for (Map<String, Double> sample : replaySamples(v11)) {
            double scoreV11 = BehaviorScoreEngineImpl.calculateBaseScore(v11, sample);
            double scoreV10 = BehaviorScoreEngineImpl.calculateBaseScore(v10, sample);
            assertEquals(scoreV10, scoreV11, REPLAY_TOLERANCE,
                    "回放漂移超过容差，样本=" + sample);
        }
    }

    @Test
    void thresholdsInterceptAndPdoAreUnchangedFromV10() throws Exception {
        JsonNode rule = loadProductionRule();

        assertEquals(-0.0005089810929602766, rule.get("intercept").asDouble(), 0.0);
        assertEquals(684.6, rule.get("thresholds").get("watch").asDouble(), 1e-9);
        assertEquals(547.4, rule.get("thresholds").get("reduce_limit").asDouble(), 1e-9);
        assertEquals(515.169276, rule.get("scorecard").get("pdo").asDouble(), 1e-9);
        assertEquals(650.0, rule.get("scorecard").get("target_score").asDouble(), 1e-9);
        assertEquals(0.9040787474, rule.get("scorecard").get("target_odds").asDouble(), 1e-12);
        assertEquals(350, rule.get("scorecard").get("min_score").asInt());
        assertEquals(950, rule.get("scorecard").get("max_score").asInt());
        assertEquals(5, rule.get("coefficients").size(), "入模特征数应为 5");
    }

    /** 按原 v1.0 的系数与分箱把两个退化特征加回，用于对照回放。 */
    private static void addBackDegenerateFeatures(ObjectNode rule) throws Exception {
        ObjectNode coefficients = (ObjectNode) rule.get("coefficients");
        coefficients.put("pos_dpd_mean", -2.8725527731060955e-08);
        coefficients.put("pos_dpd_def_mean", -2.8725527705283472e-08);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode features = (ObjectNode) rule.get("features");
        features.set("pos_dpd_mean", mapper.readTree(
                "{\"type\":\"numeric\",\"cuts\":[2622.078],\"woe\":[6.5e-05],\"missing_bin\":{\"woe\":0.0}}"));
        features.set("pos_dpd_def_mean", mapper.readTree(
                "{\"type\":\"numeric\",\"cuts\":[1671.398],\"woe\":[6.5e-05],\"missing_bin\":{\"woe\":0.0}}"));
    }

    /** 回放样本：覆盖每个特征的全部分箱点，外加「全部缺失」与「全部超上限」两个边界样本。 */
    private static List<Map<String, Double>> replaySamples(JsonNode rule) {
        JsonNode features = rule.get("features");
        int maxBins = 0;
        for (JsonNode definition : features) {
            maxBins = Math.max(maxBins, definition.path("cuts").size());
        }

        List<Map<String, Double>> samples = new ArrayList<>();
        for (int index = 0; index < maxBins; index++) {
            Map<String, Double> sample = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = features.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                JsonNode cuts = entry.getValue().path("cuts");
                if (cuts.isEmpty()) {
                    continue;
                }
                sample.put(entry.getKey(), cuts.get(Math.min(index, cuts.size() - 1)).asDouble());
            }
            samples.add(sample);
        }

        Map<String, Double> allMissing = new LinkedHashMap<>();
        Iterator<String> names = features.fieldNames();
        while (names.hasNext()) {
            allMissing.put(names.next(), null);
        }
        samples.add(allMissing);

        Map<String, Double> allExtreme = new LinkedHashMap<>();
        Iterator<String> names2 = features.fieldNames();
        while (names2.hasNext()) {
            allExtreme.put(names2.next(), 1.0e9);
        }
        samples.add(allExtreme);
        return samples;
    }

    private JsonNode loadProductionRule() throws IOException {
        String content = readRepoFile("risk-assessment/output/b_scoring_rules.json");
        try {
            return objectMapper.readTree(content);
        } catch (Exception e) {
            throw new IOException("解析生产规则文件失败", e);
        }
    }

    private static String readRepoFile(String relative) throws IOException {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
            dir = dir.getParent();
        }
        throw new IOException("找不到仓库文件: " + relative + "，当前目录=" + Paths.get("").toAbsolutePath());
    }
}