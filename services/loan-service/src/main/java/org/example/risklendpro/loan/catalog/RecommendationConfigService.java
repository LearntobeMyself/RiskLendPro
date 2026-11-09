package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.common.CatalogBusinessException;
import org.example.risklendpro.loan.catalog.dto.RecommendationConfigRequest;
import org.example.risklendpro.loan.catalog.entity.RecommendationConfig;
import org.example.risklendpro.loan.mapper.RecommendationConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 推荐场景配置读取与校验。 */
@Service
public class RecommendationConfigService {

    private final RecommendationConfigMapper configMapper;

    public RecommendationConfigService(RecommendationConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public SceneConfig get(RecommendationScene scene) {
        RecommendationConfig row = configMapper.selectById(scene.name());
        Map<String, Object> config = defaults();
        if (row != null) {
            config.putAll(CatalogJson.readMap(row.getConfigJson()));
        }
        mergeDefaultWeights(config);
        return new SceneConfig(scene, row == null || row.getEnabled() == null || row.getEnabled() == 1,
                row == null || row.getVersion() == null ? 1 : row.getVersion(), config);
    }

    @Transactional
    public Map<String, Object> update(RecommendationScene scene, RecommendationConfigRequest request, Long adminId) {
        if (request == null || request.getConfig() == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "config 不能为空");
        }
        Map<String, Object> config = new LinkedHashMap<>(request.getConfig());
        mergeDefaultWeights(config);
        validate(config);
        RecommendationConfig row = configMapper.selectById(scene.name());
        int version = row == null || row.getVersion() == null ? 1 : row.getVersion() + 1;
        RecommendationConfig next = row == null ? new RecommendationConfig() : row;
        next.setScene(scene.name());
        next.setConfigJson(CatalogJson.writeMap(config));
        next.setEnabled(request.getEnabled() == null || request.getEnabled() ? 1 : 0);
        next.setVersion(version);
        next.setUpdatedAt(LocalDateTime.now());
        if (row == null) {
            configMapper.insert(next);
        } else {
            configMapper.updateById(next);
        }
        return Map.of(
                "scene", scene.name(),
                "config", config,
                "enabled", next.getEnabled() == 1,
                "version", version,
                "updatedBy", adminId == null ? 0 : adminId);
    }

    private static void validate(Map<String, Object> config) {
        Map<String, Object> weights = asMap(config.get("weights"));
        double sum = 0;
        for (String name : new String[]{"similarity", "popularity", "preference", "business"}) {
            Object raw = weights.get(name);
            double value = raw instanceof Number number ? number.doubleValue() : -1;
            if (value < 0) {
                throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "权重 " + name + " 必须是非负数");
            }
            sum += value;
        }
        if (sum <= 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "推荐权重之和必须大于0");
        }
        double exploration = number(config.get("explorationRate"), 0.10);
        if (exploration < 0 || exploration > 0.50) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "explorationRate 须在0到0.5之间");
        }
        int candidateLimit = (int) number(config.get("candidateLimit"), 30);
        if (candidateLimit < 1 || candidateLimit > 100) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "candidateLimit 须在1到100之间");
        }
    }

    private static Map<String, Object> defaults() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("weights", new LinkedHashMap<>(Map.of(
                "similarity", 0.45, "popularity", 0.25, "preference", 0.20, "business", 0.10)));
        config.put("candidateLimit", 30);
        config.put("explorationRate", 0.10);
        config.put("maxPerInstitution", 1);
        config.put("maxPerCategory", 2);
        config.put("fatigueDays", 7);
        config.put("fatiguePenalty", 0.10);
        return config;
    }

    private static void mergeDefaultWeights(Map<String, Object> config) {
        Map<String, Object> defaults = defaults();
        Map<String, Object> weights = defaults.get("weights") instanceof Map<?, ?> map
                ? asMap(map) : new LinkedHashMap<>();
        Map<String, Object> actual = config.get("weights") instanceof Map<?, ?> map
                ? asMap(map) : new LinkedHashMap<>();
        weights.putAll(actual);
        config.put("weights", weights);
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            config.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    private static double number(Object value, double defaultValue) {
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }

    public record SceneConfig(RecommendationScene scene, boolean enabled, int version,
                              Map<String, Object> values) {
        @SuppressWarnings("unchecked")
        public Map<String, Object> weights() {
            return values.get("weights") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        }

        public double weight(String name, double defaultValue) {
            return weights().get(name) instanceof Number number ? number.doubleValue() : defaultValue;
        }

        public double decimal(String name, double defaultValue) {
            return values.get(name) instanceof Number number ? number.doubleValue() : defaultValue;
        }

        public int integer(String name, int defaultValue) {
            return values.get(name) instanceof Number number ? number.intValue() : defaultValue;
        }
    }
}
